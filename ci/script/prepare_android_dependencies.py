#!/usr/bin/env python3
"""Validate and unpack the fixed Android CI dependency archives."""

from __future__ import annotations

import argparse
import shutil
import stat
import zipfile
from pathlib import Path, PurePosixPath


PROFILE_ARCHIVES = {
    "jvm": ("libs.zip",),
    "full": ("libs.zip", "models.zip", "subpack.zip", "jniLibs.zip"),
}
ARCHIVE_ROOTS = {
    "libs.zip": "app/libs",
    "models.zip": "app/src/main/assets/models",
    "subpack.zip": "app/src/main/assets/subpack",
    "jniLibs.zip": "app/src/main/jniLibs",
}
MAX_ARCHIVE_MEMBERS = 100_000
MAX_MEMBER_BYTES = 4 * 1024**3
MAX_ARCHIVE_BYTES = 8 * 1024**3
MAX_COMPRESSION_RATIO = 1_000


def validate_member(info: zipfile.ZipInfo, allowed_root: str) -> None:
    member = PurePosixPath(info.filename)
    if member.is_absolute() or ".." in member.parts:
        raise ValueError(f"archive member escapes the repository: {info.filename}")
    root = PurePosixPath(allowed_root)
    is_output = member == root or root in member.parents
    is_root_ancestor = (
        info.is_dir() and member != PurePosixPath(".") and member in root.parents
    )
    if not is_output and not is_root_ancestor:
        raise ValueError(
            f"archive member is outside the allowed root {allowed_root}: {info.filename}"
        )
    file_type = stat.S_IFMT(info.external_attr >> 16)
    allowed_types = {0, stat.S_IFDIR} if info.is_dir() else {0, stat.S_IFREG}
    if file_type not in allowed_types:
        raise ValueError(f"archive member has an unsafe file type: {info.filename}")
    if info.flag_bits & 0x1:
        raise ValueError(f"archive member is encrypted: {info.filename}")


def validate_archive(stream: zipfile.ZipFile, allowed_root: str) -> int:
    infos = stream.infolist()
    if len(infos) > MAX_ARCHIVE_MEMBERS:
        raise ValueError(f"archive contains too many members: {len(infos)}")

    seen: set[PurePosixPath] = set()
    regular_members = 0
    total_size = 0
    for info in infos:
        validate_member(info, allowed_root)
        member = PurePosixPath(info.filename)
        if member in seen:
            raise ValueError(f"archive contains a duplicate member: {info.filename}")
        seen.add(member)

        if info.file_size > MAX_MEMBER_BYTES:
            raise ValueError(f"archive member is too large: {info.filename}")
        total_size += info.file_size
        if total_size > MAX_ARCHIVE_BYTES:
            raise ValueError("archive expands beyond the allowed size")
        if info.file_size > 0 and (
            info.compress_size == 0
            or info.file_size > info.compress_size * MAX_COMPRESSION_RATIO
        ):
            raise ValueError(
                f"archive member exceeds the compression ratio limit: {info.filename}"
            )

        if not info.is_dir() and member.name != ".keep":
            regular_members += 1

    if regular_members == 0:
        raise ValueError("archive has no usable files")
    return regular_members


def ensure_no_symlink_components(repository: Path, destination: Path) -> None:
    relative = destination.relative_to(repository)
    current = repository
    for part in relative.parts:
        current /= part
        if current.is_symlink():
            raise ValueError(f"archive destination contains a symbolic link: {current}")


def reset_output_root(repository: Path, allowed_root: str) -> None:
    output_root = repository.joinpath(*PurePosixPath(allowed_root).parts)
    ensure_no_symlink_components(repository, output_root)
    if output_root.exists():
        if not output_root.is_dir():
            raise ValueError(f"Android dependency output root is not a directory: {allowed_root}")
        shutil.rmtree(output_root)
    output_root.mkdir(parents=True)
    ensure_no_symlink_components(repository, output_root)


def extract_member(
    stream: zipfile.ZipFile,
    info: zipfile.ZipInfo,
    repository: Path,
) -> Path | None:
    member = PurePosixPath(info.filename)
    destination = repository.joinpath(*member.parts)
    ensure_no_symlink_components(repository, destination)

    if info.is_dir():
        destination.mkdir(parents=True, exist_ok=True)
        ensure_no_symlink_components(repository, destination)
        return None

    destination.parent.mkdir(parents=True, exist_ok=True)
    ensure_no_symlink_components(repository, destination)
    with stream.open(info) as source, destination.open("wb") as target:
        written = 0
        while chunk := source.read(1024 * 1024):
            written += len(chunk)
            if written > info.file_size or written > MAX_MEMBER_BYTES:
                raise ValueError(f"archive member exceeded its declared size: {info.filename}")
            target.write(chunk)
    if written != info.file_size:
        raise ValueError(f"archive member size does not match its metadata: {info.filename}")
    return destination


def extract_archive(archive: Path, repository: Path) -> set[Path]:
    if not archive.is_file():
        raise FileNotFoundError(f"required Android dependency archive is missing: {archive}")
    repository = repository.resolve()
    with zipfile.ZipFile(archive) as stream:
        allowed_root = ARCHIVE_ROOTS[archive.name]
        try:
            validate_archive(stream, allowed_root)
        except ValueError as error:
            raise ValueError(f"invalid Android dependency archive {archive}: {error}") from error
        reset_output_root(repository, allowed_root)
        outputs: set[Path] = set()
        for info in stream.infolist():
            output = extract_member(stream, info, repository)
            if output is not None:
                outputs.add(output)
    return outputs


def verify_outputs(profile: str, repository: Path, extracted_files: set[Path]) -> None:
    library_root = repository / "app" / "libs"
    libraries = [
        path
        for path in extracted_files
        if path.parent == library_root
        and path.suffix in {".aar", ".jar"}
        and path.is_file()
        and not path.is_symlink()
    ]
    if not libraries:
        raise ValueError("libs.zip did not provide an AAR or JAR in app/libs")
    if profile == "full":
        for relative_path in (
            "app/src/main/assets/models",
            "app/src/main/assets/subpack",
            "app/src/main/jniLibs",
        ):
            output_root = repository / relative_path
            if not output_root.is_dir():
                raise ValueError(f"Android dependency output is missing: {relative_path}")
            files = [
                path
                for path in extracted_files
                if path.is_relative_to(output_root)
                and path.is_file()
                and not path.is_symlink()
                and path.name != ".keep"
            ]
            if not files:
                raise ValueError(f"Android dependency output contains no usable files: {relative_path}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--profile", choices=tuple(PROFILE_ARCHIVES), required=True)
    parser.add_argument("--archives", type=Path, required=True)
    parser.add_argument("--repository", type=Path, required=True)
    args = parser.parse_args()

    repository = args.repository.resolve()
    extracted_files: set[Path] = set()
    for archive_name in PROFILE_ARCHIVES[args.profile]:
        extracted_files.update(extract_archive(args.archives / archive_name, repository))
    verify_outputs(args.profile, repository, extracted_files)
    print(f"Prepared Android {args.profile} dependencies from {args.archives}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
