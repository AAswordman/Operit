#!/usr/bin/env python3
"""按源码引用生成 Material 图标子集，同时保留动态名称目录。"""
import argparse
import hashlib
import json
import re
import subprocess
import zipfile
from pathlib import Path
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[2]
VERSION = "1.7.8"
PREFIX = "commonMain/androidx/compose/material/icons/"
SHA256 = {
    "extended": "77b7e782a519a3e074fc91e09d59334ac1b815edd87d2e89c0c9ca7c95678e93",
    "core": "14db888d2f698178799a4c18a58bb134b3e98063c6748c443b339044e0145432",
}
COMMANDS = {
    "moveTo": ("M", 2), "moveToRelative": ("m", 2),
    "lineTo": ("L", 2), "lineToRelative": ("l", 2),
    "horizontalLineTo": ("H", 1), "horizontalLineToRelative": ("h", 1),
    "verticalLineTo": ("V", 1), "verticalLineToRelative": ("v", 1),
    "curveTo": ("C", 6), "curveToRelative": ("c", 6),
    "reflectiveCurveTo": ("S", 4), "reflectiveCurveToRelative": ("s", 4),
    "arcTo": ("A", 7), "arcToRelative": ("a", 7), "close": ("Z", 0),
}


def source_catalog(cache: Path, kind: str) -> dict[str, str]:
    path = cache / f"{kind}-sources.jar"
    if not path.exists():
        name = f"material-icons-{kind}-android"
        url = f"https://dl.google.com/dl/android/maven2/androidx/compose/material/{name}/{VERSION}/{name}-{VERSION}-sources.jar"
        with urlopen(url, timeout=60) as response:
            path.write_bytes(response.read())
    if hashlib.sha256(path.read_bytes()).hexdigest() != SHA256[kind]:
        raise ValueError(f"来源校验失败：{path}")
    with zipfile.ZipFile(path) as archive:
        return {name[len(PREFIX):]: archive.read(name).decode("utf-8")
                for name in archive.namelist() if name.startswith(PREFIX) and name.endswith(".kt")}


def referenced_icons(terminal_source: Path) -> set[str]:
    # 只扫描仓库跟踪的 Kotlin 源码，排除图标子集自身和构建产物。
    # 不使用 rg，因为云端图标检查早于 ripgrep 安装。
    tracked = subprocess.run(["git", "ls-files", "-z", "*.kt"], cwd=ROOT,
                             capture_output=True, text=True, check=True).stdout
    paths = [ROOT / relative for relative in tracked.split("\0")
             if relative and not relative.startswith("material-icons-subset/")]
    terminal_sources = terminal_source / "src"
    if not terminal_sources.is_dir():
        raise ValueError(f"请先初始化 terminal 子模块，或通过 --terminal-source 指定固定版本源码：{terminal_source}")
    # 子模块源码不在父仓库的跟踪列表里，需要显式扫描，避免遗漏终端运行时图标。
    paths.extend(sorted(terminal_sources.rglob("*.kt")))
    names = set()
    for path in paths:
        text = path.read_text()
        for tail in re.findall(r"^import androidx\.compose\.material\.icons\.(.*)", text, re.M):
            key = tail.split(" as ")[0].strip().replace(".", "/") + ".kt"
            if key != "Icons.kt" and not key.endswith("/*.kt"):
                names.add(key)
        for auto, style, name in re.findall(r"Icons\.(AutoMirrored\.)?(Default|Filled|Outlined|Rounded|Sharp|TwoTone)\.([A-Za-z0-9_]+)", text):
            names.add(("automirrored/" if auto else "") + ("filled" if style == "Default" else style.lower()) + "/" + name + ".kt")
    return names


