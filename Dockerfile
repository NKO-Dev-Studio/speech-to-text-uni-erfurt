FROM eclipse-temurin:25-jre-noble

ARG SPEECH_TO_TEXT_SERVICE_VERSION="1.0.1-SNAPSHOT"

LABEL org.opencontainers.image.title="speech-to-text-service-uni-erfurt"
LABEL org.opencontainers.image.description="Java-based service to execute speech to text tasks. Service communication is handled over gRPC"
LABEL org.opencontainers.image.version="${SPEECH_TO_TEXT_SERVICE_VERSION}"
LABEL org.opencontainers.image.authors="Nico Kotlenga <nico@nko-dev.studio>"
LABEL org.opencontainers.image.source="https://github.com/NKO-Dev-Studio/speech-to-text-uni-erfurt"
LABEL org.opencontainers.image.licenses="Apache License 2.0"

ENV SPEECH_TO_TEXT_SERVICE_VERSION=${SPEECH_TO_TEXT_SERVICE_VERSION}

# Speech to text service configuration variables
ENV SPEECH_TO_TEXT_DB_MAXIMUM_POOL_SIZE=4
ENV SPEECH_TO_TEXT_DB_MINIMUM_IDLE_SIZE=1
ENV SPEECH_TO_TEXT_DB_CONNECTION_TIMEOUT_MS=30000

ENV SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_ENABLED=true
ENV SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_DEVICE_TYPE="cpu"
ENV SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_DEVICE_TYPE_CPU_NUMBER_OF_THREADS=0
ENV SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_DEVICE_TYPE_GPU_NUMBER=0

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
COPY docker/logback.xml /etc/opt/speech-to-text-service/logback.xml
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
RUN mkdir -p /opt/speech-to-text-service-uni-erfurt \
    && unzip /tmp/distribution.zip -d /opt/speech-to-text-service-uni-erfurt \
    && mv "/opt/speech-to-text-service-uni-erfurt"/speech-to-text-service-uni-erfurt-*.jar "/opt/speech-to-text-service-uni-erfurt"/speech-to-text-service.jar \
    && rm /tmp/distribution.zip

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/speech-to-text-service-uni-erfurt-entrypoint.sh"]
