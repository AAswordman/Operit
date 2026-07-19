#!/usr/bin/env python3
"""Validate and classify the exact merge candidate created for a pull request."""

from __future__ import annotations

import argparse
import fnmatch
import html
import os
import subprocess
from dataclasses import dataclass
from pathlib import Path


LOCALIZATION_PATTERNS = (
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-*/strings.xml",
    "app/src/main/res/xml/locales_config.xml",
    "tools/string/**",
)
TRANSLATED_STRINGS_PATTERN = "app/src/main/res/values-*/strings.xml"
LOCALE_CONFIG_PATH = "app/src/main/res/xml/locales_config.xml"
WEB_PATTERNS = (
    ".npmrc",
    "npm-shrinkwrap.json",
    "package-lock.json",
    "package.json",
    "web-chat/**",
)
TOOLPKG_PATTERNS = (
    ".npmrc",
    "app/src/main/assets/packages/**",
    "examples/**",
    "package.json",
    "package-lock.json",
    "npm-shrinkwrap.json",
    "pnpm-workspace.yaml",
    "tools/packages_whitelist.txt",
    "tools/sync_example_packages.py",
)
ANDROID_FULL_PATTERNS = (
    ".github/workflows/android-build.yml",
    ".github/workflows/pr-check.yml",
    ".gitmodules",
    "app/build.gradle.kts",
    "app/src/main/cpp/**",
    "build.gradle.kts",
    "cmake/**",
    "ci/script/download_android_dependencies.sh",
    "ci/script/prepare_android_dependencies.py",
    "gradle/**",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "settings.gradle.kts",
    "tools/native_ripgrep/**",
)
ANDROID_MODULE_ROOTS = (
    "dragonbones",
    "fbx",
    "llama",
    "mmd",
    "mnn",
    "quickjs",
    "showerclient",
    "terminal",
)
CI_PATTERNS = (
    ".github/actions/**",
    ".github/workflows/**",
    "ci/**",
)


@dataclass(frozen=True)
class ScopePlan:
    paths: tuple[str, ...]
    localization: bool
    android_resources: bool
    android_jvm: bool
    android_instrumentation: bool
    android_full: bool
    web: bool
    toolpkg: bool
    docs: bool
    yaml: bool
    ci: bool

    def output_values(self) -> dict[str, str]:
        return {
            "changed_count": str(len(self.paths)),
            "localization": boolean(self.localization),
            "android_resources": boolean(self.android_resources),
            "android_jvm": boolean(self.android_jvm),
            "android_instrumentation": boolean(self.android_instrumentation),
            "android_full": boolean(self.android_full),
            "web": boolean(self.web),
            "toolpkg": boolean(self.toolpkg),
            "docs": boolean(self.docs),
            "yaml": boolean(self.yaml),
            "ci": boolean(self.ci),
        }


@dataclass(frozen=True)
class CandidateContext:
    base_sha: str
    head_sha: str
    candidate_sha: str


def boolean(value: bool) -> str:
    return "true" if value else "false"


def path_matches(path: str, patterns: tuple[str, ...]) -> bool:
    for pattern in patterns:
        if pattern.endswith("/**"):
            root = pattern[:-3]
            if path == root or path.startswith(f"{root}/"):
                return True
        elif fnmatch.fnmatchcase(path, pattern):
            return True
    return False


def classify_paths(paths: list[str] | tuple[str, ...]) -> ScopePlan:
    normalized_paths = tuple(sorted(set(paths)))
    localization = any(path_matches(path, LOCALIZATION_PATTERNS) for path in normalized_paths)
    web = any(path_matches(path, WEB_PATTERNS) for path in normalized_paths)
    toolpkg = any(path_matches(path, TOOLPKG_PATTERNS) for path in normalized_paths)
    docs = any(path.endswith((".md", ".mdx")) for path in normalized_paths)
    yaml = any(path.endswith((".yml", ".yaml")) for path in normalized_paths)
    ci = any(path_matches(path, CI_PATTERNS) for path in normalized_paths)
    android_instrumentation = any(path.startswith("app/src/androidTest/") for path in normalized_paths)

    android_full = any(
        path_matches(path, ANDROID_FULL_PATTERNS)
        or any(path == root or path.startswith(f"{root}/") for root in ANDROID_MODULE_ROOTS)
        for path in normalized_paths
    )

    resource_change = False
    android_jvm_change = False
    for path in normalized_paths:
        if not path.startswith("app/"):
            continue
        if path_matches(path, ANDROID_FULL_PATTERNS):
            continue
        if path == LOCALE_CONFIG_PATH or fnmatch.fnmatchcase(path, TRANSLATED_STRINGS_PATTERN):
            resource_change = True
            continue
        android_jvm_change = True

    android_jvm = android_jvm_change and not android_full
    android_resources = resource_change and not android_jvm and not android_full

    return ScopePlan(
        paths=normalized_paths,
        localization=localization,
        android_resources=android_resources,
        android_jvm=android_jvm,
        android_instrumentation=android_instrumentation,
        android_full=android_full,
        web=web,
        toolpkg=toolpkg,
        docs=docs,
        yaml=yaml,
        ci=ci,
    )


