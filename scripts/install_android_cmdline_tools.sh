#!/usr/bin/env bash
# Old avdmanager silently writes target=android-0 for dotted API levels such as 37.0.
# Pin the official 23.0 package and replace the latest path used by emulator-runner.
set -euo pipefail
android_sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [ -z "$android_sdk" ] || [ ! -d "$android_sdk" ]; then
  echo '::error::An existing Android SDK directory is required'
  exit 1
fi
tools_parent="$android_sdk/cmdline-tools"
latest="$tools_parent/latest"
revision=''
if [ -f "$latest/source.properties" ]; then
  revision=$(sed -n 's/^Pkg.Revision[[:space:]]*=[[:space:]]*//p' "$latest/source.properties" | tr -d '\r')
fi
if [ "$revision" != 23.0 ]; then
  work=$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/snowglobe-cmdline.XXXXXX")
  trap 'rm -rf "$work"' EXIT
  archive=${1:-$work/commandline-tools.zip}
  if [ "$#" -eq 0 ]; then
    curl --fail --location --silent --show-error \
      https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip -o "$archive"
  fi
  # SHA256 computed from bytes verified against Google's repository2-3.xml:
  # 23.0, 181052239 bytes, SHA1 e025545c62a8e64c7559119566a569fb1dec5f60 (2026-11-07).
  printf '%s  %s\n' 0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8 "$archive" | sha256sum -c -
  unzip -q "$archive" -d "$work/extracted"
  incoming="$work/extracted/cmdline-tools"
  grep -Eq '^Pkg.Revision[[:space:]]*=[[:space:]]*23[.]0[[:space:]]*$' "$incoming/source.properties"
  test -x "$incoming/bin/avdmanager"
  mkdir -p "$tools_parent"
  if [ -e "$latest" ] || [ -L "$latest" ]; then mv "$latest" "$work/replaced-latest"; fi
  mv "$incoming" "$latest"
fi
"$latest/bin/avdmanager" list device > /dev/null
printf 'Using Android command-line tools 23.0 from %s\n' "$latest"
if [ -n "${GITHUB_PATH:-}" ]; then printf '%s\n' "$latest/bin" >> "$GITHUB_PATH"; fi
