# speech-to-text-service-protocol

## Overview
`speech-to-text-service-protocol` contains the shared gRPC and Protocol Buffers contract for the
speech-to-text system. The module defines the RPC surface, task lifecycle messages, engine and
output format enums, and the payloads used to transfer audio and transcription results between the
client and the service.

During the Maven build, the `.proto` definition is compiled into Java protobuf classes and gRPC
stubs that are consumed by both `speech-to-text-service` and `speech-to-text-service-client`.

## Main RPCs
- `StartSpeechToTextTask` creates a new transcription task and returns its `taskId`.
- `ProvideAudioFileStream` uploads audio chunks directly to the service.
- `ProvideSharedAudioFile` references an audio file that already exists in shared storage.
- `GetTaskStatus` returns the current task state.
- `WriteTaskResultsIntoSharedStorage` exports the completed result file into shared storage.
- `GetTaskResultsAsStream` streams the completed result file back to the client.
- `GetEngines` lists the currently available speech-to-text engines and models.

## Sequence Diagram
```plantuml
@startuml
title Speech-to-text task flow

actor Client
participant "SpeechToText gRPC Service" as Service
participant "Task Storage / Engine Runtime" as Backend
participant "Shared Storage" as SharedStorage

opt Discover available engines
  Client -> Service: GetEngines(Empty)
  Service -> Backend: load active engines
  Backend --> Service: engines + models + output formats
  Service --> Client: SpeechToTextEngines
end

Client -> Service: StartSpeechToTextTask(TaskRequest)
Service -> Backend: create task
Backend --> Service: taskId
Service --> Client: TaskDescription(taskId)

alt Audio uploaded as gRPC stream
  Client -> Service: ProvideAudioFileStream(AudioFileChunk...)
  Service -> Backend: persist uploaded chunks
  Backend --> Service: audio stored
  Service --> Client: Empty
else Audio provided through shared storage
  Client -> SharedStorage: copy audio file
  Client -> Service: ProvideSharedAudioFile(taskId, fileName)
  Service -> Backend: attach shared audio file to task
  Backend --> Service: audio accepted
  Service --> Client: Empty
end

loop Until task reaches DONE or FAILED
  Client -> Service: GetTaskStatus(TaskDescription)
  Service -> Backend: read task state
  Backend --> Service: WAITING_FOR_AUDIO / PENDING / RUNNING / DONE / FAILED
  Service --> Client: TaskState
end

alt Results written to shared storage
  Client -> Service: WriteTaskResultsIntoSharedStorage(TaskDescription)
  Service -> Backend: move result file to shared storage
  Backend -> SharedStorage: write result file
  SharedStorage --> Backend: stored file name
  Backend --> Service: SharedStorageResult
  Service --> Client: SharedStorageResult
else Results streamed back to the client
  Client -> Service: GetTaskResultsAsStream(TaskDescription)
  Service -> Backend: open result file
  Backend --> Service: result file content
  Service --> Client: SpeechToTextResultFileChunk...
end

@enduml
```
