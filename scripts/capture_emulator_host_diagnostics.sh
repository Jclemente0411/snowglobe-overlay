#!/usr/bin/env bash
# Best-effort host evidence. Never print environment variables or process arguments.
# Called before launch, around device tests, and by an always-run workflow step.
set -u
phase=${1:-snapshot}
case "$phase" in
  before-emulator|device-start|device-finish|after-emulator) ;;
  *) echo 'Unknown emulator diagnostic phase' >&2; exit 2 ;;
esac
output="app/build/reports/emulator-host/$phase"
mkdir -p "$output" || exit 0

capture() {
  local filename=$1
  shift
  timeout 5s "$@" 2>&1 | head -c 262144 > "$output/$filename" || true
}

capture timestamp.txt date -u '+%Y-%m-%dT%H:%M:%SZ'
capture uname.txt uname -a
capture lscpu.txt lscpu
capture memory.txt free -m
capture host-meminfo.txt cat /proc/meminfo
capture memory-pressure.txt cat /proc/pressure/memory
capture cpu-pressure.txt cat /proc/pressure/cpu
capture disk.txt df -h . /tmp
capture processes.txt ps -eo pid,ppid,comm,rss,vsz,pcpu,pmem,etimes --sort=-rss
# Preserve the tail, where a recent OOM kill or native crash would be recorded.
timeout 5s sudo -n dmesg --ctime 2>&1 | tail -n 400 | head -c 262144 > "$output/kernel.txt" || true
capture kernel-journal.txt sudo -n journalctl -k --since '-20 min' --no-pager -n 400
capture coredumps.txt sudo -n coredumpctl --no-pager --no-legend list --since '-20 min'
capture emulator-coredump-info.txt sudo -n coredumpctl --no-pager info --since '-20 min' \
  "${ANDROID_HOME:-/usr/local/lib/android/sdk}/emulator/qemu/linux-x86_64/qemu-system-x86_64-headless"

timeout 10s python3 - "$output" <<'PY' || true
import getpass
import os
import sys
from pathlib import Path

output = Path(sys.argv[1])


def read_bounded(path, maximum=16384):
    try:
        with path.open('rb') as handle:
            return handle.read(maximum).decode('utf-8', errors='replace')
    except OSError as error:
        return f'Unavailable: {error}\n'


# Record the current cgroup and its ancestors: a host can have free RAM while a
# runner-specific memory limit still kills qemu. Include v1 and v2 counters.
cgroup_text = read_bounded(Path('/proc/self/cgroup'))
names = ('memory.current', 'memory.max', 'memory.peak', 'memory.events',
         'memory.events.local', 'memory.pressure', 'memory.swap.current',
         'memory.swap.max', 'memory.usage_in_bytes', 'memory.max_usage_in_bytes',
         'memory.limit_in_bytes', 'memory.failcnt', 'memory.oom_control')
root = Path('/sys/fs/cgroup')
folders = {root, root / 'memory'}
for line in cgroup_text.splitlines():
    fields = line.split(':', 2)
    if len(fields) != 3:
        continue
    base = root / 'memory' if 'memory' in fields[1].split(',') else root
    folder = base / fields[2].lstrip('/')
    for _ in range(8):
        if folder.is_relative_to(root):
            folders.add(folder)
        if folder == root or folder.parent == folder:
            break
        folder = folder.parent
with (output / 'cgroups.txt').open('w') as report:
    report.write(cgroup_text + '\n')
    for folder in sorted(folders)[:32]:
        for name in names:
            path = folder / name
            if path.is_file():
                report.write(f'\n{path}\n{read_bounded(path)}')

with (output / 'emulator-processes.txt').open('w') as report:
    selected = 0
    mapped = 0
    for process in Path('/proc').iterdir():
        if not process.name.isdigit():
            continue
        name = read_bounded(process / 'comm', 128).strip()
        # Keep the executable load bias before a native crash removes /proc/PID.
        # Never read process memory, command lines or environment variables.
        try:
            executable = (process / 'exe').resolve(strict=True)
            if executable.name == 'qemu-system-x86_64-headless' and mapped < 4:
                with (process / 'maps').open('rb') as handle:
                    (output / f'qemu-{process.name}-maps.txt').write_bytes(handle.read(262144))
                mapped += 1
        except OSError:
            pass
        if not any(part in name.lower() for part in ('qemu', 'emulator', 'java', 'adb', 'netsim')):
            continue
        selected += 1
        if selected > 32:
            if selected == 33:
                report.write('Process record limit reached.\n')
            continue
        for filename in ('status', 'oom_score', 'oom_score_adj', 'cgroup'):
            report.write(f'\nPID {process.name} {filename}\n{read_bounded(process / filename)}')

# Use the same dotted-API validator as the device gate, then limit the selected
# source.properties to the SDK system-images tree. Diagnostic failure is benign.
with (output / 'system-image-selection.txt').open('w') as report:
    try:
        sys.path.insert(0, str(Path('scripts').resolve()))
        from verify_android_avd import properties, verify
        name = os.environ['SNOWGLOBE_AVD_NAME']
        expected = os.environ['EXPECTED_ANDROID_API']
        avd_home = Path(os.environ.get('ANDROID_AVD_HOME', str(Path.home() / '.android/avd')))
        verify(avd_home, name, expected)
        root_ini = properties(avd_home / (name + '.ini'))
        config = properties(Path(root_ini['path']) / 'config.ini')
        sdk = Path(os.environ['ANDROID_HOME']).resolve(strict=True)
        image = (sdk / config['image.sysdir.1'].replace('\\', '/')).resolve(strict=True)
        if not image.is_relative_to((sdk / 'system-images').resolve(strict=True)):
            raise ValueError('Selected image is outside the SDK system-images tree')
        source = image / 'source.properties'
        with source.open('rb') as handle:
            (output / 'system-image-source.properties').write_bytes(handle.read(16384))
        report.write(f'Verified AVD: {name}\nExpected API: {expected}\nSource: {source}\n')
    except (KeyError, ValueError, OSError) as error:
        report.write(f'Unavailable: {error}\n')

# Crashpad uses emu-crash-<version>.db directories under /tmp/android-<user>.
# Retain metadata and short text tails only; never upload raw cores/minidumps.
roots = {Path('/tmp/android-runner'), Path('/tmp') / ('android-' + getpass.getuser())}
record_count = 0
with (output / 'emulator-crashes.txt').open('w') as report:
    for crash_root in sorted(roots):
        if not crash_root.is_dir():
            report.write(f'{crash_root}: absent\n')
            continue
        for directory, children, files in os.walk(crash_root, followlinks=False):
            relative = Path(directory).relative_to(crash_root)
            if len(relative.parts) >= 5:
                children[:] = []
            for name in sorted(files):
                path = Path(directory) / name
                if 'crash' not in str(path).lower() or path.is_symlink():
                    continue
                record_count += 1
                if record_count > 128:
                    report.write('Crash record limit reached.\n')
                    break
                try:
                    stat = path.stat()
                    report.write(f'{path}: bytes={stat.st_size}, modified={stat.st_mtime}\n')
                    if path.suffix.lower() in ('.txt', '.log'):
                        with path.open('rb') as handle:
                            handle.seek(max(0, stat.st_size - 65536))
                            report.write(handle.read(65536).decode('utf-8', errors='replace') + '\n')
                except OSError as error:
                    report.write(f'Unavailable: {error}\n')
            if record_count > 128:
                break
PY
exit 0
