#!/usr/bin/env python3
"""Audit a source-built FFmpegKit AAR and emit a deterministic text report."""

from __future__ import annotations

import argparse
import hashlib
import re
import subprocess
import tempfile
import zipfile
from pathlib import Path, PurePosixPath


NATIVE_PREFIX = PurePosixPath("jni")
EXPECTED_ABI = "arm64-v8a"
EXPECTED_FFMPEG_LIBRARIES = {
    "libavcodec.so",
    "libavdevice.so",
    "libavfilter.so",
    "libavformat.so",
    "libavutil.so",
    "libffmpegkit.so",
    "libswresample.so",
    "libswscale.so",
}


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def validate_member_path(name: str) -> PurePosixPath:
    path = PurePosixPath(name)
    if path.is_absolute() or ".." in path.parts:
        raise ValueError(f"unsafe AAR member path: {name}")
    return path


def readelf_metadata(readelf: str, library: Path) -> tuple[str, tuple[str, ...]]:
    header = subprocess.run(
        [readelf, "-h", str(library)],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    dynamic = subprocess.run(
        [readelf, "-d", str(library)],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    machine_match = re.search(r"^\s*Machine:\s*(.+)$", header, re.MULTILINE)
    if machine_match is None:
        raise ValueError(f"unable to read ELF machine: {library}")
    needed = tuple(sorted(set(re.findall(r"Shared library: \[(.+?)\]", dynamic))))
    return machine_match.group(1).strip(), needed


def audit(aar: Path, readelf: str) -> list[str]:
    if not aar.is_file() or aar.stat().st_size == 0:
        raise ValueError(f"AAR is missing or empty: {aar}")

    lines = [
        f"aar.path={aar.name}",
        f"aar.bytes={aar.stat().st_size}",
        f"aar.sha256={hashlib.sha256(aar.read_bytes()).hexdigest()}",
    ]
    seen: set[PurePosixPath] = set()
    native_members: dict[str, bytes] = {}
    with zipfile.ZipFile(aar) as archive:
        for info in sorted(archive.infolist(), key=lambda item: item.filename):
            path = validate_member_path(info.filename)
            if path in seen:
                raise ValueError(f"duplicate AAR member: {info.filename}")
            seen.add(path)
            if info.is_dir():
                continue
            data = archive.read(info)
            lines.append(
                f"member={info.filename}|bytes={len(data)}|sha256={sha256_bytes(data)}"
            )
            if len(path.parts) == 3 and path.parts[0] == str(NATIVE_PREFIX):
                abi, filename = path.parts[1], path.parts[2]
                if filename.endswith(".so"):
                    if abi != EXPECTED_ABI:
                        raise ValueError(f"unexpected FFmpeg AAR ABI: {abi}/{filename}")
                    native_members[filename] = data

    missing = sorted(EXPECTED_FFMPEG_LIBRARIES - set(native_members))
    if missing:
        raise ValueError(f"AAR is missing expected FFmpeg libraries: {missing}")

    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        for filename, data in sorted(native_members.items()):
            library = root / filename
            library.write_bytes(data)
            machine, needed = readelf_metadata(readelf, library)
            if "AArch64" not in machine:
                raise ValueError(f"unexpected ELF machine for {filename}: {machine}")
            lines.append(
                f"elf={filename}|machine={machine}|needed={','.join(needed)}"
            )
    return lines


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--aar", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--readelf", default="readelf")
    args = parser.parse_args()

    report_lines = audit(args.aar.resolve(), args.readelf)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text("\n".join(report_lines) + "\n")
    print(f"Wrote FFmpeg AAR audit report: {args.report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())