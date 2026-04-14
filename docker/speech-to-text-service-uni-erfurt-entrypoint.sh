#!/bin/sh
set -eu

get_env_or_default() {
  variable_name="$1"
  default_value="${2-}"
  eval "variable_value=\${$variable_name-}"
  if [ -n "$variable_value" ]; then
    printf '%s' "$variable_value"
    return
  fi

  printf '%s' "$default_value"
}

require_non_empty() {
  variable_name="$1"
  variable_value="$2"
  if [ -z "$variable_value" ]; then
    echo "Missing required environment variable: $variable_name" >&2
    exit 1
  fi
}

require_file() {
  variable_name="$1"
  file_path="$2"
  require_non_empty "$variable_name" "$file_path"
  if [ ! -f "$file_path" ]; then
    echo "Configured file for $variable_name does not exist: $file_path" >&2
    exit 1
  fi
}

require_directory_parent() {
  target_path="$1"
  parent_directory=$(dirname "$target_path")
  mkdir -p "$parent_directory"
}

yaml_quote() {
  printf '"%s"' "$(printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g')"
}

APP_HOME=$(get_env_or_default APP_HOME "/opt/speech-to-text-service-uni-erfurt")
APP_DATA_LOCATION=$(get_env_or_default APP_DATA_LOCATION "/var/opt/speech-to-text-service-uni-erfurt")
CONFIG_FILE_PATH=$(get_env_or_default SPEECH_TO_TEXT_CONFIGURATION_FILE_LOCATION "$APP_DATA_LOCATION/config.yaml")

AUDIO_FILE_STORAGE_LOCATION=$(get_env_or_default SPEECH_TO_TEXT_AUDIO_FILE_STORAGE_LOCATION "$APP_DATA_LOCATION/audio-files")
SHARED_AUDIO_FILE_STORAGE_LOCATION=$(get_env_or_default SPEECH_TO_TEXT_AUDIO_FILE_STORAGE_SHARED_STORAGE_LOCATION "")
RESULT_STORAGE_LOCATION=$(get_env_or_default SPEECH_TO_TEXT_RESULT_STORAGE_LOCATION "$APP_DATA_LOCATION/results")
SHARED_RESULT_STORAGE_LOCATION=$(get_env_or_default SPEECH_TO_TEXT_RESULT_STORAGE_SHARED_STORAGE_LOCATION "")

DB_SQL_FILE_PATH=$(get_env_or_default SPEECH_TO_TEXT_DB_SQL_FILE_PATH "$APP_DATA_LOCATION/speech-to-text-db.sqlite")
DB_MAXIMUM_POOL_SIZE=$(get_env_or_default SPEECH_TO_TEXT_DB_MAXIMUM_POOL_SIZE "4")
DB_MINIMUM_IDLE_SIZE=$(get_env_or_default SPEECH_TO_TEXT_DB_MINIMUM_IDLE_SIZE "1")
DB_CONNECTION_TIMEOUT_MS=$(get_env_or_default SPEECH_TO_TEXT_DB_CONNECTION_TIMEOUT_MS "30000")

LOCAL_WHISPER_ENABLED=$(get_env_or_default SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_ENABLED "true")
LOCAL_WHISPER_EXECUTABLE="/usr/local/bin/whisper"
LOCAL_WHISPER_DEVICE_TYPE=$(get_env_or_default SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_DEVICE_TYPE "cpu")
LOCAL_WHISPER_CPU_THREADS=$(get_env_or_default SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_DEVICE_TYPE_CPU_NUMBER_OF_THREADS "0")
LOCAL_WHISPER_GPU_NUMBER=$(get_env_or_default SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_DEVICE_TYPE_GPU_NUMBER "0")

TASK_SCHEDULER_NUMBER_OF_PARALLEL_TASKS=$(
  get_env_or_default \
    SPEECH_TO_TEXT_TASK_SCHEDULER_NUMBER_OF_PARALLEL_TASKS \
    "$(get_env_or_default SPEECH_TO_TEXT_TASK_SCHEDULER_NUMBER_OF_PARLLEL_TASKS "1")"
)

