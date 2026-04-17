[![Maven build pipeline](https://github.com/NKO-Dev-Studio/speech-to-text-uni-erfurt/actions/workflows/maven-build.yml/badge.svg)](https://github.com/NKO-Dev-Studio/speech-to-text-uni-erfurt/actions/workflows/maven-build.yml)
[![Docker image build](https://github.com/NKO-Dev-Studio/speech-to-text-uni-erfurt/actions/workflows/docker-image-build.yml/badge.svg)](https://github.com/NKO-Dev-Studio/speech-to-text-uni-erfurt/actions/workflows/docker-image-build.yml)
# speech-to-text-uni-erfurt

This project includes modules that enable the execution of asynchronous speech-to-text tasks.
The goal of the implementation was to enable easy integration into existing services and to allow the expansion of the
used engines as needed

## Modules

The project consists of the following modules:

* **[speech-to-text-service-uni-erfurt](speech-to-text-service-uni-erfurt)**: Backend service handling the management
  and execution of speech-to-text-tasks.
* **[speech-to-text-service-client-uni-erfurt](speech-to-text-service-client-uni-erfurt)**:  Client library which can be
  integrated to existing java projects to trigger tasks, receiving their current state and consuming their results.
* **[speech-to-text-service-protocol-uni-erfurt](speech-to-text-service-client-uni-erfurt)**:  Definition of the gRPC
  interface used for the communication between client and service

## Interfaces

gRPC is currently used as network interface between client and service. The components are designed to allow definition
of alternative interfaces in the future.

A detailed documentation of the gRPC interface can be found inside
the [readme.md](speech-to-text-service-protocol-uni-erfurt/readme.md) of the speech-to-text-service-protocol-uni-erfurt.

## Docker image

This repository also ships a Docker image for `speech-to-text-service-uni-erfurt`. The image bundles the Java service,
the local Whisper runtime (`openai-whisper`), `ffmpeg`, and an entrypoint script which translates environment variables
into the YAML service configuration before the gRPC server is started.

The image is intended for running a self-contained speech-to-text service instance with persistent storage for the
SQLite database, uploaded audio files, and generated transcription results. TLS is always enabled for the gRPC server,
so certificate files must be provided to the container. The service storage paths inside the container are fixed and
cannot be reconfigured.

Build the image locally:

```bash
docker build -t speech-to-text-service-uni-erfurt .
```

Minimal example:

```bash
docker run --rm \
  -p 8080:8080 \
  --mount type=bind,src="$(pwd)/storage",target=/var/opt/speech-to-text-service/storage \
  --mount type=bind,src="$(pwd)/shared-storage",target=/var/opt/speech-to-text-service/shared-storage \
  --mount type=bind,src="$(pwd)/certificates/server-certificate.pem",target=/certificates/server-certificate.pem,readonly \
  --mount type=bind,src="$(pwd)/certificates/server-private-key.pem",target=/certificates/server-private-key.pem,readonly \
  -e SPEECH_TO_TEXT_GRPC_SERVER_CERTIFICATE_PATH=/certificates/server-certificate.pem \
  -e SPEECH_TO_TEXT_GRPC_SERVER_PRIVATE_KEY_PATH=/certificates/server-private-key.pem \
  speech-to-text-service-uni-erfurt
```

## Docker configuration

The container does not require a manually authored YAML file by default. On startup, the entrypoint script generates
the configuration file at `/etc/opt/speech-to-text-service/speech-to-text-service.yaml`.

### Fixed container paths

The following paths are fixed in the Docker image and cannot be changed with environment variables:

* Service installation directory: `/opt/speech-to-text-service-uni-erfurt`
* Generated YAML configuration file: `/etc/opt/speech-to-text-service/speech-to-text-service.yaml`
* Logback configuration file: `/etc/opt/speech-to-text-service/logback.xml`
* Service storage directory: `/var/opt/speech-to-text-service/storage`
* Audio working directory: `/var/opt/speech-to-text-service/storage/audio`
* Result working directory: `/var/opt/speech-to-text-service/storage/results`
* Shared storage directory: `/var/opt/speech-to-text-service/shared-storage`
* Shared audio directory: `/var/opt/speech-to-text-service/shared-storage/audio`
* Shared result directory: `/var/opt/speech-to-text-service/shared-storage/results`
* SQLite database file: `/var/opt/speech-to-text-service/storage/speech-to-text-db.sqlite`

### Runtime configuration

The following environment variables are supported at runtime. Storage paths and database file locations are excluded
because they are fixed by the image.

### Database pool

* `SPEECH_TO_TEXT_DB_MAXIMUM_POOL_SIZE`: Maximum JDBC connection pool size. Default: `4`
* `SPEECH_TO_TEXT_DB_MINIMUM_IDLE_SIZE`: Minimum number of idle DB connections kept in the pool. Default: `1`
* `SPEECH_TO_TEXT_DB_CONNECTION_TIMEOUT_MS`: Connection acquisition timeout in milliseconds. Default: `30000`

### Whisper engine

* `SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_ENABLED`: Must currently stay `true`. The image does not ship an alternative
  engine implementation.
* `SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_DEVICE_TYPE`: Execution device for Whisper. Supported values: `cpu`, `gpu`,
  `mps`. Default: `cpu`
* `SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_DEVICE_TYPE_CPU_NUMBER_OF_THREADS`: Number of CPU threads for Whisper when
  `DEVICE_TYPE=cpu`. `0` means no explicit limit. Default: `0`
* `SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_DEVICE_TYPE_GPU_NUMBER`: GPU index used when `DEVICE_TYPE=gpu`. Default: `0`

### Scheduling, gRPC, and authentication

* `SPEECH_TO_TEXT_TASK_SCHEDULER_NUMBER_OF_PARALLEL_TASKS`: Number of transcription jobs that may run concurrently.
  Default: `1`
* `SPEECH_TO_TEXT_GRPC_SERVER_CERTIFICATE_PATH`: Path to the server TLS certificate file. Required.
* `SPEECH_TO_TEXT_GRPC_SERVER_PRIVATE_KEY_PATH`: Path to the server TLS private key file. Required.
* `SPEECH_TO_TEXT_GRPC_SERVER_PRIVATE_KEY_PASSWORD_FILE`: Optional path to a file containing the password for an
  encrypted private key.
* `SPEECH_TO_TEXT_AUTHENTICATION_TYPE`: Client authentication mode. Supported values: `none`, `certificate`.
  Default: `none`
* `SPEECH_TO_TEXT_AUTHENTICATION_CERTIFICATE_ROOT_CERTIFICATE_PATH`: Root CA certificate used to validate client
  certificates. Required when `SPEECH_TO_TEXT_AUTHENTICATION_TYPE=certificate`.

## Files and directories to provide

The container creates its working directories automatically, but some files must be bind-mounted or otherwise made
available inside the container:

* Required:
  * A server certificate file referenced by `SPEECH_TO_TEXT_GRPC_SERVER_CERTIFICATE_PATH`
  * A server private key file referenced by `SPEECH_TO_TEXT_GRPC_SERVER_PRIVATE_KEY_PATH`
* Conditionally required:
  * A root CA certificate file referenced by
    `SPEECH_TO_TEXT_AUTHENTICATION_CERTIFICATE_ROOT_CERTIFICATE_PATH` when client certificate authentication is enabled
  * A password file referenced by `SPEECH_TO_TEXT_GRPC_SERVER_PRIVATE_KEY_PASSWORD_FILE` when the private key is
    encrypted
* Mountable service directories:
  * `/var/opt/speech-to-text-service/storage` can be mounted to persist the SQLite database and the internal
    `audio` and `results` working directories across container restarts
  * `/var/opt/speech-to-text-service/shared-storage` can be mounted when the `audio` and `results` shared
    subdirectories must be exchanged with other services through a shared filesystem

No static YAML configuration file needs to be provided. The container always generates it at
`/etc/opt/speech-to-text-service/speech-to-text-service.yaml`.
