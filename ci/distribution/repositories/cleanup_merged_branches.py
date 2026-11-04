"""公开发行及附件核验成功后，只清理计划中已随本轮发行合入的分支。"""
from __future__ import annotations

import argparse
import base64
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "ci/distribution"))
from repositories.config import SOURCE_REPOSITORY, UPDATE_REPOSITORY
from repositories.migrate_releases import api, asset_specs


def validate_release(cleanup: dict, release_plan: dict, published: dict, commit: str) -> list[str]:
    if cleanup["repository"] != SOURCE_REPOSITORY or re.fullmatch(r"[0-9a-f]{40}", commit) is None:
        raise ValueError("分支清理的仓库或构建提交无效")
    if release_plan["sourceCommit"] != commit or release_plan["repositories"] != [UPDATE_REPOSITORY]:
        raise ValueError("分支清理必须对应本轮实际发行提交和统一发行仓库")
    tag = "v" + cleanup["version"]
    if release_plan["tag"] != tag or published["tag_name"] != tag or published["draft"] or published["prerelease"]:
        raise ValueError("必须先公开计划对应的正式 Release")
    expected = {item["name"]: {"size": item["bytes"], "digest": "sha256:" + item["sha256"]} for item in release_plan["assets"]}
    if not {"app-kong.apk", "distribution-release.json"} <= expected.keys() or asset_specs(published["assets"]) != expected:
        raise ValueError("公开发行附件与本轮发布计划不一致")
    branches = cleanup["branches"]
    if not branches or len(branches) != len(set(branches)):
        raise ValueError("分支清理列表为空或包含重复项")
    if any(re.fullmatch(r"(?:feat|fix|chore)/[A-Za-z0-9._/-]+", name) is None or ".." in name for name in branches):
        raise ValueError("分支清理只接受本次明确列出的工作分支")
    return branches


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", type=Path, required=True)
    parser.add_argument("--release-plan", type=Path, required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--output", type=Path, default=Path(".ci-release/branch-cleanup-report.json"))
    args = parser.parse_args()
    cleanup = json.loads(args.plan.read_text())
    release_plan = json.loads(args.release_plan.read_text())
    published = api(f"repos/{UPDATE_REPOSITORY}/releases/tags/{quote(release_plan['tag'], safe='')}")
    branches = validate_release(cleanup, release_plan, published, args.commit)
    remote = {item["name"]: item["commit"]["sha"] for item in api(f"repos/{SOURCE_REPOSITORY}/branches?per_page=100", paginate=True)}
    open_heads = {item["head"]["ref"] for item in api(f"repos/{SOURCE_REPOSITORY}/pulls?state=open&per_page=100", paginate=True)}
    report = {"tag": release_plan["tag"], "sourceCommit": args.commit, "deleted": [], "retained": []}
    args.output.parent.mkdir(parents=True, exist_ok=True)

    def save() -> None:
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")

    save()
    # 删除租约锁定观察到的分支头，发布期间出现的新提交不能被误删。
    env = os.environ.copy()
    authorization = base64.b64encode(("x-access-token:" + os.environ["GH_TOKEN"]).encode()).decode()
    env.update(GIT_TERMINAL_PROMPT="0", GIT_CONFIG_COUNT="1", GIT_CONFIG_KEY_0="http.https://github.com/.extraheader", GIT_CONFIG_VALUE_0="Authorization: Basic " + authorization)
    for name in branches:
        if name not in remote:
            report["retained"].append({"branch": name, "reason": "分支已不存在"})
        elif name in open_heads:
            report["retained"].append({"branch": name, "reason": "仍有关联的开放 PR"})
        else:
            sha = remote[name]
            comparison = api(f"repos/{SOURCE_REPOSITORY}/compare/{sha}...{args.commit}")
            if comparison["status"] not in {"ahead", "identical"}:
                report["retained"].append({"branch": name, "reason": "分支含有本轮发行未包含的提交"})
            else:
                result = subprocess.run(
                    ["git", "push", f"--force-with-lease=refs/heads/{name}:{sha}", f"https://github.com/{SOURCE_REPOSITORY}.git", f":refs/heads/{name}"],
                    env=env, capture_output=True, text=True, timeout=45,
                )
                if result.returncode:
                    save()
                    raise RuntimeError("分支清理失败，已保留未完成项：" + name + "\n" + result.stderr)
                report["deleted"].append({"branch": name, "sha": sha})
                print("已清理随本轮发行合入的分支：" + name, flush=True)
        save()
    print(json.dumps(report, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
