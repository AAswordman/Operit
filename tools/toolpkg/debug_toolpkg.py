#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile
from dataclasses import dataclass
from pathlib import Path

DEBUG_APP_PACKAGE = "com.ai.assistance.operit.debug"
RELEASE_APP_PACKAGE = "com.ai.assistance.operit"
SUPPORTED_APP_PACKAGES = (DEBUG_APP_PACKAGE, RELEASE_APP_PACKAGE)
APP_PACKAGE_ENV = "OPERIT_APP_PACKAGE"
MANIFEST_FILENAMES = ("manifest.json", "manifest.hjson")
DEFAULT_LOG_WAIT_SECONDS = 6
LOGCAT_TAGS = (
    "ToolPkgDebugInstallReceiver:*",
    "ToolPkg:*",
    "PackageManager:*",
)
TOOLPKG_ID_PATTERN = re.compile(
    r'^\s*["\']?toolpkg_id["\']?\s*:\s*["\']([^"\']+)["\']',
    re.MULTILINE,
)
MAIN_PATTERN = re.compile(
    r'^\s*["\']?main["\']?\s*:\s*["\']([^"\']+)["\']',
    re.MULTILINE,
)
SKIP_DIR_NAMES = {".git", "__pycache__", ".backup", "backup", ".operit"}
SKIP_FILE_NAMES = {".DS_Store", "Thumbs.db"}


@dataclass(frozen=True)
class ToolPkgSource:
    kind: str  # folder | archive
    source_path: Path
    package_id: str
    main_entry: str


class ToolPkgDebugError(RuntimeError):
    pass


