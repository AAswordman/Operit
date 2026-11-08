"""预览或复制历史发行到专用仓库；保留源 Release、标签和备份。"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "ci/distribution"))
from repositories.config import SOURCE_REPOSITORY, UPDATE_REPOSITORY, RESOURCE_REPOSITORY, source_info

VERSION_PATTERN = re.compile(r"^v\d+\.\d+\.\d+(?:\+\d+)?$")


def api(endpoint: str, payload: dict | None = None, *, paginate: bool = False, method: str | None = None) -> dict | list:
    command = ["gh", "api", endpoint]
    if payload is not None:
        command += ["--method", method or "POST", "--input", "-"]
    elif method is not None:
        command += ["--method", method]
    if paginate:
        command += ["--paginate"]
    result = subprocess.run(command, input=None if payload is None else json.dumps(payload),
                            text=True, capture_output=True, timeout=45)
    if result.returncode:
        raise RuntimeError(result.stderr.strip())
    if not paginate:
        return json.loads(result.stdout)
    # gh --paginate 输出连续的 JSON 页面，逐页解码以保留全部历史发行。
    remaining = result.stdout.lstrip()
    decoder = json.JSONDecoder()
    values = []
    while remaining:
        page, end = decoder.raw_decode(remaining)
        values.extend(page)
        remaining = remaining[end:].lstrip()
    return values


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return "sha256:" + hashlib.file_digest(stream, "sha256").hexdigest()


def destination(release: dict) -> str:
    tag = release["tag_name"]
    if tag.startswith("resources-"):
        return RESOURCE_REPOSITORY
    if VERSION_PATTERN.fullmatch(tag) and any(asset["name"] == "app-kong.apk" for asset in release["assets"]):
        return UPDATE_REPOSITORY
    raise ValueError("不能确定发行职责，必须先人工核对：" + tag)


def asset_specs(assets: list[dict]) -> dict[str, dict]:
    result = {}
    for asset in assets:
        name = asset["name"]
        if name in {".", ".."} or Path(name).name != name or name in result:
            raise ValueError("发行附件名称无效或重复：" + name)
        if asset["state"] != "uploaded" or not re.fullmatch(r"sha256:[0-9a-f]{64}", asset.get("digest") or ""):
            raise ValueError("附件未上传完成或没有可靠的 SHA-256：" + name)
        result[name] = {"size": asset["size"], "digest": asset["digest"]}
    if not result:
        raise ValueError("不能迁移没有附件的发行")
    return result


def migration_plan(releases: list[dict]) -> list[dict]:
    result = []
    for release in releases:
        if release["draft"] or release["prerelease"]:
            raise ValueError("存在草稿或预发布，先完成或明确处理后再切换：" + release["tag_name"])
        specs = asset_specs(release["assets"])
        result.append({"tag": release["tag_name"], "releaseId": release["id"],
                       "repository": destination(release), "files": specs,
                       "bytes": sum(spec["size"] for spec in specs.values())})
    return result


def verify_files(directory: Path, expected: dict[str, dict]) -> None:
    if {path.name for path in directory.iterdir()} != set(expected):
        raise ValueError("本地附件集合与固定清单不一致")
    for name, spec in expected.items():
        path = directory / name
        if not path.is_file() or path.stat().st_size != spec["size"] or sha256(path) != spec["digest"]:
            raise ValueError("附件大小或 SHA-256 不一致：" + name)


def prepare_assets(source: Path, output: Path, release: dict, repository: str, commit: str) -> dict[str, dict]:
    output.mkdir(parents=True, exist_ok=True)
    if any(output.iterdir()):
        raise ValueError("准备目录必须为空，禁止混入上次未完成的附件")
    old_prefix = f"https://github.com/{SOURCE_REPOSITORY}/releases/"
    new_prefix = f"https://github.com/{repository}/releases/"
    for original in source.iterdir():
        target = output / original.name
        if original.suffix == ".json":
            text = original.read_text(encoding="utf-8").replace(old_prefix, new_prefix)
            value = json.loads(text)
            if isinstance(value, dict) and value.get("repo") == SOURCE_REPOSITORY:
                value["repo"] = repository
                text = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
            if original.name == "distribution-release.json":
                value.update(source_info(commit, release["tag_name"]))
                value["updateRepository"] = UPDATE_REPOSITORY
                text = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
            target.write_text(text, encoding="utf-8")
        else:
            shutil.copyfile(original, target)
    # 仅目录和元数据改地址；APK、补丁、模型和资源归档保持逐字节相同。
    sums = output / "SHA256SUMS.txt"
    if sums.exists():
        sums.write_text("".join(f"{sha256(path).removeprefix('sha256:')}  {path.name}\n"
                                for path in sorted(output.iterdir()) if path != sums), encoding="utf-8")
    return {path.name: {"size": path.stat().st_size, "digest": sha256(path)} for path in sorted(output.iterdir())}


def verify_remote(release: dict, expected: dict[str, dict]) -> None:
    if asset_specs(release["assets"]) != expected:
        raise ValueError("目标发行附件与核对后的迁移内容不一致：" + release["tag_name"])


def release_body(release: dict, repository: str, commit: str) -> str:
    text = (release["body"] or "").replace(f"https://github.com/{SOURCE_REPOSITORY}/releases/",
                                          f"https://github.com/{repository}/releases/")
    if text.lstrip().startswith("{"):
        value = json.loads(text)
        value.update(source_info(commit, release["tag_name"]))
        if value.get("repo") == SOURCE_REPOSITORY:
            value["repo"] = repository
        return json.dumps(value, ensure_ascii=False, indent=2) + "\n"
    return text.rstrip() + f"\n\n对应源码：https://github.com/{SOURCE_REPOSITORY}/tree/{commit}\n"


def initialize_repositories() -> None:
    # 此操作须显式选择，预览迁移不会创建仓库；已有同名仓库仅核对后补充说明。
    owned = {item["full_name"]: item for item in api("user/repos?affiliation=owner&per_page=100", paginate=True)}
    for repository, label in [(UPDATE_REPOSITORY, "正式版与补丁版发行"), (RESOURCE_REPOSITORY, "固定离线资源发行")]:
        name = repository.split("/", 1)[1]
        if repository not in owned:
            api("user/repos", {"name": name, "description": "Operit Kong · " + label,
                               "private": False, "auto_init": True, "license_template": "lgpl-3.0"})
        else:
            info = owned[repository]
            if info["private"] or not info["permissions"]["admin"]:
                raise ValueError("现有仓库不符合维护者公开发行目标：" + repository)
        text = f"# {name}\n\n本仓库用于{label}。\n\n应用源码与版本标签位于 https://github.com/{SOURCE_REPOSITORY} 。各发行说明和元数据链接对应源码提交与归档。本仓库自动生成的 Source code 归档仅包含发行文档，不代表应用源码。模型、引擎及第三方资源沿用各自随附许可证。\n"
        existing = api(f"repos/{repository}/contents/README.md")
        api(f"repos/{repository}/contents/README.md", {"message": "docs: 说明发行职责和对应源码", "sha": existing["sha"], "content": base64.b64encode(text.encode()).decode()}, method="PUT")
        print("已创建 https://github.com/" + repository, flush=True)


def migrate(releases: list[dict], output: Path) -> None:
    receipts = []
    for release in reversed(releases):
        tag, repository = release["tag_name"], destination(release)
        commit = api(f"repos/{SOURCE_REPOSITORY}/commits/{quote(tag, safe='')}")["sha"]
        target_releases = api(f"repos/{repository}/releases?per_page=100", paginate=True)
        remote = next((item for item in target_releases if item["tag_name"] == tag), None)
        folder = output / "assets" / tag
        source = folder / "source"
        source.mkdir(parents=True, exist_ok=True)
        # 原始附件随迁移备份保留；重跑时必须先通过同一份摘要核对。
        if not any(source.iterdir()):
            subprocess.run(["gh", "release", "download", tag, "--repo", SOURCE_REPOSITORY, "--dir", str(source)], check=True, timeout=900)
        verify_files(source, asset_specs(release["assets"]))
        prepared = folder / "prepared"
        if prepared.exists():
            shutil.rmtree(prepared)
        expected = prepare_assets(source, prepared, release, repository, commit)
        if remote is None:
            # 草稿尚未生成 Git 标签，创建时取得 Release ID，后续读取和公开均使用该 ID。
            remote = api(f"repos/{repository}/releases", {
                "tag_name": tag, "name": release["name"] or tag,
                "body": release_body(release, repository, commit), "draft": True, "prerelease": False,
            })
            subprocess.run(["gh", "release", "upload", tag, "--repo", repository,
                            *[str(path) for path in sorted(prepared.iterdir())]], check=True, timeout=900)
        endpoint = f"repos/{repository}/releases/{remote['id']}"
        remote = api(endpoint)
        if remote["tag_name"] != tag or remote["prerelease"]:
            raise ValueError("目标发行身份与迁移计划不一致：" + tag)
        verify_remote(remote, expected)
        if remote["draft"]:
            remote = api(endpoint, {"draft": False, "make_latest": "false"}, method="PATCH")
        # 公开响应仍须核对，避免将未公开或附件发生变化的目标写入完成收据。
        if remote["draft"] or remote["prerelease"] or remote["tag_name"] != tag:
            raise ValueError("目标发行尚未按迁移计划公开：" + tag)
        verify_remote(remote, expected)
        receipts.append({"tag": tag, "repository": repository, "sourceCommit": commit, "sourceReleaseId": release["id"], "sourceFiles": asset_specs(release["assets"]), "files": expected})
        (output / "verified-receipts.json").write_text(json.dumps(receipts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("已逐项核对：https://github.com/" + repository + "/releases/tag/" + tag, flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--initialize", action="store_true", help="创建两个带许可证和源码说明的公开仓库")
    parser.add_argument("--apply", action="store_true", help="下载并校验源文件后复制发行，不删除源发行")
    args = parser.parse_args()
    releases = api(f"repos/{SOURCE_REPOSITORY}/releases?per_page=100", paginate=True)
    plan = migration_plan(releases)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "source-releases.json").write_text(json.dumps(releases, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (args.output / "migration-plan.json").write_text(json.dumps(plan, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"releases": len(plan), "files": sum(len(item["files"]) for item in plan),
                      "bytes": sum(item["bytes"] for item in plan), "applied": args.apply}, ensure_ascii=False), flush=True)
    if args.initialize:
        initialize_repositories()
    if args.apply:
        migrate(releases, args.output)
        receipts = json.loads((args.output / "verified-receipts.json").read_text())
        cleanup_receipts = {"sourceRepository": SOURCE_REPOSITORY, "updateRepository": UPDATE_REPOSITORY,
                            "resourceRepository": RESOURCE_REPOSITORY, "releases": receipts}
        (args.output / "cleanup-receipts.json").write_text(json.dumps(cleanup_receipts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
