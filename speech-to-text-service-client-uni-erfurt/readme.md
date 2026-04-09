# speech-to-text-service-client

## Overview
`speech-to-text-service-client` is the Java client library for `speech-to-text-service`. It opens a
TLS-secured gRPC connection, uploads audio files, starts transcription tasks, polls task state
changes, and saves completed transcription results to the local filesystem.

The client currently supports two transfer modes:
- `STREAMING`: audio files and result files are transferred through gRPC.
- `SHARED_STORAGE`: files are exchanged through a directory that is accessible by both client and
  service.

## Short Usage Explanation
The typical workflow is:
1. Build a `SpeechToTextServiceClientConfiguration`.
2. Create a `SpeechToTextServiceClient`.
3. Optionally call `getAvailableEngines()` to inspect engines, models, and supported output
   formats.
4. Start a task with `startSpeechToTextTask(...)`.
5. Track progress by either polling `getStateOfTask(...)` or registering `observeTask(...)`.
6. When the task reaches `DONE`, call `saveResultsOfTask(...)`.

Important behavior:
- `saveResultsOfTask(...)` consumes the result file only once. After the result is retrieved,
  it is removed from the service side.
- `observeTask(...)` reports only changed task states and stops automatically after `DONE` or
  `FAILED`.
- `SpeechToTextServiceClient` implements `AutoCloseable` and should be used in a
  try-with-resources block.

## Configuration Overview
Create the configuration with
`SpeechToTextServiceClientConfiguration.builder(host, port, trustedServerCertificatePath, audioTransfer, resultTransfer)`.

Mandatory settings:
- `host`: service host name or IP address.
- `port`: gRPC port of the service.
- `trustedServerCertificatePath`: PEM file used to validate the server certificate.
- `audioFileTransferConfiguration`: created with `SpeechToTextTransferConfigurationFactory`.
- `resultTransferConfiguration`: created with `SpeechToTextTransferConfigurationFactory`.

Optional settings:
- `authenticationConfiguration(...)`: enables mutual TLS with a client certificate and private key.
- `authorityOverride(...)`: overrides the TLS authority if the connection host and certificate host
  differ.
- `audioFileChunkSizeBytes(...)`: chunk size for streamed uploads. Default: `65536`.
- `taskObservationInitialDelayMs(...)`: initial polling delay. Default: `250`.
- `taskObservationMaximumDelayMs(...)`: maximum polling delay. Default: `5000`.
- `taskObservationDelayMultiplier(...)`: backoff factor for unchanged task states. Default: `2.0`.
- `observationThreadPoolSize(...)`: number of observation threads. Default: `1`.

Transfer configuration options:
- `SpeechToTextTransferConfigurationFactory.streaming()`: transfer through gRPC.
- `SpeechToTextTransferConfigurationFactory.sharedStorage(path)`: transfer through an existing
  shared directory.

Authentication configuration:
- `SpeechToTextServiceClientAuthenticationConfiguration.builder(certificatePath, privateKeyPath)`
  configures the client certificate for mutual TLS.
- `privateKeyPassword(...)` can be set when the private key is encrypted.

Supported task states:
- `WAITING_FOR_AUDIO_FILE`
- `PENDING`
- `RUNNING`
- `DONE`
- `FAILED`

Supported result formats:
- `TXT`
- `VTT`
- `SRT`
- `TSV`
- `JSON`

## Error Handling
The client fails fast for invalid local configuration and wraps remote/runtime failures into
`SpeechToTextServiceClientException`.

Common error categories:
- `IllegalArgumentException`: invalid configuration, missing certificate files, invalid transfer
  setup, invalid audio file path, or missing task parameters.
- `SpeechToTextServiceClientException`: TLS setup failures, gRPC request failures, file transfer
  errors, result persistence errors, or incomplete streamed results.
- `IllegalStateException`: using the client after `close()`.

Recommended handling:
- Treat `IllegalArgumentException` as a caller/configuration error and fix the input.
- Catch `SpeechToTextServiceClientException` around network and file operations.
- Handle `FAILED` as a terminal task state and do not call `saveResultsOfTask(...)` unless your
  service guarantees results for failed tasks.

## Usage Examples
### Basic Streaming Setup
```java
import java.nio.file.Path;
import studio.nkodev.stt.client.SpeechToTextServiceClient;
import studio.nkodev.stt.client.config.SpeechToTextServiceClientConfiguration;
import studio.nkodev.stt.client.config.SpeechToTextTransferConfigurationFactory;

SpeechToTextServiceClientConfiguration configuration =
    SpeechToTextServiceClientConfiguration.builder(
            "localhost",
            8080,
            Path.of("/path/to/root-ca.cert.pem"),
            SpeechToTextTransferConfigurationFactory.streaming(),
            SpeechToTextTransferConfigurationFactory.streaming())
        .build();

try (SpeechToTextServiceClient client = new SpeechToTextServiceClient(configuration)) {
  System.out.println(client.getAvailableEngines());
}
```

### Start a Task and Observe Progress
```java
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import studio.nkodev.stt.client.SpeechToTextServiceClient;
import studio.nkodev.stt.client.api.SpeechToTextEngineOutputFormat;
import studio.nkodev.stt.client.api.SpeechToTextTaskState;

CountDownLatch done = new CountDownLatch(1);

try (SpeechToTextServiceClient client = new SpeechToTextServiceClient(configuration)) {
  long taskId =
      client.startSpeechToTextTask(
          Path.of("/path/to/audio.opus"),
          "WHISPER_LOCAL",
          "turbo",
          SpeechToTextEngineOutputFormat.JSON);

  client.observeTask(
      taskId,
      (observedTaskId, state) -> {
        System.out.println("Task " + observedTaskId + " -> " + state);

        if (state == SpeechToTextTaskState.DONE) {
          Path resultPath = client.saveResultsOfTask(observedTaskId, Path.of("./results"));
          System.out.println("Saved result to " + resultPath);
          done.countDown();
        }

        if (state == SpeechToTextTaskState.FAILED) {
          done.countDown();
        }
      });

  done.await();
}
```

### Shared Storage and Mutual TLS
```java
import java.nio.file.Path;
import studio.nkodev.stt.client.config.SpeechToTextServiceClientAuthenticationConfiguration;
import studio.nkodev.stt.client.config.SpeechToTextServiceClientConfiguration;
import studio.nkodev.stt.client.config.SpeechToTextTransferConfigurationFactory;

SpeechToTextServiceClientConfiguration configuration =
    SpeechToTextServiceClientConfiguration.builder(
            "stt.internal",
            8443,
            Path.of("/certs/root-ca.cert.pem"),
            SpeechToTextTransferConfigurationFactory.sharedStorage(Path.of("/shared/stt")),
            SpeechToTextTransferConfigurationFactory.sharedStorage(Path.of("/shared/stt")))
        .authenticationConfiguration(
            SpeechToTextServiceClientAuthenticationConfiguration.builder(
                    Path.of("/certs/client.cert.pem"),
                    Path.of("/certs/client.key.pem"))
                .build())
        .authorityOverride("speech-to-text-service.internal")
        .build();
```
