#!/usr/bin/env bash
set -euo pipefail

positional=()
output_dir=""
output_format=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output_dir)
      output_dir="$2"
      shift 2
      ;;
    --output_format)
      output_format="$2"
      shift 2
      ;;
    --*)
      shift 2
      ;;
    *)
      positional+=("$1")
      shift
      ;;
  esac
done

for i in $(seq 1 8000); do
  printf 'whisper-noise-%04d-abcdefghijklmnopqrstuvwxyz0123456789\n' "$i" >&2
done

audio="${positional[-1]}"
audio_name="$(basename "$audio")"
audio_stem="${audio_name%.*}"
mkdir -p "$output_dir"
printf 'mock-result\n' > "$output_dir/$audio_stem.$output_format"
