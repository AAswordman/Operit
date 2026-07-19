#!/usr/bin/env python3
"""Check localization regressions introduced by a merge candidate."""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import xml.etree.ElementTree as ET
from collections import Counter
from dataclasses import dataclass

from check_output import Diagnostic, report


RESOURCE_ROOT = "app/src/main/res"
LOCALE_CONFIG_PATH = f"{RESOURCE_ROOT}/xml/locales_config.xml"
ANDROID_NAME = "{http://schemas.android.com/apk/res/android}name"
ANDROID_DEFAULT_LOCALE = "{http://schemas.android.com/apk/res/android}defaultLocale"
PRINTF_RE = re.compile(r"%(?:\d+\$)?[-+# 0,(<]*\d*(?:\.\d+)?[a-zA-Z]")
BRACE_RE = re.compile(r"\{[A-Za-z0-9_]+\}")
HAN_RE = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
LOCALE_TAG_RE = re.compile(r"^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$")
LEGACY_LOCALE_RE = re.compile(r"^(?P<language>[a-z]{2,3})(?:-r(?P<region>[A-Z]{2}|\d{3}))?$")
BCP47_QUALIFIER_RE = re.compile(r"^b\+(?P<parts>[A-Za-z0-9]{2,8}(?:\+[A-Za-z0-9]{2,8})*)$")


@dataclass(frozen=True)
class ResourceEntry:
    name: str
    tag: str
    text: str
    attributes: tuple[tuple[str, str], ...]
    items: tuple[tuple[str, str], ...]


@dataclass(frozen=True)
class LocalizationIssue:
    code: str
    path: str
    locale: str | None
    key: str | None
    message: str

    @property
    def identity(self) -> tuple[str, str, str | None, str | None]:
        return self.code, self.path, self.locale, self.key


@dataclass
class Snapshot:
    files: dict[str, str]
    entries: dict[str, dict[str, ResourceEntry]]
    localized_files: dict[str, tuple[str, dict[str, ResourceEntry]]]
    localized_duplicates: dict[str, set[str]]
    parse_errors: dict[str, str]
    locale_tags: set[str]
    config_error: str | None
    blobs: dict[str, bytes]


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def run_git_bytes(*args: str) -> bytes:
    result = subprocess.run(["git", *args], check=True, capture_output=True)
    return result.stdout


def tree_paths(commit: str, root: str) -> list[str]:
    output = run_git_bytes("ls-tree", "-r", "-z", "--name-only", commit, "--", root)
    return [os.fsdecode(value) for value in output.split(b"\0") if value]


def read_blob(commit: str, path: str) -> bytes:
    return run_git_bytes("show", f"{commit}:{path}")


def locale_path_details(path: str) -> tuple[str, bool] | None:
    match = re.fullmatch(rf"{re.escape(RESOURCE_ROOT)}/(values(?:-[^/]+)?)/strings\.xml", path)
    if not match:
        return None
    directory = match.group(1)
    if directory == "values":
        return "zh", True
    qualifier = directory.removeprefix("values-")
    parts = qualifier.split("-")
    bcp47 = BCP47_QUALIFIER_RE.fullmatch(parts[0])
    if bcp47:
        return bcp47.group("parts").replace("+", "-"), len(parts) == 1
    legacy = LEGACY_LOCALE_RE.fullmatch("-".join(parts[:2]))
    if legacy and legacy.group("region") is not None:
        return f"{legacy.group('language')}-{legacy.group('region')}", len(parts) == 2
    legacy = LEGACY_LOCALE_RE.fullmatch(parts[0])
    if legacy:
        return legacy.group("language"), len(parts) == 1
    return None


def locale_from_path(path: str) -> str | None:
    details = locale_path_details(path)
    return details[0] if details is not None else None


def parse_resource_file(path: str, content: bytes) -> tuple[dict[str, ResourceEntry], set[str]]:
    root = ET.fromstring(content)
    entries: dict[str, ResourceEntry] = {}
    duplicates: set[str] = set()
    for element in root:
        name = element.get("name")
        if not name:
            continue
        if name in entries:
            duplicates.add(name)

        element_tag = local_name(element.tag)
        items: list[tuple[str, str]] = []
        for index, child in enumerate(element):
            if local_name(child.tag) != "item":
                continue
            qualifier = child.get("quantity") or str(index)
            items.append((qualifier, "".join(child.itertext())))

        entries[name] = ResourceEntry(
            name=name,
            tag=element_tag,
            text="".join(element.itertext()),
            attributes=tuple(sorted((key, value) for key, value in element.attrib.items() if key != "name")),
            items=tuple(items),
        )
    return entries, duplicates


