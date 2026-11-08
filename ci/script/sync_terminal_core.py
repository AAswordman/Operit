"""将应用锁定的终端提交快进到独立仓库的默认分支。"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[2]
SOURCE_REPOSITORY = "3316891527/Operit"
CORE_REPOSITORY = "3316891527/OperitTerminalCore"
CORE_URL = f"https://github.com/{CORE_REPOSITORY}.git"


def git(*args: str) -> str:
    return subprocess.run(
        ["git", *args], cwd=ROOT, text=True, capture_output=True, check=True, timeout=30
    ).stdout.strip()


def api(endpoint: str, *, update: dict | None = None) -> dict:
    command = ["gh", "api", endpoint]
    payload = None
    if update is not None:
        command += ["--method", "PATCH", "--input", "-"]
        payload = json.dumps(update)
    result = subprocess.run(
        command, input=payload, text=True, capture_output=True, check=True, timeout=30
    )
    return json.loads(result.stdout)


def record(
    message: str, source: str, target: str, branch: str,
    before: str, after: str, adopted: dict[str, str],
) -> None:
    details = (
        f"{message}\n"
        f"Operit: {source}\n"
        f"terminal gitlink: {target}\n"
        f"Adopted refs: {json.dumps(adopted, sort_keys=True)}\n"
        f"{CORE_REPOSITORY}/{branch}: {before} -> {after}\n"
    )
    print(details, flush=True)
    if summary := os.environ.get("GITHUB_STEP_SUMMARY"):
        with Path(summary).open("a", encoding="utf-8") as stream:
            stream.write(f"## Terminal core sync\n\n{message}\n\n")
            stream.write(f"- Operit commit: `{source}`\n")
            stream.write(f"- terminal commit: `{target}`\n")
            for ref, commit in adopted.items():
                stream.write(f"- Operit/{ref} terminal: `{commit}`\n")
            stream.write(f"- Core branch: `{CORE_REPOSITORY}/{branch}`\n")
            stream.write(f"- Before: `{before}`\n- After: `{after}`\n")


def sync(*, dry_run: bool) -> None:
    if not os.environ.get("GH_TOKEN", "").strip():
        raise ValueError("缺少 GH_TOKEN；工作流需配置可写入终端仓库的 OPERIT_DISTRIBUTION_TOKEN")
    configured_url = git("config", "--blob", "HEAD:.gitmodules", "--get", "submodule.terminal.url")
    if configured_url != CORE_URL:
        raise ValueError(f"terminal 来源与同步目标不一致：{configured_url}")
    source = git("rev-parse", "HEAD")
    adopted: dict[str, str] = {}
    # Actions 会合并等待中的同组运行；每次读取两个分支的最新引用，避免漏掉较新的 dev。
    for source_ref in ("dev", "main"):
        submodule = api(f"repos/{SOURCE_REPOSITORY}/contents/terminal?ref={source_ref}")
        if submodule["type"] != "submodule" or submodule["submodule_git_url"] != CORE_URL:
            raise ValueError(f"{SOURCE_REPOSITORY}/{source_ref} 的 terminal 来源与同步目标不一致")
        adopted[source_ref] = submodule["sha"]
    target = adopted["dev"]
    if target != adopted["main"]:
        branches = api(f"repos/{CORE_REPOSITORY}/compare/{target}...{adopted['main']}")
        if branches["status"] == "ahead":
            target = adopted["main"]
        elif branches["status"] != "behind":
            raise ValueError(f"应用两个分支采用的终端历史已分叉：{branches['html_url']}")
    repository = api(f"repos/{CORE_REPOSITORY}")
    if not repository["permissions"]["push"]:
        raise ValueError(f"凭据缺少写入权限：{CORE_REPOSITORY}")
    branch = repository["default_branch"]
    ref_path = f"repos/{CORE_REPOSITORY}/git/ref/heads/{quote(branch, safe='')}"
    before = api(ref_path)["object"]["sha"]
    comparison = api(f"repos/{CORE_REPOSITORY}/compare/{before}...{target}")
    status = comparison["status"]
    if status == "identical":
        record("终端默认分支已与应用引用一致，无需更新。", source, target, branch, before, before, adopted)
        return
    if status == "behind":
        # main 的旧版发布事件可能晚于 dev；保留已经包含目标提交的新版本。
        record("终端默认分支已包含该提交，保留现有较新版本。", source, target, branch, before, before, adopted)
        return
    if status != "ahead":
        raise ValueError(
            f"终端默认分支与应用引用已分叉，需先在终端仓库整合历史：{comparison['html_url']}"
        )
    if dry_run:
        record(
            f"预览：将快进 {comparison['ahead_by']} 个提交；未修改远端。",
            source, target, branch, before, before, adopted,
        )
        return

    # 直接推进引用，保留应用固定的提交及源码；GitHub 拒绝非快进更新。
    endpoint = f"repos/{CORE_REPOSITORY}/git/refs/heads/{quote(branch, safe='')}"
    updated = api(endpoint, update={"sha": target, "force": False})
    after = updated["object"]["sha"]
    if after != target:
        raise ValueError(f"终端默认分支更新结果不一致：期望 {target}，实际 {after}")
    verified = api(ref_path)["object"]["sha"]
    if verified != target:
        raise ValueError(f"终端默认分支在更新后发生变化：期望 {target}，实际 {verified}")
    record(
        f"终端默认分支已快进 {comparison['ahead_by']} 个提交，与应用引用一致。",
        source, target, branch, before, verified, adopted,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="只读取版本与提交关系，不修改远端")
    args = parser.parse_args()
    try:
        sync(dry_run=args.dry_run)
    except subprocess.CalledProcessError as error:
        print(f"终端同步命令失败：{error.stderr.strip()}", file=sys.stderr)
        return 1
    except ValueError as error:
        print(f"终端同步失败：{error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
