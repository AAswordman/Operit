"""为独立发行版发布完整 APK 和可逐字节还原的 apkraw 增量包。"""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import time
import sys
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "ci/distribution"))
from repositories.config import SOURCE_REPOSITORY, UPDATE_REPOSITORY, release_repositories, source_info
sys.path.insert(0, str(ROOT / "ci/distribution/resources"))
from prepare_current_resources import release_prepared

REPOSITORY = SOURCE_REPOSITORY
APK_NAME = "app-kong.apk"
PACKAGE_NAME = "com.ai.assistance.operit.kong"
CERTIFICATE_SHA256 = "5E01FFF4DD01A44BAB8318BE35107AE39C60A1261CFCE33003EA3528727EA799"
VERSION_PATTERN = re.compile(r"^v?(\d+)\.(\d+)\.(\d+)(?:\+(\d+))?$")


def version_key(value: str) -> tuple[int, int, int, int]:
    match = VERSION_PATTERN.fullmatch(value)
    if match is None:
        raise ValueError(f"版本必须使用 x.y.z 或 x.y.z+n：{value}")
    major, minor, patch, index = match.groups()
    return int(major), int(minor), int(patch), 0 if index is None else int(index)


def select_previous_release(releases: list[dict], target: str, previous_tag: str = "") -> dict | None:
    target_key = version_key(target)
    candidates = []
    for release in releases:
        tag = release["tag_name"]
        if release["draft"] or release["prerelease"] or VERSION_PATTERN.fullmatch(tag) is None:
            continue
        key = version_key(tag)
        if key[:3] == target_key[:3] and key < target_key and any(a["name"] == APK_NAME for a in release["assets"]):
            candidates.append(release)
    if previous_tag:
        return next((r for r in candidates if r["tag_name"] == previous_tag), None)
    return max(candidates, key=lambda r: version_key(r["tag_name"]), default=None)


def run(*args: str) -> str:
    result = subprocess.run(args, text=True, capture_output=True, check=True)
    return result.stdout


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def inspect_apk(apk: Path, build_tools: Path) -> dict:
    badging = run(str(build_tools / "aapt"), "dump", "badging", str(apk))
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.MULTILINE)
    if package is None:
        raise ValueError("APK 缺少可解析的包名和版本信息")
    name, code, version = package.groups()
    if name != PACKAGE_NAME:
        raise ValueError(f"APK 包名不属于独立发行版：{name}")
    version_key(version)
    signatures = run(str(build_tools / "apksigner"), "verify", "--print-certs", str(apk))
    certificates = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", signatures)
    if [x.upper() for x in certificates] != [CERTIFICATE_SHA256]:
        raise ValueError("APK 未使用已有的独立发行固定签名")
    return {"packageName": name, "versionCode": int(code), "versionName": version, "certificateSha256": CERTIFICATE_SHA256}


