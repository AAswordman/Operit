#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
使用 zh strings.xml 作为基准，自动补全其它语言缺失的字符串。

默认行为：
- 扫描 app/src/main/res 下已有的语言目录（values-en / values-es / values-pt-rBR ...）
- 删除目标语言中源文件已不存在的 <string>
- 翻译并追加缺失的 <string>，不覆盖已有翻译
- 复用 tools/github/.env 中的 OpenAI 兼容接口配置

示例：
    python tools/string/fill_missing_translations.py --report-only
    python tools/string/fill_missing_translations.py
    python tools/string/fill_missing_translations.py --targets all,ms,id
    python tools/string/fill_missing_translations.py --targets ms,id --dry-run --limit 20
"""

import argparse
import concurrent.futures
import json
import os
import re
import sys
import textwrap
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

if sys.platform == "win32":
    import io

    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8")


PRINTF_PLACEHOLDER_RE = re.compile(
    r"%(?:\d+\$)?[-#+ 0,(<]*(?:\d+)?(?:\.\d+)?(?:[tT])?[a-zA-Z]"
)
BRACE_PLACEHOLDER_RE = re.compile(r"\{[A-Za-z0-9_]+\}")
ESCAPE_SEQUENCE_RE = re.compile(r"\\[ntr'\"@?]")
HTML_TAG_RE = re.compile(r"</?[^>]+?>")
THINK_BLOCK_RE = re.compile(r"<think>.*?</think>", re.DOTALL | re.IGNORECASE)
CODE_FENCE_RE = re.compile(r"^```(?:json)?\s*|\s*```$", re.IGNORECASE)
LANG_DIR_RE = re.compile(r"^values-([a-z]{2,3})(?:-r([A-Z0-9]{2,3}))?$")
PAIRED_SINGLE_QUOTE_RE = re.compile(r"(?<![\\\w])'([^'\n]+)'(?!\w)")

LANGUAGE_NAME_MAP = {
    "zh": "Simplified Chinese",
    "en": "English",
    "ko": "Korean",
    "es": "Spanish",
    "pt-BR": "Portuguese (Brazil)",
    "ms": "Malay",
    "id": "Indonesian",
}

LANGUAGE_DIR_OVERRIDES = {
    "zh": "values",
    "en": "values-en",
    "ko": "values-ko",
    "es": "values-es",
    "pt-BR": "values-pt-rBR",
    "pt-br": "values-pt-rBR",
    "ms": "values-ms",
    "id": "values-in",
    "in": "values-in",
}


@dataclass
class StringEntry:
    name: str
    text: str
    attrs: dict[str, str]


@dataclass
class TargetSpec:
    code: str
    dir_name: str
    file_path: Path

    @property
    def display_name(self) -> str:
        return LANGUAGE_NAME_MAP.get(self.code, self.code)


def _repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def _log(message: str) -> None:
    print(message, flush=True)


def _load_env(env_path: Path) -> None:
    if not env_path.exists():
        return
    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


def _openai_chat_completion(base_url: str, api_key: str, payload: dict) -> dict:
    url = base_url.rstrip("/") + "/v1/chat/completions"
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")

    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", f"Bearer {api_key}")

    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = ""
        try:
            body = e.read().decode("utf-8", errors="replace")
        except Exception:
            pass
        raise RuntimeError(f"AI request failed: HTTP {e.code} {e.reason}\n{body}") from e


def _extract_message_content(resp: dict) -> str:
    try:
        content = resp["choices"][0]["message"]["content"]
    except Exception as e:
        preview = json.dumps(resp, ensure_ascii=False)[:2000]
        raise RuntimeError(f"Unexpected AI response: {e}\n{preview}") from e
    if not isinstance(content, str):
        raise RuntimeError("AI response content is not a string.")
    content = THINK_BLOCK_RE.sub("", content).strip()
    content = CODE_FENCE_RE.sub("", content).strip()
    return content


def _extract_json_object(content: str) -> dict[str, str]:
    if not content:
        raise RuntimeError("AI returned empty content.")

    candidates = [content]
    start = content.find("{")
    end = content.rfind("}")
    if start >= 0 and end > start:
        candidates.append(content[start : end + 1])

    last_error: Optional[Exception] = None
    for candidate in candidates:
        try:
            obj = json.loads(candidate)
        except Exception as e:
            last_error = e
            continue
        if not isinstance(obj, dict):
            raise RuntimeError("AI response JSON must be an object mapping key -> translated text.")
        normalized: dict[str, str] = {}
        for key, value in obj.items():
            if not isinstance(key, str) or not isinstance(value, str):
                raise RuntimeError("AI response JSON must contain only string keys and string values.")
            normalized[key] = value
        return normalized

    raise RuntimeError(f"Failed to parse AI response as JSON object: {last_error}")


def _parse_strings_file(file_path: Path) -> list[StringEntry]:
    tree = ET.parse(file_path)
    root = tree.getroot()
    if root.tag != "resources":
        raise RuntimeError(f"Invalid root tag in {file_path}: {root.tag}")

    entries: list[StringEntry] = []
    for element in root.findall("string"):
        name = element.get("name")
        if not name:
            continue
        attrs = dict(element.attrib)
        text = element.text or ""
        entries.append(StringEntry(name=name, text=text, attrs=attrs))
    return entries


def _parse_string_names(file_path: Path) -> set[str]:
    if not file_path.exists():
        return set()
    return {entry.name for entry in _parse_strings_file(file_path)}


def _remove_stale_strings(file_path: Path, stale_keys: set[str]) -> int:
    if not file_path.exists() or not stale_keys:
        return 0

    original = file_path.read_text(encoding="utf-8")
    removed: set[str] = set()

    def remove_if_stale(match: re.Match[str]) -> str:
        attrs = match.group(1)
        name_match = re.search(r'\bname\s*=\s*"([^"]+)"', attrs)
        if name_match and name_match.group(1) in stale_keys:
            removed.add(name_match.group(1))
            return ""
        return match.group(0)

    string_pattern = re.compile(
        r'^[ \t]*<string(?=\s|>|/)([^>]*)>.*?</string>[ \t]*(?:\r?\n)?',
        re.MULTILINE | re.DOTALL,
    )
    updated = string_pattern.sub(remove_if_stale, original)

    empty_string_pattern = re.compile(
        r'^[ \t]*<string(?=\s|>|/)([^>]*)/\s*>[ \t]*(?:\r?\n)?',
        re.MULTILINE,
    )
    updated = empty_string_pattern.sub(remove_if_stale, updated)

    if removed:
        file_path.write_text(updated, encoding="utf-8")
    return len(removed)


def _split_csv(value: str) -> list[str]:
    return [item.strip() for item in value.split(",") if item.strip()]


def _dir_to_language_code(dir_name: str) -> Optional[str]:
    if dir_name == "values":
        return "zh"
    match = LANG_DIR_RE.fullmatch(dir_name)
    if not match:
        return None
    language = match.group(1).lower()
    region = match.group(2)
    if language == "in":
        language = "id"
    if region:
        return f"{language}-{region.upper()}"
    return language


def _language_code_to_dir(language_code: str) -> str:
    normalized = language_code.strip()
    if not normalized:
        raise ValueError("Language code is empty.")

    override = LANGUAGE_DIR_OVERRIDES.get(normalized) or LANGUAGE_DIR_OVERRIDES.get(normalized.lower())
    if override:
        return override

    normalized = normalized.replace("_", "-")
    parts = normalized.split("-")
    language = parts[0].lower()
    if not re.fullmatch(r"[a-z]{2,3}", language):
        raise ValueError(f"Unsupported language code: {language_code}")

    if len(parts) == 1:
        return f"values-{language}"

    if len(parts) == 2 and re.fullmatch(r"[A-Za-z0-9]{2,3}", parts[1]):
        return f"values-{language}-r{parts[1].upper()}"

    raise ValueError(f"Unsupported language code: {language_code}")


def _discover_existing_targets(res_dir: Path) -> list[TargetSpec]:
    targets: list[TargetSpec] = []
    for child in sorted(res_dir.iterdir(), key=lambda p: p.name):
        if not child.is_dir():
            continue
        code = _dir_to_language_code(child.name)
        if code in (None, "zh"):
            continue
        file_path = child / "strings.xml"
        if not file_path.exists():
            continue
        targets.append(TargetSpec(code=code, dir_name=child.name, file_path=file_path))
    return targets


def _resolve_targets(res_dir: Path, raw_targets: str) -> list[TargetSpec]:
    resolved: dict[str, TargetSpec] = {}

    def add_target_from_dir(dir_name: str) -> None:
        code = _dir_to_language_code(dir_name)
        if code is None:
            raise RuntimeError(f"Unsupported resource directory: {dir_name}")
        if code == "zh":
            return
        resolved[code] = TargetSpec(
            code=code,
            dir_name=dir_name,
            file_path=res_dir / dir_name / "strings.xml",
        )

    if not raw_targets.strip():
        for target in _discover_existing_targets(res_dir):
            resolved[target.code] = target
        return list(resolved.values())

    tokens = _split_csv(raw_targets)
    for token in tokens:
        if token.lower() == "all":
            for target in _discover_existing_targets(res_dir):
                resolved[target.code] = target
            continue

        if token.startswith("values"):
            add_target_from_dir(token)
            continue

        add_target_from_dir(_language_code_to_dir(token))

    return list(resolved.values())


def _counter_for_pattern(pattern: re.Pattern[str], text: str) -> Counter[str]:
    return Counter(pattern.findall(text or ""))


def _preserve_escape_style(source_text: str, translated_text: str) -> str:
    adjusted = translated_text
    source_angle_tokens = re.findall(r"<[^>]+>", source_text)
    translated_angle_tokens = re.findall(r"<[^>]+>", adjusted)

    if source_angle_tokens and len(source_angle_tokens) == len(translated_angle_tokens):
        token_iter = iter(source_angle_tokens)
        adjusted = re.sub(r"<[^>]+>", lambda _: next(token_iter), adjusted)

    if "\n" in source_text and "\\n" not in source_text and "\\n" in adjusted:
        adjusted = adjusted.replace("\\n", "\n")

    if "\t" in source_text and "\\t" not in source_text and "\\t" in adjusted:
        adjusted = adjusted.replace("\\t", "\t")

    if "\r" in source_text and "\\r" not in source_text and "\\r" in adjusted:
        adjusted = adjusted.replace("\\r", "\r")

    if "\\n" in source_text and "\\n" not in adjusted and "\n" in adjusted:
        adjusted = adjusted.replace("\n", "\\n")

    if "\\t" in source_text and "\\t" not in adjusted and "\t" in adjusted:
        adjusted = adjusted.replace("\t", "\\t")

    if "\\r" in source_text and "\\r" not in adjusted and "\r" in adjusted:
        adjusted = adjusted.replace("\r", "\\r")

    if '\\"' in source_text and '\\"' not in adjusted and '"' in adjusted:
        adjusted = re.sub(r'(?<!\\)"', r'\\"', adjusted)

    if "\\'" in source_text and "\\'" not in adjusted and "'" in adjusted:
        adjusted = re.sub(r"(?<!\\)'", r"\\'", adjusted)

    return adjusted


def _normalize_android_safe_quotes(source_text: str, translated_text: str) -> str:
    adjusted = translated_text
    if "'" not in source_text and "\\'" not in source_text:
        adjusted = PAIRED_SINGLE_QUOTE_RE.sub(r'"\1"', adjusted)
    return adjusted


def _validate_translation(source_text: str, translated_text: str, key: str) -> None:
    if not translated_text.strip():
        raise RuntimeError(f"{key}: translated text is empty")

    checks = [
        ("printf placeholders", PRINTF_PLACEHOLDER_RE),
        ("brace placeholders", BRACE_PLACEHOLDER_RE),
        ("escape sequences", ESCAPE_SEQUENCE_RE),
        ("HTML tags", HTML_TAG_RE),
    ]

    for label, pattern in checks:
        source_counter = _counter_for_pattern(pattern, source_text)
        translated_counter = _counter_for_pattern(pattern, translated_text)
        if source_counter != translated_counter:
            raise RuntimeError(
                f"{key}: {label} mismatch\n"
                f"source={dict(source_counter)}\n"
                f"translated={dict(translated_counter)}"
            )


def _build_translation_prompts(target: TargetSpec, batch: list[StringEntry]) -> tuple[str, str]:
    batch_json = json.dumps(
        [{"name": entry.name, "text": entry.text} for entry in batch],
        ensure_ascii=False,
        indent=2,
    )

    system_prompt = textwrap.dedent(
        f"""
        Translate Android app string resources from Simplified Chinese to {target.display_name}.

        Rules:
        - Return JSON only: one object mapping string key -> translated string.
        - Preserve every key exactly.
        - Preserve placeholders exactly, including forms like %1$s, %d, %1$.1f and {{text}}.
        - Preserve HTML/XML tags exactly, including attributes and tag names.
        - Preserve any angle-bracket wrapped literal tokens exactly, such as <空>, <null>, <think> and similar forms.
        - Preserve escape sequences exactly, including \\n, \\t, \\" and \\' when present.
        - If the source contains backslashes or escaped quotes such as \\" or \\\\, keep those characters exactly as-is in the output.
        - Preserve markdown markers, backticks, URLs, product names, code identifiers and API names unless translation is obviously needed.
        - Keep the translation natural for UI text in {target.display_name}.
        - Do not add comments, explanations, code fences or extra keys.
        """
    ).strip()

    user_prompt = textwrap.dedent(
        f"""
        Translate the following Android strings into {target.display_name}.
        Output JSON object only.

        Input:
        {batch_json}
        """
    ).strip()

    return system_prompt, user_prompt


def _translate_batch(
    *,
    base_url: str,
    api_key: str,
    model: str,
    temperature: float,
    target: TargetSpec,
    batch: list[StringEntry],
) -> dict[str, str]:
    system_prompt, user_prompt = _build_translation_prompts(target, batch)
    payload = {
        "model": model,
        "temperature": temperature,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
    }
    response = _openai_chat_completion(base_url=base_url, api_key=api_key, payload=payload)
    content = _extract_message_content(response)
    translated = _extract_json_object(content)

    expected_keys = [entry.name for entry in batch]
    missing_keys = [key for key in expected_keys if key not in translated]
    extra_keys = [key for key in translated.keys() if key not in expected_keys]
    if missing_keys or extra_keys:
        raise RuntimeError(
            f"{target.code}: AI returned unexpected keys. missing={missing_keys[:10]} extra={extra_keys[:10]}"
        )

    source_map = {entry.name: entry.text for entry in batch}
    for key in expected_keys:
        translated[key] = _preserve_escape_style(source_map[key], translated[key])
        translated[key] = _normalize_android_safe_quotes(source_map[key], translated[key])
        _validate_translation(source_map[key], translated[key], key)
    return translated


def _serialize_string_element(entry: StringEntry, translated_text: str) -> str:
    attrs = {"name": entry.name}
    for key, value in entry.attrs.items():
        if key == "name":
            continue
        attrs[key] = value
    element = ET.Element("string", attrs)
    element.text = translated_text
    return ET.tostring(element, encoding="unicode", short_empty_elements=False)


def _append_missing_strings(file_path: Path, rendered_lines: list[str]) -> None:
    file_path.parent.mkdir(parents=True, exist_ok=True)
    if file_path.exists():
        original = file_path.read_text(encoding="utf-8")
        closing_tag = "</resources>"
        index = original.rfind(closing_tag)
        if index < 0:
            raise RuntimeError(f"Invalid XML file, missing </resources>: {file_path}")

        prefix = original[:index].rstrip()
        suffix = original[index:]
        updated = prefix + "\n\n" + "\n".join(rendered_lines) + "\n" + suffix
        file_path.write_text(updated, encoding="utf-8")
        return

    content = (
        "<?xml version='1.0' encoding='utf-8'?>\n"
        "<resources>\n"
        + "\n".join(rendered_lines)
        + "\n</resources>\n"
    )
    file_path.write_text(content, encoding="utf-8")


def _chunked(items: list[StringEntry], batch_size: int) -> list[list[StringEntry]]:
    return [items[i : i + batch_size] for i in range(0, len(items), batch_size)]


def _load_source_entries(source_file: Path) -> list[StringEntry]:
    entries = _parse_strings_file(source_file)
    deduped: list[StringEntry] = []
    seen: set[str] = set()
    for entry in entries:
        if entry.name in seen:
            raise RuntimeError(f"Duplicate key found in source file: {entry.name}")
        seen.add(entry.name)
        if entry.attrs.get("translatable") == "false":
            continue
        deduped.append(entry)
    return deduped


def _translate_and_render_batch(
    *,
    index: int,
    base_url: str,
    api_key: str,
    model: str,
    temperature: float,
    target: TargetSpec,
    batch: list[StringEntry],
) -> tuple[int, list[StringEntry], list[str], dict[str, str]]:
    translated = _translate_batch(
        base_url=base_url,
        api_key=api_key,
        model=model,
        temperature=temperature,
        target=target,
        batch=batch,
    )

    rendered_lines = []
    for entry in batch:
        translated_text = translated.get(entry.name)
        if translated_text is None:
            raise RuntimeError(f"{target.code}: missing translated text for {entry.name}")
        rendered_lines.append("    " + _serialize_string_element(entry, translated_text))

    return index, batch, rendered_lines, translated


def main() -> int:
    repo_root = _repo_root()
    default_res_dir = repo_root / "app" / "src" / "main" / "res"
    default_source = default_res_dir / "values" / "strings.xml"
    default_env = repo_root / "tools" / "github" / ".env"

    parser = argparse.ArgumentParser(
        prog="fill_missing_translations.py",
        description="Use zh strings.xml as the baseline and fill missing translations for other languages.",
    )
    parser.add_argument("--env", default=str(default_env), help="Path to .env file")
    parser.add_argument("--res-dir", default=str(default_res_dir), help="Android res directory")
    parser.add_argument("--source-file", default=str(default_source), help="Source zh strings.xml")
    parser.add_argument(
        "--targets",
        default="",
        help="Comma-separated language codes or values-* dirs. Default: auto-discover existing language dirs. Use all,ms,id to include new languages.",
    )
    parser.add_argument("--batch-size", type=int, default=80, help="Strings per AI request")
    parser.add_argument(
        "--concurrency",
        type=int,
        default=12,
        help="Number of concurrent AI batch requests per target language",
    )
    parser.add_argument("--limit", type=int, default=0, help="Max missing strings per target to process")
    parser.add_argument("--dry-run", action="store_true", help="Translate but do not write files")
    parser.add_argument("--report-only", action="store_true", help="Only print missing counts, do not call AI")
    parser.add_argument("--model", default="", help="Override AI model from env")
    parser.add_argument(
        "--temperature",
        type=float,
        default=None,
        help="Override AI temperature from env",
    )
    args = parser.parse_args()

    if args.batch_size <= 0:
        print("[X] --batch-size must be > 0", file=sys.stderr)
        return 2
    if args.concurrency <= 0:
        print("[X] --concurrency must be > 0", file=sys.stderr)
        return 2
    if args.limit < 0:
        print("[X] --limit must be >= 0", file=sys.stderr)
        return 2

    res_dir = Path(args.res_dir).resolve()
    source_file = Path(args.source_file).resolve()
    if not source_file.exists():
        print(f"[X] source file not found: {source_file}", file=sys.stderr)
        return 2
    if not res_dir.exists():
        print(f"[X] res directory not found: {res_dir}", file=sys.stderr)
        return 2

    try:
        source_entries = _load_source_entries(source_file)
        targets = _resolve_targets(res_dir, args.targets)
    except Exception as e:
        print(f"[X] {e}", file=sys.stderr)
        return 2

    if not targets:
        print("[X] no target languages found", file=sys.stderr)
        return 2

    source_key_set = {entry.name for entry in source_entries}
    target_missing: list[tuple[TargetSpec, list[StringEntry]]] = []
    target_stale: list[tuple[TargetSpec, set[str]]] = []
    for target in targets:
        target_keys = _parse_string_names(target.file_path)
        stale = target_keys - source_key_set
        if stale and not args.dry_run and not args.report_only:
            removed_count = _remove_stale_strings(target.file_path, stale)
            if removed_count != len(stale):
                raise RuntimeError(
                    f"{target.code}: expected to remove {len(stale)} stale strings, removed {removed_count}"
                )
            target_keys -= stale
        missing = [entry for entry in source_entries if entry.name not in target_keys]
        if args.limit:
            missing = missing[: args.limit]
        target_missing.append((target, missing))
        target_stale.append((target, stale))

    _log("Translation sync summary")
    _log("=" * 72)
    total_missing = 0
    total_stale = 0
    stale_by_target = {target.file_path: stale for target, stale in target_stale}
    for target, missing in target_missing:
        exists_mark = "existing" if target.file_path.exists() else "new"
        stale = stale_by_target[target.file_path]
        action = "stale" if args.dry_run or args.report_only else "removed"
        _log(
            f"{target.code:<8} {target.dir_name:<18} {len(missing):>5} missing "
            f"{len(stale):>5} {action} ({exists_mark})"
        )
        total_missing += len(missing)
        total_stale += len(stale)
    _log("-" * 72)
    _log(f"Total missing strings: {total_missing}")
    _log(f"Total stale strings: {total_stale}")

    if args.report_only or total_missing == 0:
        return 0

    _load_env(Path(args.env))
    base_url = os.environ.get("AI_BASE_URL", "").strip()
    api_key = os.environ.get("AI_API_KEY", "").strip()
    model = (args.model or os.environ.get("AI_MODEL", "") or "gpt-4o-mini").strip()
    temperature = args.temperature
    if temperature is None:
        temperature = float(os.environ.get("AI_TEMPERATURE", "0.2") or 0.2)

    if not base_url:
        print("[X] Missing AI_BASE_URL in env.", file=sys.stderr)
        return 2
    if not api_key:
        print("[X] Missing AI_API_KEY in env.", file=sys.stderr)
        return 2

    had_failures = False

    for target, missing_entries in target_missing:
        if not missing_entries:
            continue

        _log(f"\n[LANG] {target.code} -> {target.file_path}")
        batches = _chunked(missing_entries, args.batch_size)
        worker_count = min(args.concurrency, len(batches))
        _log(f"[AI] rolling submit {len(batches)} batches with concurrency {worker_count}")
        completed_batches = 0
        completed_strings = 0
        failed_batches: list[int] = []
        stop_submitting = False

        with concurrent.futures.ThreadPoolExecutor(max_workers=worker_count) as executor:
            active_futures: dict[concurrent.futures.Future, tuple[int, int]] = {}
            next_batch_index = 1

            def submit_batch(batch_index: int) -> None:
                batch = batches[batch_index - 1]
                _log(
                    f"[QUEUE] {target.code}: batch {batch_index}/{len(batches)} queued ({len(batch)} strings)"
                )
                future = executor.submit(
                    _translate_and_render_batch,
                    index=batch_index,
                    base_url=base_url,
                    api_key=api_key,
                    model=model,
                    temperature=temperature,
                    target=target,
                    batch=batch,
                )
                active_futures[future] = (batch_index, len(batch))

            while next_batch_index <= len(batches) and len(active_futures) < worker_count:
                submit_batch(next_batch_index)
                next_batch_index += 1

            while active_futures:
                done, _ = concurrent.futures.wait(
                    active_futures.keys(),
                    return_when=concurrent.futures.FIRST_COMPLETED,
                )

                for future in done:
                    queued_index, queued_size = active_futures.pop(future)
                    try:
                        batch_index, batch, rendered_lines, translated = future.result()
                        completed_batches += 1
                        completed_strings += len(batch)

                        if args.dry_run:
                            sample_key = batch[0].name
                            _log(
                                f"[DRY-RUN] {target.code}: batch {batch_index}/{len(batches)} prepared {len(rendered_lines)} strings"
                            )
                            _log(f"[DRY-RUN] sample: {sample_key} = {translated[sample_key]}")
                        else:
                            _append_missing_strings(target.file_path, rendered_lines)
                            _log(
                                f"[WRITE] {target.code}: batch {batch_index}/{len(batches)} appended {len(rendered_lines)} strings"
                            )
                    except Exception as e:
                        failed_batches.append(queued_index)
                        had_failures = True
                        stop_submitting = True
                        _log(
                            f"[FAIL] {target.code}: batch {queued_index}/{len(batches)} failed ({queued_size} strings): {e}"
                        )

                while (
                    not stop_submitting
                    and next_batch_index <= len(batches)
                    and len(active_futures) < worker_count
                ):
                    submit_batch(next_batch_index)
                    next_batch_index += 1

        if failed_batches:
            _log(
                f"[SUMMARY] {target.code}: completed {completed_batches}/{len(batches)} batches, "
                f"wrote {completed_strings} strings, failed batches={failed_batches[:20]}"
            )
            if len(failed_batches) > 20:
                _log(f"[SUMMARY] {target.code}: ... and {len(failed_batches) - 20} more failed batches")
        else:
            _log(
                f"[SUMMARY] {target.code}: completed all {completed_batches} batches, wrote {completed_strings} strings"
            )

    if had_failures:
        _log("\n[WARNING] finished with failed batches; rerun will continue from remaining missing strings")
        return 1

    _log("\n[OK] done")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
