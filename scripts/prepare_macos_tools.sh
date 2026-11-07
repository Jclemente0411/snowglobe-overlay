#!/usr/bin/env bash
# Bash 3.2 compatible. Run before any timeout/sha256sum use on the Intel Mac job.
set -euo pipefail
if [ "$(uname -s)" != Darwin ]; then exit 0; fi
python3 - <<'PY'
import os
import signal
import subprocess
from pathlib import Path

environment = dict(os.environ, HOMEBREW_NO_AUTO_UPDATE='1', HOMEBREW_NO_INSTALL_CLEANUP='1')
installed = subprocess.run(['brew', 'list', '--versions', 'coreutils'],
                           env=environment, capture_output=True, text=True, timeout=20)
if installed.returncode != 0 or not installed.stdout.strip():
    # Bootstrap has no GNU timeout yet. Bound the complete process group instead.
    process = subprocess.Popen(['brew', 'install', 'coreutils'], env=environment, start_new_session=True)
    try:
        result = process.wait(timeout=240)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()
        raise SystemExit('GNU coreutils installation exceeded its bounded time budget')
    if result != 0:
        raise SystemExit('GNU coreutils installation failed')
prefix = subprocess.check_output(['brew', '--prefix', 'coreutils'], env=environment,
                                 text=True, timeout=20).strip()
gnubin = Path(prefix) / 'libexec' / 'gnubin'
for command in ('timeout', 'sha256sum'):
    executable = gnubin / command
    if not executable.is_file() or not os.access(executable, os.X_OK):
        raise SystemExit('Required GNU tool is unavailable: ' + command)
    subprocess.run([str(executable), '--version'], check=True, timeout=5)
github_path = Path(os.environ['GITHUB_PATH'])
existing = github_path.read_text().splitlines() if github_path.exists() else []
if str(gnubin) not in existing:
    with github_path.open('a') as output:
        output.write(str(gnubin) + '\n')
print('GNU command-line tools are ready for subsequent macOS steps.')
PY