def run_command(
    command: list[str],
    *,
    capture_output: bool = False,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    completed = subprocess.run(
        command,
        text=True,
        capture_output=capture_output,
    )
    if check and completed.returncode != 0:
        output = f"{completed.stdout or ''}{completed.stderr or ''}".strip()
        raise ToolPkgDebugError(
            f"Command failed ({completed.returncode}): {' '.join(command)}\n"
            f"{output}"
        )
    return completed


def parse_manifest_text(text: str, manifest_path: Path) -> tuple[str, str]:
    package_id = ""
    main_entry = ""

    try:
        parsed = json.loads(text)
    except json.JSONDecodeError:
        parsed = None

    if isinstance(parsed, dict):
        package_id = str(parsed.get("toolpkg_id", "")).strip()
        main_entry = str(parsed.get("main", "")).strip()

    if not package_id:
        match = TOOLPKG_ID_PATTERN.search(text)
        if match:
            package_id = match.group(1).strip()

    if not main_entry:
        match = MAIN_PATTERN.search(text)
        if match:
            main_entry = match.group(1).strip()

    if not package_id:
        raise ToolPkgDebugError(f"manifest.toolpkg_id is required: {manifest_path}")
    if not main_entry:
        raise ToolPkgDebugError(f"manifest.main is required: {manifest_path}")

    return package_id, main_entry.replace("\\", "/").lstrip("/")


def load_manifest_from_folder(folder: Path) -> tuple[Path, str, str]:
    for file_name in MANIFEST_FILENAMES:
        manifest_path = folder / file_name
        if manifest_path.is_file():
            text = manifest_path.read_text(encoding="utf-8")
            package_id, main_entry = parse_manifest_text(text, manifest_path)
            return manifest_path, package_id, main_entry
    raise ToolPkgDebugError(
        f"Missing manifest.json or manifest.hjson in folder: {folder}"
    )


def load_manifest_from_archive(archive_path: Path) -> tuple[str, str]:
    with zipfile.ZipFile(archive_path, "r") as archive:
        names = archive.namelist()
        manifest_name = next(
            (name for name in names if Path(name).name in MANIFEST_FILENAMES),
            None,
        )
        if manifest_name is None:
            raise ToolPkgDebugError(
                f"Archive does not contain manifest.json or manifest.hjson: {archive_path}"
            )
        text = archive.read(manifest_name).decode("utf-8")
        package_id, main_entry = parse_manifest_text(text, archive_path / manifest_name)
        if main_entry not in names:
            raise ToolPkgDebugError(
                f"manifest.main entry is missing in archive: {archive_path} -> {main_entry}"
            )
        return package_id, main_entry


def resolve_source(raw_path: str) -> ToolPkgSource:
    source_path = Path(raw_path).expanduser().resolve()
    if not source_path.exists():
        raise ToolPkgDebugError(f"Path does not exist: {source_path}")

    if source_path.is_file() and source_path.name in MANIFEST_FILENAMES:
        source_path = source_path.parent

    if source_path.is_dir():
        manifest_path, package_id, main_entry = load_manifest_from_folder(source_path)
        main_file = source_path / main_entry
        if not main_file.is_file():
            raise ToolPkgDebugError(
                f"manifest.main file does not exist: {manifest_path} -> {main_entry}"
            )
        return ToolPkgSource(
            kind="folder",
            source_path=source_path,
            package_id=package_id,
            main_entry=main_entry,
        )

    if source_path.is_file() and source_path.suffix.lower() == ".toolpkg":
        package_id, main_entry = load_manifest_from_archive(source_path)
        return ToolPkgSource(
            kind="archive",
            source_path=source_path,
            package_id=package_id,
            main_entry=main_entry,
        )

    raise ToolPkgDebugError(
        "Source must be a ToolPkg folder, manifest.json/manifest.hjson, or .toolpkg file"
    )


def iter_folder_files(source_dir: Path) -> list[Path]:
    files: list[Path] = []
    for candidate in sorted(source_dir.rglob("*")):
        if not candidate.is_file():
            continue
        relative_parts = candidate.relative_to(source_dir).parts
        if any(part in SKIP_DIR_NAMES for part in relative_parts[:-1]):
            continue
        if candidate.name in SKIP_FILE_NAMES:
            continue
        files.append(candidate)
    return files


def safe_remote_file_name(package_id: str) -> str:
    safe_name = re.sub(r"[^A-Za-z0-9._-]", "_", package_id).strip("._")
    if not safe_name:
        safe_name = "debug_toolpkg"
    return f"{safe_name}.toolpkg"


def build_temp_archive(source: ToolPkgSource) -> Path:
    archive_name = safe_remote_file_name(source.package_id)
    temp_dir = tempfile.mkdtemp(prefix="operit-toolpkg-debug-")
    archive_path = Path(temp_dir) / archive_name

    with zipfile.ZipFile(archive_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for file_path in iter_folder_files(source.source_path):
            archive.write(file_path, file_path.relative_to(source.source_path).as_posix())

    return archive_path


def list_devices() -> list[str]:
    result = run_command(["adb", "devices"], capture_output=True)
    devices: list[str] = []
    for line in result.stdout.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
    return devices


def select_device(explicit_serial: str | None) -> str:
    print("Checking connected devices...")
    devices = list_devices()
    if not devices:
        raise ToolPkgDebugError("No authorized adb devices found")

    if explicit_serial:
        if explicit_serial not in devices:
            raise ToolPkgDebugError(
                f"Requested device is not available: {explicit_serial}\n"
                f"Available devices: {', '.join(devices)}"
            )
        return explicit_serial

    if len(devices) == 1:
        print(f"Using the only connected device: {devices[0]}")
        return devices[0]

    print("Multiple devices detected:")
    for index, device in enumerate(devices, start=1):
        print(f"  {index}. {device}")

    while True:
        choice = input(f"Select device (1-{len(devices)}): ").strip()
        if not choice.isdigit():
            print("Invalid input. Numbers only.")
            continue
        value = int(choice)
        if value < 1 or value > len(devices):
            print("Selection out of range.")
            continue
        return devices[value - 1]


def adb_command(device_serial: str, *args: str, capture_output: bool = False) -> subprocess.CompletedProcess[str]:
    command = ["adb", "-s", device_serial, *args]
    return run_command(command, capture_output=capture_output)


def _is_app_package_installed(device_serial: str, app_package: str) -> bool:
    result = adb_command(
        device_serial,
        "shell",
        "pm",
        "list",
        "packages",
        app_package,
        capture_output=True,
    )
    expected_line = f"package:{app_package}"
    return any(line.strip() == expected_line for line in result.stdout.splitlines())


def resolve_app_package(
    device_serial: str,
    requested_package: str | None,
) -> str:
    # Debug and Release APKs expose the same receivers under different applicationIds;
    # resolving before pushing prevents an archive or broadcast from reaching the other APK.
    configured_package = (requested_package or os.environ.get(APP_PACKAGE_ENV, "")).strip()
    if configured_package:
        if configured_package not in SUPPORTED_APP_PACKAGES:
            supported = ", ".join(SUPPORTED_APP_PACKAGES)
            raise ToolPkgDebugError(
                f"Unsupported application package: {configured_package}. "
                f"Expected one of: {supported}"
            )
        if not _is_app_package_installed(device_serial, configured_package):
            raise ToolPkgDebugError(
                f"Application package is not installed on {device_serial}: {configured_package}"
            )
        print(f"Using Operit application package: {configured_package}")
        return configured_package

    for app_package in SUPPORTED_APP_PACKAGES:
        if _is_app_package_installed(device_serial, app_package):
            print(f"Using Operit application package: {app_package}")
            return app_package

    supported = ", ".join(SUPPORTED_APP_PACKAGES)
    raise ToolPkgDebugError(
        f"Neither supported Operit application package is installed on {device_serial}: {supported}"
    )


def install_toolpkg(
    *,
    source: ToolPkgSource,
    archive_path: Path,
    device_serial: str,
    app_package: str,
    reset_subpackage_states: bool,
    log_wait_seconds: int,
) -> None:
    action_debug_install_toolpkg = f"{app_package}.DEBUG_INSTALL_TOOLPKG"
    receiver_component = (
        f"{app_package}/.core.tools.packTool.ToolPkgDebugInstallReceiver"
    )
    remote_packages_dir = f"/sdcard/Android/data/{app_package}/files/packages"
    remote_file = f"{remote_packages_dir}/{safe_remote_file_name(source.package_id)}"

    print(f"Package ID: {source.package_id}")
    print(f"Main entry: {source.main_entry}")
    print(f"Device: {device_serial}")
    print(f"Remote archive: {remote_file}")

    adb_command(device_serial, "shell", "mkdir", "-p", remote_packages_dir)

    print(f"Pushing [{archive_path}] to device...")
    adb_command(device_serial, "push", str(archive_path), remote_file)

    adb_command(device_serial, "logcat", "-c")

    print("Sending debug install broadcast...")
    broadcast = adb_command(
        device_serial,
        "shell",
        "am",
        "broadcast",
        "-a",
        action_debug_install_toolpkg,
        "-n",
        receiver_component,
        "--include-stopped-packages",
        "--es",
        "package_name",
        source.package_id,
        "--es",
        "file_path",
        remote_file,
        "--ez",
        "reset_subpackage_states",
        "true" if reset_subpackage_states else "false",
        capture_output=True,
    )
    if broadcast.stdout.strip():
        print(broadcast.stdout.strip())

    print(f"Waiting {log_wait_seconds}s for install and logs...")
    time.sleep(log_wait_seconds)

    print("Capturing logcat output and exiting...")
    logcat = adb_command(
        device_serial,
        "logcat",
        "-d",
        "-s",
        *LOGCAT_TAGS,
        capture_output=True,
    )
    output = logcat.stdout.strip()
    if output:
        print(output)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Package a ToolPkg folder or use an existing .toolpkg archive, push it to the "
            "device external packages directory, and trigger Operit's debug install flow."
        )
    )
    parser.add_argument(
        "source",
        help="ToolPkg folder path, manifest path, or existing .toolpkg archive",
    )
    parser.add_argument(
        "--device",
        dest="device_serial",
        default=None,
        help="adb device serial. If omitted, auto-selects when only one device is connected.",
    )
    parser.add_argument(
        "--app-package",
        dest="app_package",
        default=None,
        help=(
            "Operit applicationId to debug. Supports com.ai.assistance.operit.debug and "
            "com.ai.assistance.operit; defaults to OPERIT_APP_PACKAGE or automatic Debug-first detection."
        ),
    )
    parser.add_argument(
        "--no-reset-subpackage-states",
        action="store_true",
        help="Keep saved subpackage enable states instead of resetting them from manifest defaults.",
    )
    parser.add_argument(
        "--log-wait-seconds",
        type=int,
        default=int(os.environ.get("OPERIT_LOG_WAIT_SECONDS", DEFAULT_LOG_WAIT_SECONDS)),
        help=(
            "Seconds to wait after sending the install broadcast before dumping logcat. "
            "Defaults to OPERIT_LOG_WAIT_SECONDS or 6."
        ),
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    try:
        run_command(["adb", "version"], capture_output=True)
    except ToolPkgDebugError as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1

    archive_path: Path | None = None
    temp_dir: Path | None = None

    try:
        source = resolve_source(args.source)
        device_serial = select_device(args.device_serial)
        app_package = resolve_app_package(device_serial, args.app_package)

        if source.kind == "folder":
            archive_path = build_temp_archive(source)
            temp_dir = archive_path.parent
        else:
            archive_path = source.source_path

        install_toolpkg(
            source=source,
            archive_path=archive_path,
            device_serial=device_serial,
            app_package=app_package,
            reset_subpackage_states=not args.no_reset_subpackage_states,
            log_wait_seconds=max(0, args.log_wait_seconds),
        )
        return 0
    except ToolPkgDebugError as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1
    finally:
        if temp_dir is not None and temp_dir.exists():
            shutil.rmtree(temp_dir, ignore_errors=True)


if __name__ == "__main__":
    raise SystemExit(main())
