from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path

MANIFEST_FILENAMES = ("manifest.hjson", "manifest.json")
SYNCABLE_SUFFIXES = {".js", ".toolpkg"}
SYNC_MODES = ("normal", "test")
APP_PACKAGE = "com.ai.assistance.operit"
ACTION_DEBUG_INSTALL_TOOLPKG = "com.ai.assistance.operit.DEBUG_INSTALL_TOOLPKG"
ACTION_DEBUG_REFRESH_PACKAGES = "com.ai.assistance.operit.DEBUG_REFRESH_PACKAGES"
RECEIVER_COMPONENT_TOOLPKG = (
    "com.ai.assistance.operit/.core.tools.packTool.ToolPkgDebugInstallReceiver"
)
RECEIVER_COMPONENT_REFRESH = (
    "com.ai.assistance.operit/.core.tools.packTool.PackageDebugRefreshReceiver"
)
REMOTE_PACKAGES_DIR = f"/sdcard/Android/data/{APP_PACKAGE}/files/packages"
HOT_RELOAD_STATE_FILE = ".sync_example_packages_hot_reload_state.json"
LOCAL_SYNC_STATE_FILE = ".sync_example_packages_local_state.json"


@dataclass(frozen=True)
class SyncPlanItem:
    mode: str  # copy | pack
    source: Path
    destination_name: str


def _run_checked_command(command: list[str], cwd: Path, *, dry_run: bool) -> None:
    command_text = subprocess.list2cmdline(command)
    if dry_run:
        print(f"DRY-RUN-CMD: (cd {cwd}) {command_text}")
        return

    print(f"RUN-CMD: (cd {cwd}) {command_text}")
    completed = subprocess.run(command, cwd=str(cwd))
    if completed.returncode != 0:
        raise RuntimeError(f"Command failed with exit code {completed.returncode}: {command_text}")


def _load_local_sync_state(path: Path) -> dict[str, dict[str, str]]:
    if not path.is_file():
        return {"prebuild": {}, "outputs": {}}

    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {"prebuild": {}, "outputs": {}}

    if not isinstance(data, dict):
        return {"prebuild": {}, "outputs": {}}

    normalized: dict[str, dict[str, str]] = {"prebuild": {}, "outputs": {}}
    for section in ("prebuild", "outputs"):
        entry = data.get(section, {})
        if not isinstance(entry, dict):
            continue
        normalized[section] = {
            str(name): str(signature)
            for name, signature in entry.items()
            if isinstance(name, str) and isinstance(signature, str)
        }
    return normalized