def path_data(source: str) -> str:
    paths = []
    matches = list(re.finditer(r"materialPath(?:\(([^{}]*)\))?\s*\{([^{}]*)\}", source, re.S))
    if not matches:
        raise ValueError("图标缺少矢量路径")
    for match in matches:
        header, body = match.group(1) or "", match.group(2)
        if header not in ("", "pathFillType = EvenOdd"):
            raise ValueError(f"尚未支持的路径属性：{header}")
        commands = []
        calls = list(re.finditer(r"(\w+)\(([^()]*)\)", body))
        residue = re.sub(r"(\w+)\(([^()]*)\)", "", body).strip()
        if residue:
            raise ValueError(f"尚未支持的路径语法：{residue}")
        for call in calls:
            command, arity = COMMANDS[call.group(1)]
            args = [value.strip().removesuffix("f") for value in call.group(2).split(",") if value.strip()]
            args = [{"true": "1", "false": "0"}.get(value, value) for value in args]
            if len(args) != arity or any(not re.fullmatch(r"[-+0-9.eE]+", value) for value in args):
                raise ValueError(f"路径参数异常：{call.group(0)}")
            commands.append(command + " ".join(args))
        paths.append(("E:" if header else "N:") + "".join(commands))
    return ("1" if "autoMirror = true" in source else "0") + "\t" + "|".join(paths)


def generate(cache: Path, terminal_source: Path) -> None:
    extended = source_catalog(cache, "extended")
    core = source_catalog(cache, "core")
    names = referenced_icons(terminal_source)
    missing = names - extended.keys() - core.keys()
    if missing:
        raise ValueError(f"图标引用未找到：{sorted(missing)}")
    selected = sorted(names & extended.keys())
    target = ROOT / "material-icons-subset/src/main/java/androidx/compose/material/icons"
    for old in target.rglob("*.kt"):
        old.unlink()
    for name in selected:
        path = target / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(extended[name])
    filled = {Path(name).stem: path_data(text) for name, text in (extended | core).items() if name.startswith("filled/")}
    rows = [f"{name}\t{data}" for name, data in sorted(filled.items())]
    chunks, current = [], ""
    for row in rows:
        if len((current + row).encode()) > 48_000:
            chunks.append(current)
            current = ""
        current += row + "\n"
    chunks.append(current)
    code = '''// 由 tools/icons/sync_material_icons.py 生成，请勿手动编辑。
// Material Icons 1.7.8，Android Open Source Project，Apache-2.0。
package com.ai.assistance.operit.ui.common.icons

internal object MaterialFilledIconData {
    val entries: Map<String, String> by lazy {
        listOf(CHUNKS)
            .flatMap { it.lineSequence().filter(String::isNotBlank).toList() }
            .associate { it.substringBefore('\t') to it.substringAfter('\t') }
    }
'''.replace("CHUNKS", ", ".join(f"chunk{i}()" for i in range(len(chunks))))
    for index, chunk in enumerate(chunks):
        code += f'    private fun chunk{index}(): String = """\n{chunk}""".trimIndent()\n'
    code += "}\n"
    (ROOT / "app/src/main/java/com/ai/assistance/operit/ui/common/icons/MaterialFilledIconData.kt").write_text(code)
    manifest = {"version": VERSION, "source_sha256": SHA256, "extended_total": len(extended),
                "static_icons": selected, "dynamic_filled_count": len(filled)}
    (ROOT / "tools/icons/material-icons-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    print(f"静态扩展图标 {len(selected)}/{len(extended)}；动态 Filled 名称 {len(filled)}")


def check_references(terminal_source: Path) -> None:
    selected = {str(p.relative_to(ROOT / "material-icons-subset/src/main/java/androidx/compose/material/icons"))
                for p in (ROOT / "material-icons-subset/src/main/java/androidx/compose/material/icons").rglob("*.kt")}
    manifest = json.loads((ROOT / "tools/icons/material-icons-manifest.json").read_text())
    core_names = set(manifest["core_icons"])
    missing = referenced_icons(terminal_source) - selected - core_names
    if missing:
        raise ValueError(f"请重新生成 Material 图标子集：{sorted(missing)}")
    print("Material 图标引用已覆盖")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--cache", type=Path, default=Path("/tmp/operit-material-icons"))
    parser.add_argument("--terminal-source", type=Path, default=ROOT / "terminal",
                        help="固定版本的 terminal 源码目录，默认使用已初始化的子模块")
    args = parser.parse_args()
    if args.check:
        check_references(args.terminal_source)
    else:
        args.cache.mkdir(parents=True, exist_ok=True)
        generate(args.cache, args.terminal_source)
        path = ROOT / "tools/icons/material-icons-manifest.json"
        manifest = json.loads(path.read_text())
        manifest["core_icons"] = sorted(source_catalog(args.cache, "core"))
        path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