def parse_locale_config(content: bytes) -> set[str]:
    root = ET.fromstring(content)
    if local_name(root.tag) != "locale-config":
        raise ValueError("root element must be locale-config")
    tags: list[str] = []
    for element in root:
        if local_name(element.tag) != "locale":
            raise ValueError(f"unsupported locale config element: {local_name(element.tag)}")
        value = element.get(ANDROID_NAME)
        if not value:
            raise ValueError("locale entry is missing android:name")
        tags.append(value)
    if len(tags) != len(set(tags)):
        raise ValueError("duplicate locale entries")
    invalid = sorted(tag for tag in tags if not LOCALE_TAG_RE.fullmatch(tag))
    if invalid:
        raise ValueError(f"invalid locale tag(s): {', '.join(invalid)}")
    tag_set = set(tags)
    default_locale = root.get(ANDROID_DEFAULT_LOCALE)
    if default_locale is not None:
        if not LOCALE_TAG_RE.fullmatch(default_locale):
            raise ValueError(f"invalid default locale tag: {default_locale}")
        if default_locale not in tag_set:
            raise ValueError(f"default locale is not listed: {default_locale}")
    return tag_set


def load_snapshot(commit: str) -> Snapshot:
    files: dict[str, str] = {}
    entries: dict[str, dict[str, ResourceEntry]] = {}
    localized_files: dict[str, tuple[str, dict[str, ResourceEntry]]] = {}
    localized_duplicates: dict[str, set[str]] = {}
    parse_errors: dict[str, str] = {}
    blobs: dict[str, bytes] = {}

    paths = tree_paths(commit, RESOURCE_ROOT)
    for path in paths:
        locale_details = locale_path_details(path)
        if locale_details is None and path != LOCALE_CONFIG_PATH:
            continue
        content = read_blob(commit, path)
        blobs[path] = content
        if locale_details is None:
            continue
        locale, is_primary = locale_details
        try:
            parsed_entries, parsed_duplicates = parse_resource_file(path, content)
            localized_files[path] = (locale, parsed_entries)
            localized_duplicates[path] = parsed_duplicates
            if is_primary:
                if locale in files:
                    parse_errors[path] = f"multiple primary strings.xml files map to locale {locale}"
                else:
                    files[locale] = path
                    entries[locale] = parsed_entries
        except ET.ParseError as error:
            parse_errors[path] = f"invalid XML: {error}"

    locale_tags: set[str] = set()
    config_error: str | None = None
    config_content = blobs.get(LOCALE_CONFIG_PATH)
    if config_content is None:
        config_error = "locale config is missing"
    else:
        try:
            locale_tags = parse_locale_config(config_content)
        except (ET.ParseError, ValueError) as error:
            config_error = f"invalid locale config: {error}"

    return Snapshot(
        files=files,
        entries=entries,
        localized_files=localized_files,
        localized_duplicates=localized_duplicates,
        parse_errors=parse_errors,
        locale_tags=locale_tags,
        config_error=config_error,
        blobs=blobs,
    )


def placeholder_tokens(text: str) -> Counter[str]:
    return Counter(PRINTF_RE.findall(text) + BRACE_RE.findall(text))


def placeholder_mismatch(source: ResourceEntry, target: ResourceEntry) -> bool:
    if source.tag == "plurals" and target.tag == "plurals":
        source_items = dict(source.items)
        target_items = dict(target.items)
        source_other = source_items.get("other", "")
        if "other" not in target_items:
            return True
        return any(
            placeholder_tokens(source_items.get(quantity, source_other)) != placeholder_tokens(text)
            for quantity, text in target_items.items()
        )
    if source.tag in {"string-array", "integer-array"} and target.tag == source.tag:
        if len(source.items) != len(target.items):
            return True
        return any(
            placeholder_tokens(source_text) != placeholder_tokens(target_text)
            for (_, source_text), (_, target_text) in zip(source.items, target.items, strict=True)
        )
    return placeholder_tokens(source.text) != placeholder_tokens(target.text)


def snapshot_issues(snapshot: Snapshot) -> list[LocalizationIssue]:
    issues: list[LocalizationIssue] = []
    for path, message in snapshot.parse_errors.items():
        issues.append(LocalizationIssue("parse", path, locale_from_path(path), None, message))
    for path, names in snapshot.localized_duplicates.items():
        locale = snapshot.localized_files[path][0]
        for name in sorted(names):
            issues.append(
                LocalizationIssue(
                    "duplicate",
                    path,
                    locale,
                    name,
                    f"duplicate resource name: {name}",
                )
            )

    source = snapshot.entries.get("zh", {})
    source_names = set(source)
    source_path = snapshot.files.get("zh")
    for path, (locale, target) in sorted(snapshot.localized_files.items()):
        if path == source_path:
            continue
        for name in sorted(set(target) - source_names):
            issues.append(
                LocalizationIssue(
                    "extra",
                    path,
                    locale,
                    name,
                    f"resource is not present in the zh source: {name}",
                )
            )
        for name in sorted(source_names & set(target)):
            source_entry = source[name]
            target_entry = target[name]
            if source_entry.tag != target_entry.tag:
                issues.append(
                    LocalizationIssue(
                        "type",
                        path,
                        locale,
                        name,
                        f"resource type differs from zh ({source_entry.tag} vs {target_entry.tag}): {name}",
                    )
                )
            elif placeholder_mismatch(source_entry, target_entry):
                issues.append(
                    LocalizationIssue(
                        "placeholder",
                        path,
                        locale,
                        name,
                        f"placeholder structure differs from zh: {name}",
                    )
                )

    if snapshot.config_error:
        issues.append(
            LocalizationIssue("locale-config", LOCALE_CONFIG_PATH, None, None, snapshot.config_error)
        )
    else:
        resource_tags = {locale for locale, _ in snapshot.localized_files.values()}
        for tag in sorted(resource_tags - snapshot.locale_tags):
            issues.append(
                LocalizationIssue(
                    "locale-unregistered",
                    LOCALE_CONFIG_PATH,
                    tag,
                    None,
                    f"translation directory is not registered in locale config: {tag}",
                )
            )
        for tag in sorted(snapshot.locale_tags - resource_tags):
            issues.append(
                LocalizationIssue(
                    "locale-missing",
                    LOCALE_CONFIG_PATH,
                    tag,
                    None,
                    f"locale config entry has no strings.xml: {tag}",
                )
            )
    return issues


