"""从指定源码提交的 Actions 产物发布已准备且经过固定清单校验的资源。"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

from publish_hub import publish, ROOT
from published_resource_state import existing_publication
from verify_hub_catalog import main as verify_catalog
from repositories.config import SOURCE_REPOSITORY, RESOURCE_REPOSITORY


def prepare_artifact(archive: Path, destination: Path) -> None:
    catalog = json.loads((ROOT / "app/config/offline-packs.json").read_text())
    expected = {pack["archive"]["path"]: pack["archive"]["bytes"] for pack in catalog["packs"]}
    expected["offline-packs.json"] = (ROOT / "app/config/offline-packs.json").stat().st_size
    with zipfile.ZipFile(archive) as source:
        entries = source.infolist()
        names = [entry.filename for entry in entries]
        if len(names) != len(set(names)) or set(names) != set(expected) | {"SHA256SUMS.txt"}:
            raise ValueError("资源产物必须仅包含当前清单声明的资源包、目录和校验清单")
        for entry in entries:
            if Path(entry.filename).name != entry.filename or entry.is_dir() or (entry.external_attr >> 16) & 0o170000 == 0o120000:
                raise ValueError("资源产物包含非法路径或符号链接")
            if entry.filename in expected and entry.file_size != expected[entry.filename]:
                raise ValueError("资源产物中的文件大小与固定清单不符：" + entry.filename)
            if entry.filename == "SHA256SUMS.txt" and entry.file_size > 1024 * 1024:
                raise ValueError("总校验清单过大")
            with source.open(entry) as stream, (destination / entry.filename).open("wb") as output:
                shutil.copyfileobj(stream, output)


def main(artifact_id: int) -> None:
    if os.environ["GITHUB_REPOSITORY"] != SOURCE_REPOSITORY:
        raise ValueError("必须从源码仓库发布固定资源")
    commit = os.environ["GITHUB_SHA"]
    if re.fullmatch(r"[0-9a-f]{40}", commit) is None:
        raise ValueError("源码提交格式不正确")
    verify_catalog(current_source=True)
    existing = existing_publication(ROOT / "app/config/offline-packs.json", RESOURCE_REPOSITORY)
    if existing is not None:
        print(json.dumps(existing, ensure_ascii=False), flush=True)
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a") as summary:
                summary.write("资源内容未变，已核对并复用现有发行，不创建标签或上传附件：" + existing["url"] + "\n")
        return
    if artifact_id <= 0:
        raise ValueError("资源版本尚未完整公开，必须选择当前提交的固定资源产物")
    artifact = json.loads(subprocess.run(["gh", "api", f"repos/{SOURCE_REPOSITORY}/actions/artifacts/{artifact_id}"],
                                         capture_output=True, text=True, check=True, timeout=30).stdout)
    if artifact["expired"] or artifact["workflow_run"]["head_sha"] != commit:
        raise ValueError("资源产物已过期或不属于本次派发的源码提交")
    with tempfile.TemporaryDirectory(prefix="operit-resource-release-") as directory:
        work = Path(directory)
        archive = work / "assets.zip"
        with archive.open("wb") as output:
            subprocess.run(["gh", "api", f"repos/{SOURCE_REPOSITORY}/actions/artifacts/{artifact_id}/zip"], stdout=output, check=True, timeout=300)
        assets = work / "assets"
        assets.mkdir()
        prepare_artifact(archive, assets)
        publish(assets, RESOURCE_REPOSITORY, commit)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-artifact-id", type=int, default=0)
    main(parser.parse_args().source_artifact_id)
