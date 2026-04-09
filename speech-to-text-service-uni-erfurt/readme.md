# speech-to-text-service

## Overview
`speech-to-text-service` is a backend service that accepts speech-to-text tasks, stores task and
file state, executes transcription jobs via configured engines, and provides the requested result
file through
adapter interfaces such as gRPC.

## Requirements
- Java 25
- Maven
- A valid YAML configuration file
- A configured speech-to-text engine executable such as `whisper-cli`
- TLS certificate and private key for the gRPC server
- If you want to use a local whisper engine whisper must be installed on your target system

## Build

```bash
mvn clean package
```

## Run
Run the service with the path to a YAML configuration file as the first program argument.

Example:

```bash
java -jar speech-to-text-service.jar /path/to/config.yaml
```

## Configuration
Use [config.example.yaml](/Users/nicokotlenga/Projects/nko-dev-studio/speech-to-text-service/config.example.yaml) as the template.

The YAML file contains all startup parameters currently used to instantiate the existing
`*Configuration` classes:
- `audioFileStorage`
- `resultStorage`
- `database`
- `engines`
- `taskScheduler`
- `authentication`
- `grpcServer`

Important configuration notes:
- `grpcServer.serverCertificatePath` and `grpcServer.serverPrivateKeyPath` are always required
  because the gRPC server always uses TLS.
- `authentication.type: "none"` keeps TLS enabled but does not require client certificate
  authentication.
- `authentication.type: "certificate"` enables certificate-based client authentication using the
  configured `authentication.rootCertificatePath`.

## Architecture
The service is structured in a few coarse-grained layers:
- `config` loads and maps the YAML startup configuration.
- `adapter` exposes the service to external clients. The current adapter is gRPC.
- `auth` contains reusable authentication components that can be shared by multiple adapters.
- `service` implements the main speech-to-text use cases.
- `storage` manages audio files, task metadata, and transcription results.
- `engine` encapsulates speech-to-text engine integrations such as local Whisper.
- `task` schedules and executes speech-to-text jobs.

At startup, the starter loads the configuration, initializes storage and engine components, creates
the reusable authentication component, and finally wires the adapter layer to the core service.

## Security
- The gRPC server always uses TLS.
- Client certificate authentication is optional and controlled through the `authentication` section.
- When certificate authentication is enabled, unauthenticated gRPC requests are rejected with
  status `UNAUTHENTICATED`.