AUTHENTICATION_TYPE=$(get_env_or_default SPEECH_TO_TEXT_AUTHENTICATION_TYPE "none")
ROOT_CERTIFICATE_PATH=$(
  get_env_or_default SPEECH_TO_TEXT_AUTHENTICATION_CERTIFICATE_ROOT_CERTIFICATE_PATH ""
)

GRPC_SERVER_PORT=$(get_env_or_default SPEECH_TO_TEXT_GRPC_SERVER_PORT "8080")
SERVER_CERTIFICATE_PATH=$(get_env_or_default SPEECH_TO_TEXT_GRPC_SERVER_CERTIFICATE_PATH "")
SERVER_PRIVATE_KEY_PATH=$(get_env_or_default SPEECH_TO_TEXT_GRPC_SERVER_PRIVATE_KEY_PATH "")
SERVER_PRIVATE_KEY_PASSWORD_FILE=$(
  get_env_or_default \
    SPEECH_TO_TEXT_GRPC_SERVER_PRIVATE_KEY_PASSWORD_FILE \
    "$(get_env_or_default SPEECH_TO_TEXT_GRPC_SERVER_PRIVATE_KEY_PASSWORD_FILE_PATH "")"
)

case "$LOCAL_WHISPER_ENABLED" in
  true) ;;
  false)
    echo "SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_ENABLED=false is unsupported because no other engine is available" >&2
    exit 1
    ;;
  *)
    echo "SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_ENABLED must be either true or false" >&2
    exit 1
    ;;
esac

case "$LOCAL_WHISPER_DEVICE_TYPE" in
  cpu|gpu|mps) ;;
  *)
    echo "SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_DEVICE_TYPE must be one of: cpu, gpu, mps" >&2
    exit 1
    ;;
esac

case "$AUTHENTICATION_TYPE" in
  none|certificate) ;;
  *)
    echo "SPEECH_TO_TEXT_AUTHENTICATION_TYPE must be either none or certificate" >&2
    exit 1
    ;;
esac

require_non_empty SPEECH_TO_TEXT_AUDIO_FILE_STORAGE_LOCATION "$AUDIO_FILE_STORAGE_LOCATION"
require_non_empty SPEECH_TO_TEXT_RESULT_STORAGE_LOCATION "$RESULT_STORAGE_LOCATION"
require_non_empty SPEECH_TO_TEXT_DB_SQL_FILE_PATH "$DB_SQL_FILE_PATH"
require_non_empty SPEECH_TO_TEXT_ENGINE_LOCAL_WHISPER_EXECUTABLE "$LOCAL_WHISPER_EXECUTABLE"
require_non_empty SPEECH_TO_TEXT_GRPC_SERVER_PORT "$GRPC_SERVER_PORT"

require_file SPEECH_TO_TEXT_GRPC_SERVER_CERTIFICATE_PATH "$SERVER_CERTIFICATE_PATH"
require_file SPEECH_TO_TEXT_GRPC_SERVER_PRIVATE_KEY_PATH "$SERVER_PRIVATE_KEY_PATH"

if [ "$AUTHENTICATION_TYPE" = "certificate" ]; then
  require_file SPEECH_TO_TEXT_AUTHENTICATION_CERTIFICATE_ROOT_CERTIFICATE_PATH "$ROOT_CERTIFICATE_PATH"
fi

if [ -n "$SERVER_PRIVATE_KEY_PASSWORD_FILE" ] && [ ! -f "$SERVER_PRIVATE_KEY_PASSWORD_FILE" ]; then
  echo "Configured file for SPEECH_TO_TEXT_GRPC_SERVER_PRIVATE_KEY_PASSWORD_FILE does not exist: $SERVER_PRIVATE_KEY_PASSWORD_FILE" >&2
  exit 1
fi

