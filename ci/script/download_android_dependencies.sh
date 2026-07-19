#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <jvm|full> <destination-directory>" >&2
  exit 2
fi

profile="$1"
destination="$2"
case "$profile" in
  jvm|full) ;;
  *)
    echo "unsupported Android dependency profile: $profile" >&2
    exit 2
    ;;
esac

rm -rf "$destination"
mkdir -p "$destination"

venv_dir="${RUNNER_TEMP:?RUNNER_TEMP is required}/operit-gdown-venv"
python3 -m venv "$venv_dir"
"$venv_dir/bin/python" -m pip install --disable-pip-version-check gdown==6.1.0

download() {
  local file_id="$1"
  local file_name="$2"
  "$venv_dir/bin/python" -m gdown \
    "https://drive.google.com/uc?id=${file_id}" \
    --output "$destination/$file_name"
  test -s "$destination/$file_name"
}

download "1iiVkHVXLtcK6WX2LHwajTygMESn4bW4D" "libs.zip"

if [[ "$profile" == "full" ]]; then
  download "1UUv500OQLGwUje6zD1VuYZzNV_29CXLh" "models.zip"
  download "1yD7Xd3apvmNyAQcvfXeaer5WA1zzd97w" "subpack.zip"
  download "156f7qBnBMwuo8oAyNyF0wgBDN5iGv_Me" "jniLibs.zip"
fi