def _save_local_sync_state(path: Path, state: dict[str, dict[str, str]]) -> None:
    path.write_text(
        json.dumps(state, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def _iter_signature_files(paths: list[Path]) -> list[Path]:
    seen: set[Path] = set()
    files: list[Path] = []
    for path in paths:
        if not path.is_file():
            continue
        if path in seen:
            continue
        seen.add(path)
        files.append(path)
    files.sort(key=lambda p: p.as_posix().lower())
    return files


def _compute_paths_signature(base_dir: Path, paths: list[Path]) -> str:
    digest = hashlib.sha256()
    for file_path in _iter_signature_files(paths):
        relative_path = file_path.relative_to(base_dir).as_posix()
        digest.update(relative_path.encode("utf-8"))
        digest.update(b"\0")
        with file_path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
        digest.update(b"\0")
    return digest.hexdigest()


def _collect_root_prebuild_inputs(examples_dir: Path) -> list[Path]:
    paths: list[Path] = []
    tsconfig = examples_dir / "tsconfig.json"
    if tsconfig.is_file():
        paths.append(tsconfig)

    for child in examples_dir.iterdir():
        if child.is_file() and child.suffix.lower() in {".ts", ".d.ts"}:
            paths.append(child)

    types_dir = examples_dir / "types"
    if types_dir.is_dir():
        for file_path in types_dir.rglob("*"):
            if file_path.is_file() and file_path.suffix.lower() in {".ts", ".d.ts"}:
                paths.append(file_path)

    return paths


def _collect_child_prebuild_inputs(examples_dir: Path, child_dir: Path) -> list[Path]:
    paths: list[Path] = []
    tsconfig = child_dir / "tsconfig.json"
    if tsconfig.is_file():
        paths.append(tsconfig)

    for file_path in child_dir.rglob("*"):
        if "node_modules" in file_path.parts:
            continue
        if file_path.is_file() and file_path.suffix.lower() in {".ts", ".d.ts"}:
            paths.append(file_path)

    types_dir = examples_dir / "types"
    if types_dir.is_dir():
        for file_path in types_dir.rglob("*"):
            if file_path.is_file() and file_path.suffix.lower() in {".ts", ".d.ts"}:
                paths.append(file_path)

    package_json = child_dir / "package.json"
    if package_json.is_file():
        paths.append(package_json)
        build_script = child_dir / "build.js"
        if build_script.is_file():
            paths.append(build_script)

    return paths


def _needs_root_prebuild(examples_dir: Path, plans: list[SyncPlanItem]) -> bool:
    for plan in plans:
        if plan.mode != "copy":
            continue
        if plan.source.parent != examples_dir:
            continue
        sibling_ts = plan.source.with_suffix(".ts")
        sibling_dts = plan.source.with_suffix(".d.ts")
        if sibling_ts.is_file() or sibling_dts.is_file():
            return True
    return False


def _prebuild_examples(
    repo_root: Path,
    examples_dir: Path,
    plans: list[SyncPlanItem],
    *,
    dry_run: bool,
    local_state: dict[str, dict[str, str]],
) -> None:
    root_tsconfig = examples_dir / "tsconfig.json"
    if not root_tsconfig.is_file():
        raise FileNotFoundError(f"Missing tsconfig.json: {root_tsconfig}")

    prebuild_state = local_state.setdefault("prebuild", {})

    if _needs_root_prebuild(examples_dir, plans):
        root_signature = _compute_paths_signature(repo_root, _collect_root_prebuild_inputs(examples_dir))
        if prebuild_state.get("root") == root_signature:
            print(f"SKIP-PREBUILD(ROOT): {examples_dir}")
        else:
            _run_checked_command(
                ["pnpm", "exec", "tsc", "-p", str(root_tsconfig)],
                cwd=repo_root,
                dry_run=dry_run,
            )
            if not dry_run:
                prebuild_state["root"] = root_signature
    else:
        print("SKIP-PREBUILD(ROOT): no planned root TS outputs")

    planned_child_dirs = sorted(
        {
            plan.source
            for plan in plans
            if plan.source.is_dir() and plan.source.parent == examples_dir and plan.source.name != "types"
        },
        key=lambda p: p.name.lower(),
    )

    for child_dir in planned_child_dirs:
        tsconfig = child_dir / "tsconfig.json"
        if not tsconfig.is_file():
            raise FileNotFoundError(f"Missing tsconfig.json: {tsconfig}")

        child_signature = _compute_paths_signature(
            repo_root,
            _collect_child_prebuild_inputs(examples_dir, child_dir),
        )
        child_key = f"child:{child_dir.name}"
        should_run_tsc = prebuild_state.get(child_key) != child_signature

        if should_run_tsc:
            _run_checked_command(
                ["pnpm", "exec", "tsc", "-p", str(tsconfig)],
                cwd=repo_root,
                dry_run=dry_run,
            )
            if not dry_run:
                prebuild_state[child_key] = child_signature
        else:
            print(f"SKIP-PREBUILD: {child_dir}")

        manifest = child_dir / "manifest.json"
        if manifest.is_file():
            print(f"SKIP-BUILD(MANIFEST): {child_dir}")
            continue

        package_json = child_dir / "package.json"
        build_key = f"child-build:{child_dir.name}"
        should_run_build = should_run_tsc or prebuild_state.get(build_key) != child_signature
        if package_json.is_file() and should_run_build:
            _run_checked_command(
                ["pnpm", "build"],
                cwd=child_dir,
                dry_run=dry_run,
            )
            if not dry_run:
                prebuild_state[build_key] = child_signature
        elif package_json.is_file():
            print(f"SKIP-BUILD: {child_dir}")


def _compute_plan_signature(repo_root: Path, plan: SyncPlanItem) -> str:
    if plan.mode == "copy":
        return _compute_paths_signature(repo_root, [plan.source])

    return _compute_paths_signature(repo_root, _iter_files_for_pack(repo_root, plan.source))


def _prune_local_sync_state_outputs(
    output_state: dict[str, str],
    planned_destination_names: set[str],
) -> None:
    for destination_name in list(output_state.keys()):
        if destination_name not in planned_destination_names:
            output_state.pop(destination_name, None)


def _prune_local_sync_state_prebuild(
    prebuild_state: dict[str, str],
    planned_child_names: set[str],
    keep_root: bool,
) -> None:
    for key in list(prebuild_state.keys()):
        if key == "root":
            if not keep_root:
                prebuild_state.pop(key, None)
            continue
        if not key.startswith("child"):
            continue
        _, _, child_name = key.partition(":")
        if child_name not in planned_child_names:
            prebuild_state.pop(key, None)


def _prebuild_planned_child_names(examples_dir: Path, plans: list[SyncPlanItem]) -> set[str]:
    return {
        plan.source.name
        for plan in plans
        if plan.source.is_dir() and plan.source.parent == examples_dir and plan.source.name != "types"
    }


def _read_whitelist_file(path: Path) -> list[str]:
    if not path.exists():
        raise FileNotFoundError(str(path))

    if path.suffix.lower() == ".json":
        data = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(data, list) or not all(isinstance(x, str) for x in data):
            raise ValueError(f"Whitelist json must be a string array: {path}")
        return [x.strip() for x in data if x.strip()]

    items: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        s = line.strip()
        if not s:
            continue
        if s.startswith("#"):
            continue
        items.append(s)
    return items


def _normalize_item(item: str) -> str:
    return item.strip().replace("\\", "/").strip().strip("/")


def _default_whitelist(packages_dir: Path) -> list[str]:
    if not packages_dir.exists():
        return []

    names: list[str] = []
    for p in packages_dir.iterdir():
        if not p.is_file():
            continue
        if p.suffix.lower() not in SYNCABLE_SUFFIXES:
            continue
        names.append(p.name)
    return sorted(names)


def _collect_all_example_items(examples_dir: Path) -> list[str]:
    if not examples_dir.exists():
        return []

    items: list[str] = []

    for child in sorted(examples_dir.iterdir(), key=lambda p: p.name.lower()):
        if child.name == "types":
            continue

        if child.is_file() and child.suffix.lower() in SYNCABLE_SUFFIXES:
            items.append(child.name)
            continue

        if child.is_dir() and _find_manifest_file(child):
            items.append(child.name)

    return items


def _find_manifest_file(folder: Path) -> Path | None:
    for file_name in MANIFEST_FILENAMES:
        manifest = folder / file_name
        if manifest.is_file():
            return manifest
    return None


def _resolve_existing_path(path: Path) -> SyncPlanItem | None:
    if path.is_file() and path.suffix.lower() in SYNCABLE_SUFFIXES:
        return SyncPlanItem(mode="copy", source=path, destination_name=path.name)

    if path.is_dir() and _find_manifest_file(path):
        return SyncPlanItem(mode="pack", source=path, destination_name=f"{path.name}.toolpkg")

    return None


def _resolve_plan_item(examples_dir: Path, item: str) -> SyncPlanItem | None:
    normalized = _normalize_item(item)
    if not normalized:
        return None

    stem = normalized
    lower_stem = stem.lower()
    if lower_stem.endswith(".js"):
        stem = stem[:-3]
    elif lower_stem.endswith(".toolpkg"):
        stem = stem[:-8]

    stem = stem.rstrip("/")

    # Prefer manifest folder over flat .js/.toolpkg with the same stem.
    if stem:
        folder_candidate = examples_dir / stem
        if folder_candidate.is_dir() and _find_manifest_file(folder_candidate):
            return SyncPlanItem(mode="pack", source=folder_candidate, destination_name=f"{folder_candidate.name}.toolpkg")

    direct_path = examples_dir / normalized
    direct_result = _resolve_existing_path(direct_path)
    if direct_result is not None:
        return direct_result

    if not stem:
        return None

    js_candidate = examples_dir / f"{stem}.js"
    if js_candidate.is_file():
        return SyncPlanItem(mode="copy", source=js_candidate, destination_name=js_candidate.name)

    toolpkg_candidate = examples_dir / f"{stem}.toolpkg"
    if toolpkg_candidate.is_file():
        return SyncPlanItem(mode="copy", source=toolpkg_candidate, destination_name=toolpkg_candidate.name)

    return None


def _resolve_plan_item_from_roots(source_roots: list[Path], item: str) -> SyncPlanItem | None:
    for source_root in source_roots:
        plan = _resolve_plan_item(source_root, item)
        if plan is not None:
            return plan
    return None


def _iter_files_for_pack(repo_root: Path, folder: Path) -> list[Path]:
    folder_rel = folder.relative_to(repo_root).as_posix()
    completed = subprocess.run(
        ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard", "--", folder_rel],
        cwd=str(repo_root),
        capture_output=True,
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError(f"git ls-files failed for: {folder_rel}")

    files: list[Path] = []
    seen: set[Path] = set()
    for raw_path in completed.stdout.split(b"\x00"):
        if not raw_path:
            continue
        repo_relative = Path(raw_path.decode("utf-8"))
        file_path = repo_root / repo_relative
        if not file_path.is_file():
            continue
        if file_path in seen:
            continue
        seen.add(file_path)
        files.append(file_path)

    files.sort(key=lambda x: x.relative_to(folder).as_posix())
    return files


def _pack_toolpkg_folder(repo_root: Path, source_folder: Path, destination_file: Path) -> None:
    if _find_manifest_file(source_folder) is None:
        raise ValueError(f"Missing manifest.hjson or manifest.json: {source_folder}")

    destination_file.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination_file, mode="w", compression=zipfile.ZIP_DEFLATED) as zf:
        for file_path in _iter_files_for_pack(repo_root, source_folder):
            arcname = file_path.relative_to(source_folder).as_posix()
            zf.write(file_path, arcname)


def _run_command(
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
        raise RuntimeError(
            f"Command failed ({completed.returncode}): {' '.join(command)}\n{output}"
        )
    return completed


def _list_adb_devices() -> list[str]:
    completed = _run_command(["adb", "devices"], capture_output=True)
    devices: list[str] = []
    for line in completed.stdout.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
    return devices


def _resolve_hot_reload_device(explicit_serial: str | None) -> str | None:
    try:
        _run_command(["adb", "version"], capture_output=True)
    except Exception:
        print("SKIP-HOT-RELOAD: adb not available in PATH")
        return None

    try:
        devices = _list_adb_devices()
    except Exception as exc:
        print(f"SKIP-HOT-RELOAD: failed to query adb devices: {exc}")
        return None

    if explicit_serial:
        if explicit_serial not in devices:
            print(
                "SKIP-HOT-RELOAD: requested device not available: "
                f"{explicit_serial} (available: {', '.join(devices) or 'none'})"
            )
            return None
        return explicit_serial

    if len(devices) == 1:
        return devices[0]

    if not devices:
        print("SKIP-HOT-RELOAD: no authorized adb devices found")
    else:
        print(
            "SKIP-HOT-RELOAD: multiple adb devices detected, use --device to choose one: "
            + ", ".join(devices)
        )
    return None


def _adb_command(
    device_serial: str,
    *args: str,
    capture_output: bool = False,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    return _run_command(
        ["adb", "-s", device_serial, *args],
        capture_output=capture_output,
        check=check,
    )


def _parse_toolpkg_manifest_text(text: str, manifest_path: Path) -> str:
    package_id = ""
    try:
        parsed = json.loads(text)
    except json.JSONDecodeError:
        parsed = None

    if isinstance(parsed, dict):
        package_id = str(parsed.get("toolpkg_id", "")).strip()

    if not package_id:
        for line in text.splitlines():
            if "toolpkg_id" not in line:
                continue
            _, _, value = line.partition(":")
            package_id = value.strip().strip('",\'')
            if package_id:
                break

    if not package_id:
        raise ValueError(f"manifest.toolpkg_id is required: {manifest_path}")
    return package_id


def _read_toolpkg_package_id(archive_path: Path) -> str:
    with zipfile.ZipFile(archive_path, "r") as archive:
        manifest_name = next(
            (name for name in archive.namelist() if Path(name).name in MANIFEST_FILENAMES),
            None,
        )
        if manifest_name is None:
            raise ValueError(
                f"Archive does not contain manifest.json or manifest.hjson: {archive_path}"
            )
        text = archive.read(manifest_name).decode("utf-8")
        return _parse_toolpkg_manifest_text(text, archive_path / manifest_name)


def _compute_file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _load_hot_reload_state(path: Path) -> dict[str, dict[str, str]]:
    if not path.is_file():
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {}
    if not isinstance(data, dict):
        return {}
    normalized: dict[str, dict[str, str]] = {}
    for device, entry in data.items():
        if not isinstance(device, str) or not isinstance(entry, dict):
            continue
        normalized[device] = {
            str(name): str(signature)
            for name, signature in entry.items()
            if isinstance(name, str) and isinstance(signature, str)
        }
    return normalized


def _save_hot_reload_state(path: Path, state: dict[str, dict[str, str]]) -> None:
    path.write_text(
        json.dumps(state, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def _broadcast_refresh_packages(device_serial: str) -> None:
    _adb_command(
        device_serial,
        "shell",
        "am",
        "broadcast",
        "-a",
        ACTION_DEBUG_REFRESH_PACKAGES,
        "-n",
        RECEIVER_COMPONENT_REFRESH,
        "--include-stopped-packages",
        "--ez",
        "reactivate_active_packages",
        "true",
        capture_output=True,
    )


def _broadcast_debug_install_toolpkg(
    device_serial: str,
    *,
    package_name: str,
    remote_file_path: str,
) -> None:
    _adb_command(
        device_serial,
        "shell",
        "am",
        "broadcast",
        "-a",
        ACTION_DEBUG_INSTALL_TOOLPKG,
        "-n",
        RECEIVER_COMPONENT_TOOLPKG,
        "--include-stopped-packages",
        "--es",
        "package_name",
        package_name,
        "--es",
        "file_path",
        remote_file_path,
        "--ez",
        "reset_subpackage_states",
        "false",
        capture_output=True,
    )


def _hot_reload_packages(
    *,
    state_dir: Path,
    packages_dir: Path,
    plans: list[SyncPlanItem],
    device_serial: str,
) -> None:
    state_file = state_dir / HOT_RELOAD_STATE_FILE
    state = _load_hot_reload_state(state_file)
    previous_signatures = state.get(device_serial, {})

    current_signatures: dict[str, str] = {}
    for plan in plans:
        destination_path = packages_dir / plan.destination_name
        if destination_path.is_file():
            current_signatures[plan.destination_name] = _compute_file_sha256(destination_path)

    changed_names = sorted(
        [
            destination_name
            for destination_name, signature in current_signatures.items()
            if previous_signatures.get(destination_name) != signature
        ]
    )
    deleted_names = sorted(
        [
            destination_name
            for destination_name in previous_signatures.keys()
            if destination_name not in current_signatures
        ]
    )

    if not changed_names and not deleted_names:
        print(f"SKIP-HOT-RELOAD: no package content changes for device {device_serial}")
        return

    print(f"HOT-RELOAD: device={device_serial}, changed={len(changed_names)}, deleted={len(deleted_names)}")
    _adb_command(device_serial, "shell", "mkdir", "-p", REMOTE_PACKAGES_DIR)

    for destination_name in deleted_names:
        remote_path = f"{REMOTE_PACKAGES_DIR}/{destination_name}"
        print(f"HOT-DELETE: {remote_path}")
        _adb_command(device_serial, "shell", "rm", "-f", remote_path)

    changed_toolpkgs: list[tuple[str, str]] = []
    for destination_name in changed_names:
        local_path = packages_dir / destination_name
        remote_path = f"{REMOTE_PACKAGES_DIR}/{destination_name}"
        print(f"HOT-PUSH: {local_path} -> {remote_path}")
        _adb_command(device_serial, "push", str(local_path), remote_path)
        if destination_name.lower().endswith(".toolpkg"):
            package_id = _read_toolpkg_package_id(local_path)
            changed_toolpkgs.append((package_id, remote_path))

    print("HOT-RELOAD: broadcasting package refresh")
    _broadcast_refresh_packages(device_serial)

    for package_id, remote_path in changed_toolpkgs:
        print(f"HOT-INSTALL: {package_id} -> {remote_path}")
        _broadcast_debug_install_toolpkg(
            device_serial,
            package_name=package_id,
            remote_file_path=remote_path,
        )

    state[device_serial] = current_signatures
    _save_hot_reload_state(state_file, state)


def _delete_unplanned_outputs(
    packages_dir: Path,
    planned_destination_names: set[str],
    *,
    dry_run: bool,
) -> int:
    if not packages_dir.exists():
        return 0

    deleted = 0
    for file_path in sorted(packages_dir.iterdir(), key=lambda p: p.name.lower()):
        if not file_path.is_file():
            continue
        if file_path.suffix.lower() not in SYNCABLE_SUFFIXES:
            continue
        if file_path.name in planned_destination_names:
            continue

        action = "DELETE" if not dry_run else "DRY-DELETE"
        print(f"{action}: {file_path}")
        if not dry_run:
            file_path.unlink(missing_ok=True)
        deleted += 1

    return deleted


def main() -> int:
    tools_dir = Path(__file__).resolve().parent
    repo_root = tools_dir.parent
    examples_dir = repo_root / "examples"
    packages_dir = repo_root / "app" / "src" / "main" / "assets" / "packages"
    default_whitelist_file = tools_dir / "packages_whitelist.txt"
    source_roots = [examples_dir, repo_root]

    parser = argparse.ArgumentParser(
        description=(
            "Sync packages from examples/ into app/src/main/assets/packages/. "
            "If an item maps to a folder that has manifest.hjson/manifest.json, it is packed as .toolpkg; "
            "otherwise .js/.toolpkg files are copied directly."
        )
    )
    parser.add_argument(
        "--mode",
        choices=SYNC_MODES,
        default="normal",
        help=(
            "Sync mode. "
            "'normal' syncs by whitelist. "
            "'test' syncs every syncable example from examples/. "
            "In both modes, only outputs not planned for this run are deleted."
        ),
    )
    parser.add_argument(
        "--whitelist",
        type=str,
        default=None,
        help=(
            "Path to whitelist file (.txt or .json). If omitted, will use tools/packages_whitelist.txt; "
            "otherwise uses current files in app/src/main/assets/packages/ as the whitelist."
        ),
    )
    parser.add_argument(
        "--include",
        action="append",
        default=[],
        help=(
            "Add an extra item to whitelist (e.g. github.js, github, windows_control, or a manifest folder path). "
            "Can be provided multiple times."
        ),
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show what would be copied/packed without writing files.",
    )
    parser.add_argument(
        "--device",
        type=str,
        default=None,
        help=(
            "adb device serial for post-sync hot reload. "
            "If omitted and exactly one device is connected, that device is used automatically."
        ),
    )
    parser.add_argument(
        "--no-hot-reload",
        action="store_true",
        help=(
            "Disable post-sync adb hot reload. "
            "By default the script will try to push changed packages to the device and refresh the app when adb is available."
        ),
    )
    parser.add_argument(
        "--delete-extra",
        action="store_true",
        help="Deprecated. Extra outputs not planned for this run are deleted automatically.",
    )

    args = parser.parse_args()

    if not examples_dir.exists():
        print(f"ERROR: examples dir not found: {examples_dir}", file=sys.stderr)
        return 2

    sync_mode = args.mode

    whitelist: list[str]
    if sync_mode == "test":
        whitelist = _collect_all_example_items(examples_dir)
    elif args.whitelist:
        whitelist = _read_whitelist_file(Path(args.whitelist))
    elif default_whitelist_file.exists():
        whitelist = _read_whitelist_file(default_whitelist_file)
    else:
        whitelist = _default_whitelist(packages_dir)

    whitelist.extend(args.include)

    normalized = [_normalize_item(x) for x in whitelist]
    normalized = [x for x in normalized if x]

    seen: set[str] = set()
    final_items: list[str] = []
    for x in normalized:
        if x in seen:
            continue
        seen.add(x)
        final_items.append(x)

    if not final_items:
        print("No whitelist items provided/found. Nothing to do.")
        print("- Provide --include <name> or create tools/packages_whitelist.txt.")
        return 0

    plans: list[SyncPlanItem] = []
    seen_dest_names: set[str] = set()
    missing = 0

    for item in final_items:
        plan = _resolve_plan_item_from_roots(source_roots, item)
        if plan is None:
            print(f"MISSING: {item}")
            missing += 1
            continue

        if plan.destination_name in seen_dest_names:
            print(f"SKIP-DUP: {item} -> {plan.destination_name}")
            continue

        seen_dest_names.add(plan.destination_name)
        plans.append(plan)

    local_state_file = tools_dir / LOCAL_SYNC_STATE_FILE
    local_state = _load_local_sync_state(local_state_file)
    output_state = local_state.setdefault("outputs", {})
    prebuild_state = local_state.setdefault("prebuild", {})

    try:
        _prebuild_examples(
            repo_root,
            examples_dir,
            plans,
            dry_run=args.dry_run,
            local_state=local_state,
        )
    except Exception as exc:  # pragma: no cover - runtime command failure path
        print(f"ERROR: prebuild step failed: {exc}", file=sys.stderr)
        return 3

    if not args.dry_run:
        packages_dir.mkdir(parents=True, exist_ok=True)

    copied = 0
    packed = 0
    deleted = 0

    deleted = _delete_unplanned_outputs(
        packages_dir,
        planned_destination_names=seen_dest_names,
        dry_run=args.dry_run,
    )
    _prune_local_sync_state_outputs(output_state, seen_dest_names)
    _prune_local_sync_state_prebuild(
        prebuild_state,
        _prebuild_planned_child_names(examples_dir, plans),
        keep_root=_needs_root_prebuild(examples_dir, plans),
    )

    for plan in plans:
        dest = packages_dir / plan.destination_name
        plan_signature = _compute_plan_signature(repo_root, plan)

        if plan.mode == "copy":
            if not args.dry_run and dest.is_file() and output_state.get(plan.destination_name) == plan_signature:
                print(f"SKIP-COPY: {plan.source} -> {dest}")
                continue
            action = "COPY" if not args.dry_run else "DRY-COPY"
            print(f"{action}: {plan.source} -> {dest}")
            if not args.dry_run:
                shutil.copy2(plan.source, dest)
                output_state[plan.destination_name] = plan_signature
                copied += 1
            continue

        if not args.dry_run and dest.is_file() and output_state.get(plan.destination_name) == plan_signature:
            print(f"SKIP-PACK: {plan.source} -> {dest}")
            continue
        action = "PACK" if not args.dry_run else "DRY-PACK"
        print(f"{action}: {plan.source} -> {dest}")
        if not args.dry_run:
            _pack_toolpkg_folder(repo_root, plan.source, dest)
            output_state[plan.destination_name] = plan_signature
            packed += 1

    print(
        "Done. "
        f"mode={sync_mode}, copied={copied}, packed={packed}, deleted={deleted}, missing={missing}, "
        f"whitelist={len(final_items)}, resolved={len(plans)}, dry_run={bool(args.dry_run)}"
    )

    if not args.dry_run and not args.no_hot_reload:
        device_serial = _resolve_hot_reload_device(args.device)
        if device_serial is not None:
            try:
                _hot_reload_packages(
                    state_dir=tools_dir,
                    packages_dir=packages_dir,
                    plans=plans,
                    device_serial=device_serial,
                )
            except Exception as exc:
                print(f"SKIP-HOT-RELOAD: {exc}", file=sys.stderr)

    if not args.dry_run:
        _save_local_sync_state(local_state_file, local_state)

    return 0 if missing == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
