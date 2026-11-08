"""源码与发行仓库的固定分工。"""
from __future__ import annotations

SOURCE_REPOSITORY = "3316891527/Operit"
UPDATE_REPOSITORY = "3316891527/Operit-Kong-patch"
RESOURCE_REPOSITORY = "3316891527/Operit-Kong-resources"


def release_repositories(version: str) -> tuple[str, ...]:
    return (UPDATE_REPOSITORY,) if "+" in version else (SOURCE_REPOSITORY, UPDATE_REPOSITORY)


def source_info(commit: str, tag: str) -> dict:
    return {
        "sourceRepository": SOURCE_REPOSITORY,
        "sourceCommit": commit,
        "sourceTag": tag,
        "sourceUrl": f"https://github.com/{SOURCE_REPOSITORY}/tree/{commit}",
        "sourceArchiveUrl": f"https://github.com/{SOURCE_REPOSITORY}/archive/{commit}.zip",
    }