def touched_state(
    base: Snapshot,
    candidate: Snapshot,
) -> tuple[set[tuple[str, str]], set[str], set[str], set[str], set[str]]:
    touched_entries: set[tuple[str, str]] = set()
    touched_locales: set[str] = set()
    for path in set(base.localized_files) | set(candidate.localized_files):
        base_locale, base_entries = base.localized_files.get(path, (None, {}))
        candidate_locale, candidate_entries = candidate.localized_files.get(path, (None, {}))
        locale = candidate_locale or base_locale
        if base_locale != candidate_locale and locale is not None:
            touched_locales.add(locale)
        for key in set(base_entries) | set(candidate_entries):
            if base_entries.get(key) != candidate_entries.get(key):
                touched_entries.add((path, key))
                if locale is not None:
                    touched_locales.add(locale)

    base_source = base.entries.get("zh", {})
    candidate_source = candidate.entries.get("zh", {})
    source_touched = {
        key
        for key in set(base_source) | set(candidate_source)
        if base_source.get(key) != candidate_source.get(key)
    }
    touched_paths = {
        path
        for path in set(base.blobs) | set(candidate.blobs)
        if base.blobs.get(path) != candidate.blobs.get(path)
    }
    config_touched_tags = base.locale_tags ^ candidate.locale_tags
    return touched_entries, source_touched, touched_locales, touched_paths, config_touched_tags


def select_blocking_issues(base: Snapshot, candidate: Snapshot) -> tuple[list[LocalizationIssue], int]:
    base_issues = snapshot_issues(base)
    candidate_issues = snapshot_issues(candidate)
    base_identities = {issue.identity for issue in base_issues}
    (
        touched_entries,
        source_touched,
        touched_locales,
        touched_paths,
        config_touched_tags,
    ) = touched_state(base, candidate)

    blocking: list[LocalizationIssue] = []
    for issue in candidate_issues:
        is_touched = (
            issue.identity not in base_identities
            or (issue.key is not None and (issue.path, issue.key) in touched_entries)
            or (issue.key is not None and issue.key in source_touched)
            or (issue.key is None and issue.locale is not None and issue.locale in touched_locales)
            or issue.path in touched_paths and issue.code == "parse"
            or issue.locale in config_touched_tags and issue.code.startswith("locale-")
            or issue.code == "locale-config" and LOCALE_CONFIG_PATH in touched_paths
        )
        if is_touched:
            blocking.append(issue)

    return blocking, len(candidate_issues) - len(blocking)


def quality_notes(snapshot: Snapshot, existing_issue_count: int) -> list[str]:
    source = snapshot.entries.get("zh", {})
    source_names = set(source)
    missing_total = 0
    untranslated_total = 0
    han_total = 0
    for locale, entries in snapshot.entries.items():
        if locale == "zh":
            continue
        missing_total += len(source_names - set(entries))
        for name in source_names & set(entries):
            if source[name].text.strip() and source[name].text == entries[name].text:
                untranslated_total += 1
            if HAN_RE.search(entries[name].text):
                han_total += 1
    return [
        f"Languages: {len(snapshot.entries)}; missing translations: {missing_total}; identical to zh: {untranslated_total}.",
        f"Non-zh values containing Han characters: {han_total}.",
        f"Existing localization diagnostic(s) outside this candidate: {existing_issue_count}.",
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--candidate", required=True)
    args = parser.parse_args()

    base = load_snapshot(args.base)
    candidate = load_snapshot(args.candidate)
    blocking, existing_count = select_blocking_issues(base, candidate)
    errors = [
        Diagnostic(code=issue.code, path=issue.path, message=issue.message)
        for issue in blocking
    ]
    return report(
        "Localization",
        errors,
        notes=quality_notes(candidate, existing_count),
    )


if __name__ == "__main__":
    raise SystemExit(main())
