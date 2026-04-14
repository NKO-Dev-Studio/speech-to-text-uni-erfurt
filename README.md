[![Maven build pipeline](https://github.com/NKO-Dev-Studio/speech-to-text-uni-erfurt/actions/workflows/maven-build.yml/badge.svg)](https://github.com/NKO-Dev-Studio/speech-to-text-uni-erfurt/actions/workflows/maven-build.yml)
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