def run_git(*args: str, text: bool = True) -> str | bytes:
    result = subprocess.run(
        ["git", *args],
        check=True,
        capture_output=True,
        text=text,
    )
    return result.stdout


def resolve_commit(value: str) -> str:
    return str(run_git("rev-parse", f"{value}^{{commit}}")).strip()


def candidate_context(candidate: str, expected_base: str, expected_head: str) -> CandidateContext:
    candidate_sha = resolve_commit(candidate)
    base_sha = resolve_commit(expected_base)
    head_sha = resolve_commit(expected_head)
    parent_line = str(run_git("rev-list", "--parents", "-n", "1", candidate_sha)).split()
    parents = parent_line[1:]

    if len(parents) != 2:
        raise ValueError(f"candidate {candidate_sha} must have exactly two parents, found {len(parents)}")
    if parents[0] != base_sha:
        raise ValueError(f"candidate first parent is {parents[0]}, expected base {base_sha}")
    if parents[1] != head_sha:
        raise ValueError(f"candidate second parent is {parents[1]}, expected head {head_sha}")

    return CandidateContext(base_sha=base_sha, head_sha=head_sha, candidate_sha=candidate_sha)


def changed_paths(base_sha: str, candidate_sha: str) -> list[str]:
    output = bytes(
        run_git(
            "diff",
            "--name-only",
            "--no-renames",
            "--diff-filter=ACMRDT",
            "-z",
            base_sha,
            candidate_sha,
            text=False,
        )
    )
    return [os.fsdecode(value) for value in output.split(b"\0") if value]


def write_github_output(path: Path, context: CandidateContext, plan: ScopePlan) -> None:
    values = {
        "base_sha": context.base_sha,
        "head_sha": context.head_sha,
        "candidate_sha": context.candidate_sha,
        **plan.output_values(),
    }
    with path.open("a", encoding="utf-8") as stream:
        for name, value in values.items():
            stream.write(f"{name}={value}\n")


def append_step_summary(path: Path, context: CandidateContext, plan: ScopePlan) -> None:
    scopes = plan.output_values()
    with path.open("a", encoding="utf-8") as stream:
        stream.write("## Pull request candidate\n\n")
        stream.write(f"- Base: `{context.base_sha}`\n")
        stream.write(f"- Head: `{context.head_sha}`\n")
        stream.write(f"- Candidate: `{context.candidate_sha}`\n")
        stream.write(f"- Changed paths: `{len(plan.paths)}`\n\n")
        stream.write("| Scope | Required |\n| --- | --- |\n")
        for name in (
            "localization",
            "android_resources",
            "android_jvm",
            "android_instrumentation",
            "android_full",
            "web",
            "toolpkg",
            "docs",
            "yaml",
            "ci",
        ):
            stream.write(f"| `{name}` | `{scopes[name]}` |\n")
        if plan.paths:
            stream.write("\n<details><summary>Candidate paths</summary>\n\n")
            for changed_path in plan.paths[:100]:
                stream.write(f"- <code>{html.escape(changed_path)}</code>\n")
            if len(plan.paths) > 100:
                stream.write(f"- {len(plan.paths) - 100} additional path(s) omitted.\n")
            stream.write("\n</details>\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)
    plan_parser = subparsers.add_parser("plan")
    plan_parser.add_argument("--candidate", required=True)
    plan_parser.add_argument("--expected-base", required=True)
    plan_parser.add_argument("--expected-head", required=True)
    plan_parser.add_argument("--github-output", type=Path)
    plan_parser.add_argument("--step-summary", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    context = candidate_context(args.candidate, args.expected_base, args.expected_head)
    plan = classify_paths(changed_paths(context.base_sha, context.candidate_sha))

    if args.github_output:
        write_github_output(args.github_output, context, plan)
    if args.step_summary:
        append_step_summary(args.step_summary, context, plan)

    values = plan.output_values()
    print(
        f"Candidate {context.candidate_sha} adds {len(plan.paths)} path(s) to "
        f"base {context.base_sha}."
    )
    print("Scopes: " + " ".join(f"{name}={value}" for name, value in values.items() if name != "changed_count"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
