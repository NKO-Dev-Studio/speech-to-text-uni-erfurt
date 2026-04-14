FROM eclipse-temurin:25-jre-noble

ARG SPEECH_TO_TEXT_SERVICE_VERSION="1.0.1-SNAPSHOT"

LABEL org.opencontainers.image.title="speech-to-text-service-uni-erfurt"
LABEL org.opencontainers.image.description="Java-based service to execute speech to text tasks. Service communication is handled over gRPC"
LABEL org.opencontainers.image.version="${SPEECH_TO_TEXT_SERVICE_VERSION}"
LABEL org.opencontainers.image.authors="Nico Kotlenga <nico@nko-dev.studio>"
LABEL org.opencontainers.image.source="https://github.com/NKO-Dev-Studio/speech-to-text-uni-erfurt"
LABEL org.opencontainers.image.licenses="Apache License 2.0"

ARG APP_HOME=/opt/speech-to-text-service-uni-erfurt
ARG APP_DATA_LOCATION=/var/opt/speech-to-text-service
ARG SHARED_AUDIO_FILE_STORAGE_LOCATION=${APP_DATA_LOCATION}/audio-files-shared
ARG AUDIO_FILE_STORAGE_LOCATION=${APP_DATA_LOCATION}/audio-files
ARG RESULT_STORAGE_LOCATION=${APP_DATA_LOCATION}/results
ARG SHARED_RESULT_STORAGE_LOCATION=${APP_DATA_LOCATION}/results-shared
ARG SPEECH_TO_TEXT_CONFIG_DIRECTORY_LOCATION=/etc/opt/speech-to-text-service


ENV SPEECH_TO_TEXT_SERVICE_VERSION=${SPEECH_TO_TEXT_SERVICE_VERSION}
ENV APP_HOME=${APP_HOME}
ENV APP_DATA_LOCATION=${APP_DATA_LOCATION}
ENV SPEECH_TO_TEXT_CONFIG_DIRECTORY_LOCATION=${SPEECH_TO_TEXT_CONFIG_DIRECTORY_LOCATION}
ENV SPEECH_TO_TEXT_CONFIGURATION_FILE_LOCATION=${SPEECH_TO_TEXT_CONFIG_DIRECTORY_LOCATION}/speech-to-text-service.yaml
ENV SPEECH_TO_TEXT_AUDIO_FILE_STORAGE_LOCATION=${AUDIO_FILE_STORAGE_LOCATION}
ENV SPEECH_TO_TEXT_AUDIO_FILE_STORAGE_SHARED_STORAGE_LOCATION=${SHARED_AUDIO_FILE_STORAGE_LOCATION}
ENV SPEECH_TO_TEXT_RESULT_STORAGE_LOCATION=${RESULT_STORAGE_LOCATION}
ENV SPEECH_TO_TEXT_RESULT_STORAGE_SHARED_STORAGE_LOCATION=${SHARED_RESULT_STORAGE_LOCATION}
ENV SPEECH_TO_TEXT_DB_SQL_FILE_PATH=${APP_DATA_LOCATION}/speech-to-text-db.sqlite

# Speech to text service configuration variables
ENV SPEECH_TO_TEXT_DB_MAXIMUM_POOL_SIZE=4
ENV SPEECH_TO_TEXT_DB_MINIMUM_IDLE_SIZE=1
ENV SPEECH_TO_TEXT_DB_CONNECTION_TIMEOUT_MS=30000

ENV SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_ENABLED=true
ENV SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_DEVICE_TYPE="cpu"
ENV SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_DEVICE_TYPE_CPU_NUMBER_OF_THREADS=0
ENV SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_DEVICE_TYPE_GPU_NUMBER=0

ENV SPEECH_TO_TEXT_GRPC_SERVER_PORT=8080
ENV SPEECH_TO_TEXT_TASK_SCHEDULER_NUMBER_OF_PARALLEL_TASKS=1
ENV SPEECH_TO_TEXT_TASK_SCHEDULER_NUMBER_OF_PARLLEL_TASKS=1

ENV SPEECH_TO_TEXT_AUTHENTICATION_TYPE="none"
ENV SPEECH_TO_TEXT_AUTHENTICATION_CERTIFICATE_ROOT_CERTIFICATE_PATH=""

ENV SPEECH_TO_TEXT_GRPC_SERVER_CERTIFICATE_PATH=
ENV SPEECH_TO_TEXT_GRPC_SERVER_PRIVATE_KEY_PATH=
ENV SPEECH_TO_TEXT_GRPC_SERVER_PRIVATE_KEY_PASSWORD_FILE=
ENV SPEECH_TO_TEXT_GRPC_SERVER_PRIVATE_KEY_PASSWORD_FILE_PATH=""


# Copy entrypoint script into container
COPY docker/speech-to-text-service-uni-erfurt-entrypoint.sh /usr/local/bin/speech-to-text-service-uni-erfurt-entrypoint.sh
RUN chmod 755 /usr/local/bin/speech-to-text-service-uni-erfurt-entrypoint.sh

# Install required packages
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        python3 \
        python3-pip \
        python3-venv \
        ffmpeg \
        ca-certificates \
        build-essential \
        rustc \
        cargo \
        unzip \
    && pip3 install --no-cache-dir --break-system-packages -U openai-whisper \
    && pip3 cache purge || true \
    && apt-get purge -y --auto-remove build-essential rustc cargo \
    && rm -rf /var/lib/apt/lists/*

# Copy and extract service jar and libraries
COPY speech-to-text-service-uni-erfurt/target/*-distribution.zip /tmp/distribution.zip
RUN mkdir -p ${APP_HOME} \
    && unzip /tmp/distribution.zip -d ${APP_HOME} \
    && mv "$APP_HOME"/speech-to-text-service-uni-erfurt-*.jar "$APP_HOME"/speech-to-text-service.jar \
    && rm /tmp/distribution.zip

# Create required directories
RUN mkdir -p ${APP_DATA_LOCATION} \
    && mkdir -p ${SHARED_AUDIO_FILE_STORAGE_LOCATION} \
    && mkdir -p ${AUDIO_FILE_STORAGE_LOCATION} \
    && mkdir -p ${RESULT_STORAGE_LOCATION} \
    && mkdir -p ${SHARED_RESULT_STORAGE_LOCATION} \
    && mkdir -p ${SPEECH_TO_TEXT_CONFIG_DIRECTORY_LOCATION}


ENTRYPOINT ["/usr/local/bin/speech-to-text-service-uni-erfurt-entrypoint.sh"]