mkdir -p "$APP_DATA_LOCATION" "$AUDIO_FILE_STORAGE_LOCATION" "$RESULT_STORAGE_LOCATION"
if [ -n "$SHARED_AUDIO_FILE_STORAGE_LOCATION" ]; then
  mkdir -p "$SHARED_AUDIO_FILE_STORAGE_LOCATION"
fi
if [ -n "$SHARED_RESULT_STORAGE_LOCATION" ]; then
  mkdir -p "$SHARED_RESULT_STORAGE_LOCATION"
fi
require_directory_parent "$DB_SQL_FILE_PATH"
require_directory_parent "$CONFIG_FILE_PATH"

{
  printf 'audioFileStorage:\n'
  printf '  audioFileStorageLocation: %s\n' "$(yaml_quote "$AUDIO_FILE_STORAGE_LOCATION")"
  if [ -n "$SHARED_AUDIO_FILE_STORAGE_LOCATION" ]; then
    printf '  sharedStorageLocation: %s\n' "$(yaml_quote "$SHARED_AUDIO_FILE_STORAGE_LOCATION")"
  fi
  printf '\n'

  printf 'resultStorage:\n'
  printf '  resultDirectoryLocation: %s\n' "$(yaml_quote "$RESULT_STORAGE_LOCATION")"
  if [ -n "$SHARED_RESULT_STORAGE_LOCATION" ]; then
    printf '  sharedStorageLocation: %s\n' "$(yaml_quote "$SHARED_RESULT_STORAGE_LOCATION")"
  fi
  printf '\n'

  printf 'database:\n'
  printf '  sqlFilePath: %s\n' "$(yaml_quote "$DB_SQL_FILE_PATH")"
  printf '  maximumPoolSize: %s\n' "$DB_MAXIMUM_POOL_SIZE"
  printf '  minimumIdleSize: %s\n' "$DB_MINIMUM_IDLE_SIZE"
  printf '  connectionTimeoutMs: %s\n' "$DB_CONNECTION_TIMEOUT_MS"
  printf '\n'

  printf 'engines:\n'
  printf '  localWhisper:\n'
  printf '    whisperExecutable: %s\n' "$(yaml_quote "$LOCAL_WHISPER_EXECUTABLE")"
  printf '    device:\n'
  printf '      type: %s\n' "$(yaml_quote "$LOCAL_WHISPER_DEVICE_TYPE")"
  case "$LOCAL_WHISPER_DEVICE_TYPE" in
    cpu)
      printf '      numberOfThreads: %s\n' "$LOCAL_WHISPER_CPU_THREADS"
      ;;
    gpu)
      printf '      gpuNumber: %s\n' "$LOCAL_WHISPER_GPU_NUMBER"
      ;;
  esac
  printf '\n'

  printf 'taskScheduler:\n'
  printf '  numberOfParallelTasks: %s\n' "$TASK_SCHEDULER_NUMBER_OF_PARALLEL_TASKS"
  printf '\n'

  printf 'authentication:\n'
  printf '  type: %s\n' "$(yaml_quote "$AUTHENTICATION_TYPE")"
  if [ "$AUTHENTICATION_TYPE" = "certificate" ]; then
    printf '  rootCertificatePath: %s\n' "$(yaml_quote "$ROOT_CERTIFICATE_PATH")"
  fi
  printf '\n'

  printf 'grpcServer:\n'
  printf '  port: %s\n' "$GRPC_SERVER_PORT"
  printf '  serverCertificatePath: %s\n' "$(yaml_quote "$SERVER_CERTIFICATE_PATH")"
  printf '  serverPrivateKeyPath: %s\n' "$(yaml_quote "$SERVER_PRIVATE_KEY_PATH")"
  if [ -n "$SERVER_PRIVATE_KEY_PASSWORD_FILE" ]; then
    printf '  serverPrivateKeyPasswordFile: %s\n' "$(yaml_quote "$SERVER_PRIVATE_KEY_PASSWORD_FILE")"
  fi
} >"$CONFIG_FILE_PATH"

exec java -jar "$APP_HOME/speech-to-text-service.jar" "$CONFIG_FILE_PATH"
