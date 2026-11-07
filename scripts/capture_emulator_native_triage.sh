#!/usr/bin/env bash
# Failure-only host triage. coredumpctl manages its temporary core; retain text
# only, never a raw core, process environment, local variables or signing data.
# The workflow bounds this entire script to 85 seconds (+5 seconds to terminate).
set -u
output=app/build/reports/emulator-host/native-triage
mkdir -p "$output" || exit 0

triage() {
  local debugger commands result executable
  printf 'Native emulator triage: '
  date -u '+%Y-%m-%dT%H:%M:%SZ'
  if ! command -v coredumpctl >/dev/null 2>&1; then
    echo 'coredumpctl is unavailable; existing host diagnostics are retained.'
    return
  fi
  if ! command -v gdb >/dev/null 2>&1; then
    # Runner image contents can change. Install only on a failed device stage,
    # only when missing, and do not spend the diagnostic budget retrying mirrors.
    timeout --kill-after=2s 25s sudo -n env DEBIAN_FRONTEND=noninteractive \
      apt-get --yes --no-install-recommends -o Dpkg::Use-Pty=0 \
      -o Acquire::Retries=0 -o Acquire::http::Timeout=10 install gdb || true
  fi
  debugger=$(command -v gdb) || { echo 'GDB is unavailable; native triage was not run.'; return; }
  executable="${ANDROID_HOME:-/usr/local/lib/android/sdk}/emulator/qemu/linux-x86_64/qemu-system-x86_64-headless"
  if [ ! -x "$executable" ]; then
    echo 'The expected headless qemu executable is unavailable.'
    return
  fi
  commands=$(mktemp /tmp/snowglobe-native-gdb.XXXXXX) || return
  trap 'rm -f "$commands"' EXIT
  cat > "$commands" <<'GDB'
set pagination off
set print frame-info location-and-address
python
import gdb
for command in ('info proc mappings', 'info sharedlibrary', 'info registers',
                'x/20i $pc-32', 'thread apply all bt 8'):
    try:
        gdb.execute(command)
    except gdb.error as error:
        print('Diagnostic unavailable: ' + str(error))
end
GDB
  # --debugger-arguments is supported by systemd >=248 (runner uses 255).
  # https://www.freedesktop.org/software/systemd/man/latest/coredumpctl.html
  # Initial commands apply BEFORE loading the core, including its first frame.
  timeout --kill-after=3s 75s sudo -n coredumpctl --no-pager --since '-25 min' \
    --debugger="$debugger" \
    --debugger-arguments="--batch --nx --quiet -iex 'set auto-load off' -iex 'set debuginfod enabled off' -iex 'set print frame-arguments none' -iex 'set print entry-values no' -x '$commands'" \
    debug "$executable"
  result=$?
  printf '\nNative debugger exit status: %s\n' "$result"
  rm -f "$commands"
  trap - EXIT
}

# Cap the entire retained report, including package-manager and debugger output.
# Reaching the cap closes the producer pipe; the outer timeout also bounds it.
triage 2>&1 | head -c 1048576 > "$output/report.txt" || true
exit 0
