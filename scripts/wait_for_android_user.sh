#!/usr/bin/env bash
# Run as one shell script: emulator-runner executes separate script lines separately.
set -euo pipefail
unlock_deadline=$((SECONDS + 180))
while [ "$SECONDS" -lt "$unlock_deadline" ]; do
  timeout 10s adb shell input keyevent KEYCODE_WAKEUP || true
  timeout 10s adb shell input keyevent 82 || true
  timeout 10s adb shell wm dismiss-keyguard || true
  current_user=$(timeout 10s adb shell am get-current-user | tr -d '\r') || current_user=''
  if [[ "$current_user" =~ ^[0-9]+$ ]]; then
    user_state=$(timeout 10s adb shell am get-started-user-state "$current_user" | tr -d '\r') || user_state=''
    # An unlocked background user does not mean the test's foreground user is ready.
    if [ "$user_state" = 'RUNNING_UNLOCKED' ]; then
      exit 0
    fi
  fi
  sleep 2
done
echo '::error::The emulator foreground user did not unlock within 180 seconds'
exit 1
