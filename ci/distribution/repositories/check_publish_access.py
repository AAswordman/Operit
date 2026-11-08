"""在构建或资源上传前检查专用发行凭据及目标仓库。"""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "ci/distribution"))
from repositories.config import SOURCE_REPOSITORY, UPDATE_REPOSITORY, RESOURCE_REPOSITORY


def check(mode: str) -> None:
    if not os.environ.get("GH_TOKEN", "").strip():
        raise ValueError("缺少 OPERIT_DISTRIBUTION_TOKEN；需为源码仓库和对应发行仓库配置访问权限")
    repositories = (SOURCE_REPOSITORY, UPDATE_REPOSITORY, RESOURCE_REPOSITORY) if mode == "release" else (SOURCE_REPOSITORY, RESOURCE_REPOSITORY)
    for repository in repositories:
        result = subprocess.run(["gh", "api", f"repos/{repository}"], text=True, capture_output=True, check=True, timeout=30)
        info = json.loads(result.stdout)
        if not info["default_branch"]:
            raise ValueError("发行仓库必须先初始化许可证和说明：" + repository)
        subprocess.run(["gh", "api", f"repos/{repository}/git/ref/heads/{info['default_branch']}"], capture_output=True, text=True, check=True, timeout=30)
        requires_write = mode == "release" or repository == RESOURCE_REPOSITORY
        if requires_write and not info["permissions"]["push"]:
            raise ValueError("凭据缺少仓库写入权限：" + repository)
        print("已核对发行访问权限：" + repository, flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=["release", "resources"], required=True)
    check(parser.parse_args().mode)
