#!/usr/bin/env bash
# Start one display for the complete emulator step, not a short-lived probe.
# Mesa's documented software OpenGL path: https://docs.mesa3d.org/envvars.html
set -euo pipefail
if [ "$(uname -s)" != Linux ]; then
  echo '::error::The llvmpipe emulator profile requires Linux'
  exit 1
fi
mesa_evidence=app/build/reports/emulator-host/mesa-setup
mkdir -p "$mesa_evidence"
if ! command -v Xvfb >/dev/null 2>&1 || ! command -v glxinfo >/dev/null 2>&1 || \
   ! command -v xdpyinfo >/dev/null 2>&1 || \
   ! dpkg-query -s libgl1-mesa-dri libglx-mesa0 >/dev/null 2>&1; then
  timeout --kill-after=5s 60s sudo -n apt-get -o Acquire::Retries=0 -o Acquire::http::Timeout=15 update \
    > "$mesa_evidence/packages-update.txt" 2>&1
  timeout --kill-after=5s 90s sudo -n env DEBIAN_FRONTEND=noninteractive \
    apt-get --yes --no-install-recommends -o Dpkg::Use-Pty=0 -o Acquire::Retries=0 \
    -o Acquire::http::Timeout=15 install xvfb x11-utils mesa-utils libgl1-mesa-dri libglx-mesa0 \
    > "$mesa_evidence/packages-install.txt" 2>&1
fi
dpkg-query -W -f='${binary:Package}\t${Version}\n' xvfb x11-utils mesa-utils libgl1-mesa-dri libglx-mesa0 \
  > "$mesa_evidence/packages.txt"
export LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe
Xvfb -displayfd 3 -screen 0 1280x720x24 -nolisten tcp \
  3> "$mesa_evidence/display-number.txt" > "$mesa_evidence/xvfb.log" 2>&1 &
mesa_display_pid=$!
printf '%s\n' "$mesa_display_pid" > "$mesa_evidence/xvfb.pid"
# A failed setup must not leave our display server running. Successful setup
# deliberately keeps it alive for emulator-runner; the CI job owns its lifetime.
trap 'kill "$mesa_display_pid" 2>/dev/null || true' EXIT
mesa_deadline=$((SECONDS + 30))
mesa_ready=false
while [ "$SECONDS" -lt "$mesa_deadline" ]; do
  if ! kill -0 "$mesa_display_pid" 2>/dev/null; then
    echo '::error::Xvfb exited before the emulator display became ready'
    exit 1
  fi
  if [ -s "$mesa_evidence/display-number.txt" ]; then
    mesa_display_number=$(tr -d '\r\n' < "$mesa_evidence/display-number.txt")
    case "$mesa_display_number" in
      ''|*[!0-9]*) echo '::error::Invalid Xvfb display number'; exit 1 ;;
    esac
    export DISPLAY=":$mesa_display_number"
    if timeout 2s xdpyinfo -display "$DISPLAY" > "$mesa_evidence/display.txt" 2>&1; then
      mesa_ready=true
      break
    fi
  fi
  sleep 1
done
if [ "$mesa_ready" != true ]; then
  echo '::error::Xvfb did not become ready within 30 seconds'
  exit 1
fi
timeout 15s glxinfo -B > "$mesa_evidence/glxinfo.txt" 2>&1
python3 scripts/verify_llvmpipe_renderer.py --source host "$mesa_evidence/glxinfo.txt"
printf 'DISPLAY=%s\nLIBGL_ALWAYS_SOFTWARE=1\nGALLIUM_DRIVER=llvmpipe\n' "$DISPLAY" >> "${GITHUB_ENV:?GitHub environment file is required}"
trap - EXIT
