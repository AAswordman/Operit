#!/usr/bin/env bash
set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
echo "build_ffmpeg_kit_wsl.sh is retained for command compatibility; use build_android.sh." >&2
exec "${SCRIPT_DIR}/build_android.sh" "$@"
