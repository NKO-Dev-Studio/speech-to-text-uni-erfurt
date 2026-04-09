#!/usr/bin/env bash
set -euo pipefail

out="/tmp/whisper-mock-arguments.json"

json="{"
positional=()
output_dir=""
output_format=""

escape_json() {
  # escape backslash and quotes for JSON strings
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --*=*)
      key="${1%%=*}"; key="${key#--}"
      value="${1#*=}"
      shift
      ;;
    --*)
      key="${1#--}"
      # if next token missing or looks like another flag, store null and do NOT consume next
      if [[ $# -lt 2 || "${2-}" == --* ]]; then
        value=""
        shift
        json+="\"$(escape_json "$key")\":null,"
        continue
      fi
      value="$2"
      shift 2
      ;;
    *)
      positional+=("$1")
      shift
      continue
      ;;
  esac

  json+="\"$(escape_json "$key")\":\"$(escape_json "$value")\","
  if [[ "$key" == "output_dir" ]]; then
    output_dir="$value"
  fi
  if [[ "$key" == "output_format" ]]; then
    output_format="$value"
  fi
done

# audioFilePath = last positional arg (your case: the final argument)
if (( ${#positional[@]} > 0 )); then
  audio="${positional[-1]}"
  json+="\"audioFilePath\":\"$(escape_json "$audio")\","
else
  json+="\"audioFilePath\":null,"
fi

# remove trailing comma and close
json="${json%,}}"

printf '%s\n' "$json" > "$out"
if [[ -n "$output_dir" && -n "$output_format" && ${#positional[@]} -gt 0 ]]; then
  audio="${positional[-1]}"
  audio_name="$(basename "$audio")"
  audio_stem="${audio_name%.*}"
  mkdir -p "$output_dir"
  printf 'mock-result\n' > "$output_dir/$audio_stem.$output_format"
fi
echo "$out"