def hotbuild_module(name: str):
    spec = importlib.util.spec_from_file_location(name, ROOT / "tools/hotbuild" / (name + ".py"))
    if spec is None or spec.loader is None:
        raise RuntimeError(f"缺少补丁模块：{name}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def prepare_patch(base: Path, target: Path, output: Path, old_version: str, new_version: str) -> tuple[Path, Path, dict]:
    old_key = version_key(old_version)
    new_key = version_key(new_version)
    if old_key[:3] != new_key[:3] or old_key >= new_key:
        raise ValueError("增量包只能连接同一基础版本的递增发行版本")
    output.mkdir(parents=True, exist_ok=True)
    base_sha, target_sha = sha256(base), sha256(target)
    suffix = f"{old_version}_to_{new_version}_{base_sha[:12]}_{target_sha[:12]}"
    patch = output / f"apkrawpatch_{suffix}.zip"
    meta_file = output / f"patch_{suffix}.json"
    extra = hotbuild_module("build_patch").build_apkraw_patch(base, target, patch)
    meta = {
        "format": "apkraw-1", "repo": UPDATE_REPOSITORY, "tag": "v" + new_version,
        "fromVersion": old_version.split("+")[0], "toVersion": new_version.split("+")[0],
        "fromPatchIndex": old_key[3], "toPatchIndex": new_key[3],
        "baseSha256": base_sha, "targetSha256": target_sha,
        "patchFile": patch.name, "patchSha256": sha256(patch), "createdAt": int(time.time()),
        **extra,
    }
    meta_file.write_text(json.dumps(meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    restored = output / "restored.apk"
    hotbuild_module("apply_patch").apply_apkraw_patch(base, patch, restored, meta)
    if sha256(restored) != target_sha:
        raise ValueError("补丁还原结果与目标 APK 的 SHA-256 不一致")
    restored.unlink()
    summary = {k: meta[k] for k in ["format", "tag", "fromVersion", "toVersion", "fromPatchIndex", "toPatchIndex", "baseSha256", "targetSha256", "patchFile", "patchSha256"]}
    summary["metaFile"] = meta_file.name
    summary["patchSize"] = patch.stat().st_size
    summary["targetSize"] = target.stat().st_size
    return patch, meta_file, summary


def ensure_source_tag(tag: str, commit: str) -> None:
    endpoint = f"repos/{SOURCE_REPOSITORY}"
    refs = json.loads(run("gh", "api", f"{endpoint}/git/matching-refs/tags/{quote(tag, safe='')}"))
    if any(ref["ref"] == "refs/tags/" + tag for ref in refs):
        actual = run("gh", "api", f"{endpoint}/commits/{quote(tag, safe='')}", "--jq", ".sha").strip()
        if actual != commit:
            raise ValueError("源码标签已指向其他提交，禁止移动")
        return
    run("gh", "api", f"{endpoint}/git/refs", "--method", "POST", "-f", "ref=refs/tags/" + tag, "-f", "sha=" + commit)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--build-tools", type=Path, required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--previous-tag", default="")
    parser.add_argument("--notes", default="")
    parser.add_argument("--output", type=Path, default=Path(".ci-release"))
    parser.add_argument("--prepare-only", action="store_true", help="核对 APK、生成并还原补丁及发布计划，不创建标签或 Release")
    args = parser.parse_args()
    if args.repository != REPOSITORY or re.fullmatch(r"[0-9a-f]{40}", args.commit) is None:
        raise ValueError("发布仓库或目标提交不属于本次独立发行流程")
    if args.apk.name != APK_NAME:
        raise ValueError(f"发行 APK 必须命名为 {APK_NAME}")
    if not args.notes.strip():
        raise ValueError("正式发布必须提供非空的更新说明")
    if args.notes.lstrip().startswith(("{", "[")):
        raise ValueError("Release 正文必须使用可读文本或 Markdown，JSON 元数据仅作为附件")
    info = inspect_apk(args.apk, args.build_tools)
    version = info["versionName"]
    tag = "v" + version
    destinations = release_repositories(version)
    releases = json.loads(run("gh", "api", f"repos/{UPDATE_REPOSITORY}/releases?per_page=100"))
    if not args.prepare_only and any(r["tag_name"] == tag for r in releases):
        raise ValueError(f"发行版本已存在，不覆盖已发布的 APK 或补丁：{tag}")
    previous = select_previous_release(releases, version, args.previous_tag)
    if args.previous_tag and previous is None:
        raise ValueError("指定的基础 Release 不存在或不符合增量更新条件")
    args.output.mkdir(parents=True, exist_ok=True)
    summary = {
        "format": "operit-distribution-1", "tag": tag, **info,
        "targetSha256": sha256(args.apk), "releaseNotes": args.notes,
        "updateRepository": UPDATE_REPOSITORY, **source_info(args.commit, tag),
    }
    assets = [args.apk]
    if previous is not None:
        base_dir = args.output / "base"
        base_dir.mkdir(exist_ok=True)
        run("gh", "release", "download", previous["tag_name"], "--repo", UPDATE_REPOSITORY, "--pattern", APK_NAME, "--dir", str(base_dir))
        base = base_dir / APK_NAME
        base_info = inspect_apk(base, args.build_tools)
        if base_info["versionName"] != previous["tag_name"].removeprefix("v"):
            raise ValueError("基础 Release 标签与 APK 内的版本不一致")
        if base_info["versionCode"] >= info["versionCode"]:
            raise ValueError("新版 versionCode 必须高于基础 APK")
        patch, metadata, patch_summary = prepare_patch(base, args.apk, args.output, base_info["versionName"], version)
        summary.update(patch_summary)
        assets.extend([patch, metadata])
        print(f"增量包 {patch.stat().st_size} 字节，完整 APK {args.apk.stat().st_size} 字节", flush=True)
    else:
        print("当前基础版本尚无已发布 APK，本次建立完整包基线", flush=True)
    # 资源必须与 APK 内的目录和本轮提交一致，先公开资源再创建应用发行草稿。
    summary["offlineResources"] = release_prepared(
        args.apk, args.commit, ROOT / ".ci-current-resources", publish_resources=not args.prepare_only)
    manifest = args.output / "distribution-release.json"
    manifest.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    assets.append(manifest)
    # 页面正文与增量元数据分别保存，防止发布页再次显示机器 JSON。
    notes_file = args.output / "release-notes.md"
    notes_file.write_text(
        args.notes.rstrip() + f"\n\n源码：[对应提交](https://github.com/{SOURCE_REPOSITORY}/tree/{args.commit}) · "
        f"[源码归档](https://github.com/{SOURCE_REPOSITORY}/archive/{args.commit}.zip)\n", encoding="utf-8")
    plan = {"tag": tag, "sourceCommit": args.commit, "repositories": list(destinations),
            "assets": [{"name": asset.name, "bytes": asset.stat().st_size, "sha256": sha256(asset)} for asset in assets]}
    (args.output / "release-plan.json").write_text(json.dumps(plan, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.prepare_only:
        print(json.dumps({"prepared": True, **plan}, ensure_ascii=False), flush=True)
        return
    # 正式版的两个目的地都先检查，避免覆盖已存在的 Release。
    if SOURCE_REPOSITORY in destinations:
        source_releases = json.loads(run("gh", "api", f"repos/{SOURCE_REPOSITORY}/releases?per_page=100"))
        if any(release["tag_name"] == tag for release in source_releases):
            raise ValueError(f"主仓库发行版本已存在：{tag}")
    ensure_source_tag(tag, args.commit)
    # 各发行仓库的标签指向各自文档提交；源码身份由主仓库标签和附件中的源码提交固定。
    # 所有目的地都上传完草稿后再公开，客户端最后从统一更新仓库发现更新。
    for repository in destinations:
        target = ("--target", args.commit) if repository == SOURCE_REPOSITORY else ()
        run("gh", "release", "create", tag, "--repo", repository, *target, "--draft", "--title", version,
            "--notes-file", str(notes_file), *[str(asset) for asset in assets])
    for repository in destinations:
        run("gh", "release", "edit", tag, "--repo", repository, "--draft=false", "--latest")
        print(f"已发布 https://github.com/{repository}/releases/tag/{tag}", flush=True)


if __name__ == "__main__":
    main()
