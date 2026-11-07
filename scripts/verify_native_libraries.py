"""Check bundle packaging and loadable ELF segments, including 16 KB RELRO safety."""
import argparse
import json
import struct
import zipfile
from pathlib import Path

PAGE_SIZE = 16384
PT_LOAD = 1
PT_GNU_RELRO = 0x6474E552
PF_W = 2
ABIS = {"arm64-v8a": (2, 183), "x86_64": (2, 62), "armeabi-v7a": (1, 40), "x86": (1, 3)}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def verify_packaging(config):
    native = config.get("optimizations", {}).get("uncompressNativeLibraries", {})
    require(native.get("enabled") is True, "Bundle must enable uncompressed native-library packaging")
    require(native.get("alignment") in ("PAGE_ALIGNMENT_16K", "PAGE_ALIGNMENT_64K"),
            "Bundle native-library packaging must request at least 16 KB alignment")


def verify_elf(data, name, abi):
    require(len(data) >= 16 and data[:4] == b"\x7fELF", f"Invalid ELF: {name}")
    require(data[5] == 1 and data[6] == 1, f"Unsupported ELF encoding/version: {name}")
    require(abi in ABIS, f"Unsupported native ABI: {name}")
    elf_class, machine = ABIS[abi]
    require(data[4] == elf_class, f"ELF class does not match {abi}: {name}")
    header_size = 64 if elf_class == 2 else 52
    require(len(data) >= header_size, f"Truncated ELF header: {name}")
    header_format = "<HHIQQQIHHHHHH" if elf_class == 2 else "<HHIIIIIHHHHHH"
    header = struct.unpack_from(header_format, data, 16)
    require(header[0] == 3 and header[1] == machine and header[2] == 1,
            f"Invalid shared-object type/machine/version: {name}")
    offset, size, number = header[4], header[8], header[9]
    program_format = "<IIQQQQQQ" if elf_class == 2 else "<IIIIIIII"
    require(header[7] == header_size and size == struct.calcsize(program_format),
            f"Invalid ELF program-header size: {name}")
    require(0 < number < 0xFFFF and offset >= header_size and offset + number * size <= len(data),
            f"Missing or truncated ELF program-header table: {name}")
    loads, relros = [], []
    for index in range(number):
        entry = struct.unpack_from(program_format, data, offset + index * size)
        if elf_class == 2:
            kind, flags, file_offset, address, _, file_size, memory_size, alignment = entry
        else:
            kind, file_offset, address, _, file_size, memory_size, flags, alignment = entry
        if kind == PT_LOAD:
            require(file_size <= memory_size, f"{name}: PT_LOAD file size exceeds memory size")
            require(file_size == 0 or file_offset + file_size <= len(data), f"{name}: PT_LOAD exceeds file bounds")
            require(address + memory_size <= 1 << (64 if elf_class == 2 else 32), f"{name}: PT_LOAD address overflow")
            require(alignment in (0, 1) or alignment & (alignment - 1) == 0,
                    f"{name}: PT_LOAD alignment is not a power of two")
            require(alignment <= 1 or (address - file_offset) % alignment == 0,
                    f"{name}: PT_LOAD address/file offset are incongruent")
            if elf_class == 2:
                require(alignment >= PAGE_SIZE, f"{name}: PT_LOAD alignment {alignment} is below 16 KB")
            loads.append((address, address + memory_size, flags))
        elif kind == PT_GNU_RELRO and memory_size:
            relros.append((address, address + memory_size))
    require(loads, f"No load segments: {name}")
    if elf_class == 2:
        # Android protects every page touched by RELRO. Linker padding may extend
        # its memory size past the containing LOAD (for example, AndroidX graphics
        # path). That is safe if page rounding cannot protect other writable data.
        # Bionic explicitly handles RELRO p_memsz >= the corresponding LOAD p_memsz:
        # https://android.googlesource.com/platform/bionic/+/android16-qpr2-release/linker/linker_phdr.cpp
        for start, end in relros:
            require(any(first <= start < last for first, last, _ in loads),
                    f"{name}: RELRO does not start in a PT_LOAD segment")
            protected_start = start // PAGE_SIZE * PAGE_SIZE
            protected_end = (end + PAGE_SIZE - 1) // PAGE_SIZE * PAGE_SIZE
            for first, last, flags in loads:
                if not flags & PF_W:
                    continue
                require(max(first, protected_start) >= min(last, start),
                        f"{name}: unaligned RELRO start shares a 16 KB page with writable data")
                require(max(first, end) >= min(last, protected_end),
                        f"{name}: unaligned RELRO end shares a 16 KB page with writable data")
    return len(loads), elf_class == 2


def verify_bundle(bundle, config):
    verify_packaging(config)
    count, count64 = 0, 0
    with zipfile.ZipFile(bundle) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), "Bundle contains duplicate ZIP entries")
        for name in sorted(names):
            parts = name.split("/")
            if len(parts) != 4 or parts[1] != "lib" or not name.endswith(".so"):
                continue
            loads, is64 = verify_elf(archive.read(name), name, parts[2])
            count += 1
            count64 += int(is64)
            label = "16 KB ELF/RELRO verified" if is64 else "32-bit ELF structurally verified"
            print(f"{label}: {name} ({loads} load segments)")
    require(count > 0 and count64 > 0, "Expected packaged 64-bit native libraries; inspect the dependency change")
    print(f"Verified {count64} 64-bit libraries for 16 KB, {count - count64} 32-bit libraries, and bundle packaging")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("configuration", type=Path)
    args = parser.parse_args()
    try:
        verify_bundle(args.bundle, json.loads(args.configuration.read_text()))
    except (ValueError, OSError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Native-library verification failed: {error}\n")


if __name__ == "__main__":
    main()
