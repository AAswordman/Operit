#!/usr/bin/env python3
"""Generate compose_dsl Kotlin registry + TypeScript material3/foundation bindings.

Source of truth: Compose Material3/Foundation source jars in Gradle cache.
"""

from __future__ import annotations

import argparse
import glob
import re
import textwrap
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple


ROOT = Path(__file__).resolve().parents[2]
SCRIPT_DIR = Path(__file__).resolve().parent

MATERIAL3_GROUP = Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1" / "androidx.compose.material3"
FOUNDATION_GROUP = Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1" / "androidx.compose.foundation"


@dataclass(frozen=True)
class ComponentSpec:
    dsl_name: str
    source: str
    package_name: str
    function_name: str
    preferred_params: Tuple[str, ...]


COMPONENTS: Tuple[ComponentSpec, ...] = (
    ComponentSpec("Column", "foundation-layout", "androidx.compose.foundation.layout", "Column", ("modifier", "verticalArrangement", "horizontalAlignment", "content")),
    ComponentSpec("Row", "foundation-layout", "androidx.compose.foundation.layout", "Row", ("modifier", "horizontalArrangement", "verticalAlignment", "content")),
    ComponentSpec("Box", "foundation-layout", "androidx.compose.foundation.layout", "Box", ("modifier", "contentAlignment", "content")),
    ComponentSpec("Spacer", "foundation-layout", "androidx.compose.foundation.layout", "Spacer", ("modifier",)),
    ComponentSpec("LazyColumn", "foundation", "androidx.compose.foundation.lazy", "LazyColumn", ("modifier", "verticalArrangement", "contentPadding", "content")),
    ComponentSpec("LazyRow", "foundation", "androidx.compose.foundation.lazy", "LazyRow", ("modifier", "horizontalArrangement", "contentPadding", "content")),
    ComponentSpec("Text", "material3", "androidx.compose.material3", "Text", ("text", "modifier", "color", "style", "maxLines")),
    ComponentSpec("TextField", "material3", "androidx.compose.material3", "OutlinedTextField", ("value", "onValueChange", "modifier", "label", "placeholder", "singleLine", "minLines", "visualTransformation")),
    ComponentSpec("Switch", "material3", "androidx.compose.material3", "Switch", ("checked", "onCheckedChange", "modifier", "enabled")),
    ComponentSpec("Checkbox", "material3", "androidx.compose.material3", "Checkbox", ("checked", "onCheckedChange", "modifier", "enabled")),
    ComponentSpec("Button", "material3", "androidx.compose.material3", "Button", ("onClick", "modifier", "enabled", "shape", "content")),
    ComponentSpec("IconButton", "material3", "androidx.compose.material3", "IconButton", ("onClick", "modifier", "enabled", "content")),
    ComponentSpec("Card", "material3", "androidx.compose.material3", "Card", ("modifier", "shape", "colors", "elevation", "border", "content")),
    ComponentSpec("MaterialTheme", "material3", "androidx.compose.material3", "MaterialTheme", ("colorScheme", "shapes", "typography", "content")),
    ComponentSpec("Surface", "material3", "androidx.compose.material3", "Surface", ("modifier", "shape", "color", "contentColor", "content")),
    ComponentSpec("Icon", "material3", "androidx.compose.material3", "Icon", ("imageVector", "contentDescription", "modifier", "tint")),
    ComponentSpec("LinearProgressIndicator", "material3", "androidx.compose.material3", "LinearProgressIndicator", ("progress", "modifier", "color")),
    ComponentSpec("CircularProgressIndicator", "material3", "androidx.compose.material3", "CircularProgressIndicator", ("modifier", "strokeWidth", "color")),
    ComponentSpec("SnackbarHost", "material3", "androidx.compose.material3", "SnackbarHost", ("hostState", "modifier", "snackbar")),
)


@dataclass
class Param:
    name: str
    type: str
    has_default: bool


def _pick_latest(pattern: str) -> Optional[Path]:
    candidates = [Path(p) for p in glob.glob(pattern, recursive=True)]
    if not candidates:
        return None
    candidates.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    return candidates[0]


def resolve_source_jars() -> Dict[str, Path]:
    material3 = _pick_latest(str(MATERIAL3_GROUP / "material3-android" / "*" / "*" / "material3-android-*-sources.jar"))
    foundation_layout = _pick_latest(str(FOUNDATION_GROUP / "foundation-layout-android" / "*" / "*" / "foundation-layout-android-*-sources.jar"))
    foundation = _pick_latest(str(FOUNDATION_GROUP / "foundation-android" / "*" / "*" / "foundation-android-*-sources.jar"))

    missing = [name for name, path in {"material3": material3, "foundation-layout": foundation_layout, "foundation": foundation}.items() if path is None]
    if missing:
        raise RuntimeError(f"missing Compose source jars: {', '.join(missing)}")

    return {
        "material3": material3,
        "foundation-layout": foundation_layout,
        "foundation": foundation,
    }


def parse_composable_signatures(source_text: str, function_name: str) -> List[List[Param]]:
    signatures: List[List[Param]] = []
    fun_pattern = re.compile(r"\bfun\s+" + re.escape(function_name) + r"\s*\(")

    for match in fun_pattern.finditer(source_text):
        start = match.start()
        if not has_attached_annotation(source_text, start, "Composable"):
            continue

        open_paren = source_text.find("(", start)
        if open_paren < 0:
            continue

        depth = 0
        i = open_paren
        while i < len(source_text):
            ch = source_text[i]
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        if i >= len(source_text):
            continue

        raw_params = source_text[open_paren + 1:i]
        parsed = parse_params(raw_params)
        signatures.append(parsed)

    return signatures


def extract_package_name(source_text: str) -> Optional[str]:
    match = re.search(r"^\s*package\s+([A-Za-z0-9_.]+)", source_text, re.MULTILINE)
    if match is None:
        return None
    return match.group(1).strip()


def parse_params(raw_params: str) -> List[Param]:
    tokens: List[str] = []
    current: List[str] = []
    depth_angle = depth_round = depth_square = depth_curly = 0

    for ch in raw_params:
        if ch == "<":
            depth_angle += 1
        elif ch == ">":
            depth_angle = max(0, depth_angle - 1)
        elif ch == "(":
            depth_round += 1
        elif ch == ")":
            depth_round = max(0, depth_round - 1)
        elif ch == "[":
            depth_square += 1
        elif ch == "]":
            depth_square = max(0, depth_square - 1)
        elif ch == "{":
            depth_curly += 1
        elif ch == "}":
            depth_curly = max(0, depth_curly - 1)

        if ch == "," and depth_angle == 0 and depth_round == 0 and depth_square == 0 and depth_curly == 0:
            token = "".join(current).strip()
            if token:
                tokens.append(token)
            current = []
            continue

        current.append(ch)

    tail = "".join(current).strip()
    if tail:
        tokens.append(tail)

    parsed: List[Param] = []
    for token in tokens:
        cleaned = re.sub(r"@\w+(?:\([^)]*\))?", "", token)
        cleaned = re.sub(r"\b(noinline|crossinline|vararg)\b", "", cleaned)
        cleaned = re.sub(r"\s+", " ", cleaned).strip()
        if ":" not in cleaned:
            continue

        name, rest = cleaned.split(":", 1)
        name = name.strip()
        has_default = "=" in rest
        type_part = rest.split("=", 1)[0].strip()

        if not name or not type_part:
            continue

        parsed.append(Param(name=name, type=type_part, has_default=has_default))

    return parsed


def extract_attached_annotation_lines(source_text: str, fun_start: int) -> List[str]:
    annotations: List[str] = []
    cursor = source_text.rfind("\n", 0, fun_start) + 1

    while cursor > 0:
        previous_line_end = cursor - 1
        if previous_line_end < 0:
            break
        previous_line_start = source_text.rfind("\n", 0, previous_line_end) + 1
        line = source_text[previous_line_start:previous_line_end].strip()
        if not line:
            break
        if not line.startswith("@"):
            break
        annotations.append(line)
        cursor = previous_line_start

    annotations.reverse()
    return annotations


def has_attached_annotation(source_text: str, fun_start: int, annotation_name: str) -> bool:
    token = f"@{annotation_name}"
    for line in extract_attached_annotation_lines(source_text, fun_start):
        if line.startswith(token) or token in line:
            return True
    return False


def has_attached_experimental_annotation(source_text: str, fun_start: int) -> bool:
    for line in extract_attached_annotation_lines(source_text, fun_start):
        if (
            "ExperimentalMaterial3Api" in line
            or "ExperimentalMaterial3ExpressiveApi" in line
            or "ExperimentalFoundationApi" in line
            or re.search(r"@OptIn\([^)]*Experimental", line) is not None
        ):
            return True
    return False


def has_explicit_non_unit_return_type(source_text: str, close_paren: int) -> bool:
    tail = source_text[close_paren + 1:close_paren + 300]
    match = re.match(r"\s*:\s*([^={]+?)\s*(?:=|\{)", tail, re.DOTALL)
    if match is None:
        return False
    return_type = " ".join(match.group(1).split()).rstrip("?")
    return return_type not in {"Unit", "kotlin.Unit"}


def choose_overload(spec: ComponentSpec, overloads: Sequence[List[Param]]) -> List[Param]:
    if not overloads:
        return []

    preferred = set(spec.preferred_params)

    def score(params: List[Param]) -> int:
        names = {p.name for p in params}
        score_value = 0
        if spec.preferred_params:
            for idx, name in enumerate(spec.preferred_params):
                if name in names:
                    score_value += 50 - idx
            score_value += len(names & preferred) * 5
            if "content" in names:
                score_value += 8
        else:
            if "modifier" in names:
                score_value += 20
            if "content" in names:
                score_value += 10
            if any(p.name.startswith("on") and "->" in p.type for p in params):
                score_value -= 5
        score_value -= len(params)
        return score_value

    return max(overloads, key=score)


def load_component_params(jars: Dict[str, Path], components: Sequence[ComponentSpec]) -> Dict[str, List[Param]]:
    resolved: Dict[str, List[Param]] = {}

    for spec in components:
        jar_path = jars[spec.source]
        matches: List[List[Param]] = []
        with zipfile.ZipFile(jar_path) as jar:
            for entry in jar.namelist():
                if not entry.endswith(".kt"):
                    continue
                text = jar.read(entry).decode("utf-8", errors="ignore")
                package_name = extract_package_name(text)
                if package_name != spec.package_name:
                    continue
                if f"fun {spec.function_name}(" not in text:
                    continue
                matches.extend(parse_composable_signatures(text, spec.function_name))

        resolved[spec.dsl_name] = choose_overload(spec, matches)

    return resolved


def map_param_to_ts(component: str, param: Param) -> Optional[Tuple[str, str, bool]]:
    name = param.name
    type_name = param.type

    if name in {"modifier", "interactionSource", "colors", "elevation", "shape", "border", "contentPadding", "hostState", "snackbar", "content", "label", "placeholder", "prefix", "suffix", "leadingIcon", "trailingIcon", "supportingText", "visualTransformation", "keyboardOptions", "keyboardActions", "singleLine", "minLines", "maxLines", "maxLength", "enabled", "isError", "readOnly", "lineLimits", "scrollState"}:
        pass

    if "->" in type_name:
        arg_type = parse_lambda_single_arg_type(type_name)
        arg_ts = map_lambda_arg_to_ts(arg_type)
        if is_action_lambda_param(param):
            if is_zero_arg_unit_lambda_type(type_name):
                return (name, "() => void | Promise<void>", not param.has_default)
            if name == "onCheckedChange":
                return ("onCheckedChange", "(checked: boolean) => void", not param.has_default)
            if name == "onValueChange":
                mapped = arg_ts or "string"
                if arg_ts is not None:
                    return ("onValueChange", f"(value: {mapped}) => void", not param.has_default)
                return None
            if arg_ts is not None:
                return (name, f"(value: {arg_ts}) => void", not param.has_default)
            return None
        if name != "content" and is_slot_lambda_param(param):
            return (name, "ComposeChildren", not param.has_default)
        if name != "content" and is_host_slot_lambda_param(param):
            return (name, "ComposeChildren", not param.has_default)
        return None

    if name == "imageVector":
        return ("name", "string", False)
    if name == "text":
        return ("text", "string", not param.has_default)
    if component == "Icon" and name == "contentDescription":
        return ("contentDescription", "string", False)
    if component == "Image" and name == "contentDescription":
        return ("contentDescription", "string", False)
    if name == "checked":
        return ("checked", "boolean", not param.has_default)
    if name == "enabled":
        return ("enabled", "boolean", False)
    if name == "singleLine":
        return ("singleLine", "boolean", False)
    if name == "minLines":
        return ("minLines", "number", False)
    if name == "maxLines":
        return ("maxLines", "number", False)
    if name == "strokeWidth":
        return ("strokeWidth", "number", False)
    if name == "progress":
        return ("progress", "number", False)
    if "TextOverflow" in type_name:
        return ("overflow", "ComposeTextOverflow", False)
    if name == "tint" or name == "color" or name == "contentColor":
        ts_prop = "tint" if component == "Icon" and name == "tint" else name
        return (ts_prop, "ComposeColor", False)

    if "Arrangement." in type_name:
        ts_prop = "verticalArrangement" if "Vertical" in type_name else "horizontalArrangement"
        return (ts_prop, "ComposeArrangement", False)
    if "Alignment." in type_name:
        if "Horizontal" in type_name:
            return ("horizontalAlignment", "ComposeAlignment", False)
        if "Vertical" in type_name:
            return ("verticalAlignment", "ComposeAlignment", False)
        return ("contentAlignment", "ComposeAlignment", False)
    if _is_box_alignment_type(type_name):
        return ("contentAlignment", "ComposeAlignment", False)
    if "DpOffset" in type_name:
        return (name, "number", False)
    if "PopupProperties" in type_name:
        return (
            name,
            "{ focusable?: boolean; dismissOnBackPress?: boolean; dismissOnClickOutside?: boolean; clippingEnabled?: boolean; usePlatformDefaultWidth?: boolean; }",
            False,
        )

    if "Dp" in type_name:
        return (name, "number", False)
    if type_name in {"Boolean", "kotlin.Boolean"}:
        return (name, "boolean", not param.has_default)
    if type_name in {"Int", "Long", "Float", "Double", "kotlin.Int", "kotlin.Long", "kotlin.Float", "kotlin.Double"}:
        return (name, "number", not param.has_default)
    if "String" in type_name or "CharSequence" in type_name:
        return (name, "string", not param.has_default)
    if "TextStyle" in type_name:
        if component == "TextField":
            return ("style", "ComposeTextFieldStyle", False)
        return ("style", "ComposeTextStyle", False)
    if "FontWeight" in type_name:
        return ("fontWeight", "string", False)
    if "FontFamily" in type_name:
        return ("fontFamily", "string", False)

    return None


AUTO_DISCOVERY_EXCLUDED_NAMES = {
    "Content",
    "ContainerBox",
    "Decoration",
    "DecorationBox",
    "MultiChoiceSegmentedButtonRow",
    "SingleChoiceSegmentedButtonRow",
    "ContextualFlowColumn",
    "ContextualFlowRow",
    "FlowColumn",
    "FlowRow",
    "ClickableText",
}

SOURCE_PRIORITY = {
    "material3": 0,
    "foundation-layout": 1,
    "foundation": 2,
}


def _is_lambda_type(type_name: str) -> bool:
    return "->" in type_name


def strip_outer_wrapping_parens(type_name: str) -> str:
    normalized = type_name.strip()
    while normalized.startswith("(") and normalized.endswith(")"):
        depth = 0
        wraps_entire_value = True
        for index, ch in enumerate(normalized):
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0 and index != len(normalized) - 1:
                    wraps_entire_value = False
                    break
        if depth != 0 or not wraps_entire_value:
            break
        normalized = normalized[1:-1].strip()
    return normalized


def normalize_type_name(type_name: str) -> str:
    normalized = type_name.strip().rstrip("?").strip()
    return strip_outer_wrapping_parens(normalized)


def split_top_level_types(raw_types: str) -> List[str]:
    parts: List[str] = []
    current: List[str] = []
    depth_angle = depth_round = depth_square = depth_curly = 0

    for ch in raw_types:
        if ch == "<":
            depth_angle += 1
        elif ch == ">":
            depth_angle = max(0, depth_angle - 1)
        elif ch == "(":
            depth_round += 1
        elif ch == ")":
            depth_round = max(0, depth_round - 1)
        elif ch == "[":
            depth_square += 1
        elif ch == "]":
            depth_square = max(0, depth_square - 1)
        elif ch == "{":
            depth_curly += 1
        elif ch == "}":
            depth_curly = max(0, depth_curly - 1)

        if ch == "," and depth_angle == 0 and depth_round == 0 and depth_square == 0 and depth_curly == 0:
            token = "".join(current).strip()
            if token:
                parts.append(token)
            current = []
            continue

        current.append(ch)

    tail = "".join(current).strip()
    if tail:
        parts.append(tail)
    return parts


def parse_lambda_signature(type_name: str) -> Optional[Tuple[Optional[str], List[str], str]]:
    normalized = normalize_type_name(type_name)
    if "->" not in normalized:
        return None

    before_arrow, return_type = normalized.rsplit("->", 1)
    before_arrow = before_arrow.strip()
    return_type = normalize_type_name(return_type.strip())

    receiver_type: Optional[str] = None
    params_part: Optional[str] = None

    if before_arrow.endswith(")") and ".(" in before_arrow:
        split_index = before_arrow.rfind(".(")
        receiver_candidate = before_arrow[:split_index].strip()
        params_part = before_arrow[split_index + 1:].strip()
        if receiver_candidate:
            receiver_type = normalize_type_name(receiver_candidate)
    elif before_arrow.startswith("(") and before_arrow.endswith(")"):
        params_part = before_arrow

    if params_part is None:
        return None

    params_raw = params_part[1:-1].strip()
    param_types = split_top_level_types(params_raw) if params_raw else []
    return (receiver_type, param_types, return_type)


def parse_lambda_single_arg_type(type_name: str) -> Optional[str]:
    signature = parse_lambda_signature(type_name)
    if signature is None:
        return None
    receiver_type, param_types, _ = signature
    if receiver_type is not None or len(param_types) != 1:
        return None
    return param_types[0]


def parse_lambda_receiver_type(type_name: str) -> Optional[str]:
    signature = parse_lambda_signature(type_name)
    if signature is None:
        return None
    receiver_type, _, _ = signature
    return receiver_type


def parse_lambda_return_type(type_name: str) -> Optional[str]:
    signature = parse_lambda_signature(type_name)
    if signature is None:
        return None
    _, _, return_type = signature
    return return_type


def _is_unit_type(type_name: str) -> bool:
    normalized = normalize_type_name(type_name)
    return normalized in {"Unit", "kotlin.Unit"}


def is_slot_lambda_type(type_name: str) -> bool:
    signature = parse_lambda_signature(type_name)
    if signature is None:
        return False
    _, param_types, return_type = signature
    return len(param_types) == 0 and _is_unit_type(return_type)


def is_zero_arg_unit_lambda_type(type_name: str) -> bool:
    signature = parse_lambda_signature(type_name)
    if signature is None:
        return False
    receiver_type, param_types, return_type = signature
    return receiver_type is None and len(param_types) == 0 and _is_unit_type(return_type)


def host_slot_lambda_arg_type(type_name: str) -> Optional[str]:
    signature = parse_lambda_signature(type_name)
    if signature is None:
        return None
    receiver_type, param_types, return_type = signature
    if receiver_type is not None or len(param_types) != 1 or not _is_unit_type(return_type):
        return None
    return param_types[0]


def is_action_lambda_param(param: Param) -> bool:
    return _is_lambda_type(param.type) and param.name.startswith("on")


def is_slot_lambda_param(param: Param) -> bool:
    return is_slot_lambda_type(param.type) and not is_action_lambda_param(param)


def _is_padding_values_type(type_name: str) -> bool:
    normalized = normalize_type_name(type_name)
    return normalized in {
        "PaddingValues",
        "androidx.compose.foundation.layout.PaddingValues",
    }


def is_host_slot_lambda_param(param: Param) -> bool:
    arg_type = host_slot_lambda_arg_type(param.type)
    if arg_type is None or is_action_lambda_param(param):
        return False
    if param.name == "content" and _is_padding_values_type(arg_type):
        return True
    return False


def scope_modifier_resolver_expr(receiver_type: Optional[str]) -> str:
    normalized = normalize_type_name(receiver_type or "")
    if "RowScope" in normalized:
        return "{ base, slotProps -> rowComposeDslModifierResolver(base, slotProps) }"
    if "ColumnScope" in normalized:
        return "{ base, slotProps -> columnComposeDslModifierResolver(base, slotProps) }"
    if "BoxScope" in normalized:
        return "{ base, slotProps -> boxComposeDslModifierResolver(base, slotProps) }"
    return "{ base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) }"


def map_lambda_arg_to_ts(arg_type: Optional[str]) -> Optional[str]:
    if arg_type is None:
        return None
    if _is_bool_type(arg_type):
        return "boolean"
    if _is_numeric_type(arg_type):
        return "number"
    if _is_string_type(arg_type):
        return "string"
    return None


def _is_bool_type(type_name: str) -> bool:
    normalized = normalize_type_name(type_name)
    return normalized in {"Boolean", "kotlin.Boolean"}


def _is_numeric_type(type_name: str) -> bool:
    normalized = normalize_type_name(type_name)
    return normalized in {
        "Int", "Long", "Float", "Double",
        "kotlin.Int", "kotlin.Long", "kotlin.Float", "kotlin.Double",
    }


def _is_string_type(type_name: str) -> bool:
    normalized = normalize_type_name(type_name)
    return normalized in {"String", "kotlin.String", "CharSequence", "kotlin.CharSequence"}


def _is_color_type(type_name: str) -> bool:
    normalized = normalize_type_name(type_name)
    return normalized in {"Color", "androidx.compose.ui.graphics.Color"}


def _is_box_alignment_type(type_name: str) -> bool:
    normalized = normalize_type_name(type_name)
    return normalized in {"Alignment", "androidx.compose.ui.Alignment"}


def _is_supported_generic_param(param: Param) -> bool:
    name = param.name
    type_name = param.type

    if _is_lambda_type(type_name):
        if is_slot_lambda_param(param):
            return True
        if is_host_slot_lambda_param(param):
            return True
        if is_action_lambda_param(param):
            if is_zero_arg_unit_lambda_type(type_name):
                return True
            arg_type = parse_lambda_single_arg_type(type_name)
            if arg_type is None:
                return False
            return map_lambda_arg_to_ts(arg_type) is not None
        return param.has_default

    if name in {"modifier", "enabled", "checked", "selected", "expanded", "shape", "border"}:
        return True
    if name == "imageVector":
        return True
    if _is_bool_type(type_name) or _is_numeric_type(type_name) or _is_string_type(type_name):
        return True
    if _is_color_type(type_name):
        return True
    if "Dp" in type_name:
        return True
    if "Arrangement." in type_name or "Alignment." in type_name or _is_box_alignment_type(type_name):
        return True
    if "TextStyle" in type_name or "FontWeight" in type_name:
        return True
    if "FontFamily" in type_name:
        return True

    return param.has_default


def can_generate_generic_renderer(params: Sequence[Param]) -> bool:
    for param in params:
        if param.has_default:
            continue
        if not _is_supported_generic_param(param):
            return False
    return True


def discover_additional_components(
    jars: Dict[str, Path],
    base_components: Sequence[ComponentSpec],
) -> Tuple[List[ComponentSpec], Dict[str, List[Param]]]:
    base_names = {component.dsl_name for component in base_components}
    grouped_overloads: Dict[Tuple[str, str, str], List[List[Param]]] = {}

    for source, jar_path in jars.items():
        with zipfile.ZipFile(jar_path) as jar:
            for entry in jar.namelist():
                if not entry.endswith(".kt"):
                    continue
                text = jar.read(entry).decode("utf-8", errors="ignore")
                package_name = extract_package_name(text)
                if package_name is None:
                    continue
                if not (
                    package_name.startswith("androidx.compose.material3")
                    or package_name.startswith("androidx.compose.foundation")
                ):
                    continue

                fun_pattern = re.compile(r"\bfun\s+([A-Z][A-Za-z0-9_]*)\s*\(")
                function_names: set[str] = set()
                for match in fun_pattern.finditer(text):
                    name = match.group(1)
                    if name in base_names:
                        continue
                    if name in AUTO_DISCOVERY_EXCLUDED_NAMES:
                        continue
                    if name.endswith("Impl"):
                        continue

                    start = match.start()
                    if not has_attached_annotation(text, start, "Composable"):
                        continue

                    line_start = text.rfind("\n", 0, start) + 1
                    declaration = text[line_start:start]
                    if start - line_start > 0:
                        continue
                    if re.search(r"\b(private|internal)\b", declaration):
                        continue
                    open_paren = text.find("(", start)
                    if open_paren < 0:
                        continue
                    depth = 0
                    close_paren = -1
                    i = open_paren
                    while i < len(text):
                        ch = text[i]
                        if ch == "(":
                            depth += 1
                        elif ch == ")":
                            depth -= 1
                            if depth == 0:
                                close_paren = i
                                break
                        i += 1
                    if close_paren < 0:
                        continue
                    if has_attached_experimental_annotation(text, start):
                        continue
                    if has_explicit_non_unit_return_type(text, close_paren):
                        continue

                    function_names.add(name)

                for function_name in function_names:
                    overloads = parse_composable_signatures(text, function_name)
                    if not overloads:
                        continue
                    key = (source, package_name, function_name)
                    grouped_overloads.setdefault(key, []).extend(overloads)

    selected: Dict[str, Tuple[int, ComponentSpec, List[Param]]] = {}
    for (source, package_name, function_name), overloads in grouped_overloads.items():
        dsl_name = function_name
        temp_spec = ComponentSpec(
            dsl_name=dsl_name,
            source=source,
            package_name=package_name,
            function_name=function_name,
            preferred_params=(),
        )
        params = choose_overload(temp_spec, overloads)
        if not can_generate_generic_renderer(params):
            continue
        priority = SOURCE_PRIORITY.get(source, 99)
        existing = selected.get(dsl_name)
        if existing is None or priority < existing[0]:
            selected[dsl_name] = (priority, temp_spec, params)

    extra_components = [value[1] for value in selected.values()]
    extra_components.sort(key=lambda spec: (SOURCE_PRIORITY.get(spec.source, 99), spec.dsl_name))
    extra_params = {value[1].dsl_name: value[2] for value in selected.values()}
    return extra_components, extra_params


RENDERER_REQUIRED_PARAMS: Dict[str, Tuple[str, ...]] = {
    "Column": ("modifier", "verticalArrangement", "horizontalAlignment", "content"),
    "Row": ("modifier", "horizontalArrangement", "verticalAlignment", "content"),
    "Box": ("modifier", "contentAlignment", "content"),
    "Spacer": ("modifier",),
    "LazyColumn": ("modifier", "verticalArrangement", "contentPadding", "content"),
    "LazyRow": ("modifier", "horizontalArrangement", "contentPadding", "content"),
    "Text": ("text", "modifier"),
    "TextField": ("value", "onValueChange", "modifier"),
    "Switch": ("checked", "onCheckedChange", "modifier"),
    "Checkbox": ("checked", "onCheckedChange", "modifier"),
    "Button": ("onClick", "modifier", "enabled", "content"),
    "IconButton": ("onClick", "modifier", "enabled", "content"),
    "Card": ("modifier", "shape", "elevation", "border", "content"),
    "MaterialTheme": ("content",),
    "Surface": ("modifier", "shape", "content"),
    "Icon": ("imageVector", "modifier"),
    "LinearProgressIndicator": ("modifier",),
    "CircularProgressIndicator": ("modifier",),
    "SnackbarHost": ("modifier",),
}


def ensure_renderer_contract(component: str, params: Sequence[Param]) -> None:
    required = RENDERER_REQUIRED_PARAMS.get(component, ())
    if not required:
        return
    names = {p.name for p in params}
    missing = [name for name in required if name not in names]
    if missing:
        raise RuntimeError(
            f"renderer contract mismatch for {component}: missing params {', '.join(missing)}"
        )


def build_component_renderer_function(spec: ComponentSpec, params: Sequence[Param]) -> str:
    component = spec.dsl_name
    if component == "Image":
        return textwrap.dedent(
            """
            @Composable
            internal fun renderImageNode(
                node: ToolPkgComposeDslNode,
                onAction: (String, Any?) -> Unit,
                nodePath: String,
                modifierResolver: ComposeDslModifierResolver
            ) {
                val props = node.props
                val imageModel = props.imageModelOrNull()
                val alignment = props.boxAlignment("contentAlignment")
                val alpha = props.floatOrNull("alpha") ?: 1f
                val contentScale = props.contentScale("contentScale")
                val modifier = applyScopedCommonModifier(Modifier, props, modifierResolver)
                if (imageModel != null) {
                    androidx.compose.foundation.Image(
                        painter = rememberAsyncImagePainter(model = imageModel),
                        contentDescription = props.stringOrNull("contentDescription"),
                        modifier = modifier,
                        alignment = alignment,
                        alpha = alpha,
                        contentScale = contentScale
                    )
                } else {
                    androidx.compose.foundation.Image(
                        painter = rememberVectorPainter(iconFromName(props.string("name", props.string("icon", "info")))),
                        contentDescription = props.stringOrNull("contentDescription"),
                        modifier = modifier,
                        alignment = alignment,
                        alpha = alpha,
                        contentScale = contentScale
                    )
                }
            }
            """
        ).strip()

    if component == "Row":
        return textwrap.dedent(
            """
            @Composable
            internal fun renderRowNode(
                node: ToolPkgComposeDslNode,
                onAction: (String, Any?) -> Unit,
                nodePath: String,
                modifierResolver: ComposeDslModifierResolver
            ) {
                val props = node.props
                val spacing = props.dp("spacing")
                val onClick = ToolPkgComposeDslParser.extractActionId(props["onClick"])
                Row(
                    modifier = applyScopedCommonModifier(Modifier, props, modifierResolver).let { modifier ->
                        if (!onClick.isNullOrBlank()) {
                            modifier.clickable { onAction(onClick, null) }
                        } else {
                            modifier
                        }
                    },
                    horizontalArrangement = props.horizontalArrangement("horizontalArrangement", spacing),
                    verticalAlignment = props.verticalAlignment("verticalAlignment")
                ) {
                    renderSlotChildren(
                        node = node,
                        slotName = "content",
                        onAction = onAction,
                        nodePath = nodePath,
                        modifierResolver = { base, slotProps -> rowComposeDslModifierResolver(base, slotProps) },
                        fallbackToChildren = true
                    )
                }
            }
            """
        ).strip()

    if component == "Spacer":
        return textwrap.dedent(
            """
            @Composable
            internal fun renderSpacerNode(
                node: ToolPkgComposeDslNode,
                onAction: (String, Any?) -> Unit,
                nodePath: String,
                modifierResolver: ComposeDslModifierResolver
            ) {
                val props = node.props
                Spacer(
                    modifier =
                        applyScopedCommonModifier(Modifier, props, modifierResolver)
                            .width(props.dp("width"))
                            .height(props.dp("height"))
                )
            }
            """
        ).strip()

    if component == "Surface":
        return textwrap.dedent(
            """
            @Composable
            internal fun renderSurfaceNode(
                node: ToolPkgComposeDslNode,
                onAction: (String, Any?) -> Unit,
                nodePath: String,
                modifierResolver: ComposeDslModifierResolver
            ) {
                val props = node.props
                val onClick = ToolPkgComposeDslParser.extractActionId(props["onClick"])
                val contentPadding = props.commonPaddingSpecOrNull()
                val modifierProps = if (contentPadding != null) props.withoutCommonPaddingProps() else props
                val resolvedModifier =
                    applyScopedCommonModifier(Modifier, modifierProps, modifierResolver).let { modifier ->
                        if (!onClick.isNullOrBlank()) {
                            modifier.clickable { onAction(onClick, null) }
                        } else {
                            modifier
                        }
                    }
                androidx.compose.material3.Surface(
                    modifier = resolvedModifier,
                    shape = props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                    color = (props.colorOrNull("color") ?: props.colorOrNull("containerColor")).let { baseColor -> baseColor?.let { color -> props.floatOrNull("alpha")?.let { color.copy(alpha = it) } ?: color } ?: Color.Transparent },
                    contentColor = props.colorOrNull("contentColor") ?: Color.Unspecified,
                    tonalElevation = props.dp("tonalElevation"),
                    shadowElevation = props.dp("shadowElevation"),
                    content = {
                        Box(modifier = contentPadding?.applyTo(Modifier) ?: Modifier) {
                            renderSlotChildren(
                                node = node,
                                slotName = "content",
                                onAction = onAction,
                                nodePath = nodePath,
                                modifierResolver = { base, slotProps -> defaultComposeDslModifierResolver(base, slotProps) },
                                fallbackToChildren = true
                            )
                        }
                    }
                )
            }
            """
        ).strip()

    if component == "LazyColumn":
        return textwrap.dedent(
            """
            @Composable
            internal fun renderLazyColumnNode(
                node: ToolPkgComposeDslNode,
                onAction: (String, Any?) -> Unit,
                nodePath: String,
                modifierResolver: ComposeDslModifierResolver
            ) {
                val props = node.props
                val spacing = props.dp("spacing")
                val reverseLayout = props.bool("reverseLayout", false)
                val autoScrollToEnd = props.bool("autoScrollToEnd", false)
                val listState = rememberLazyListState()
                val contentNodes = node.slotChildren("content", fallbackToChildren = true)
                val autoScrollSignature =
                    if (!autoScrollToEnd) {
                        0
                    } else {
                        contentNodes.fold(1) { acc, child -> 31 * acc + child.autoScrollSignature() }
                    }

                LaunchedEffect(nodePath, autoScrollToEnd, reverseLayout, autoScrollSignature) {
                    if (autoScrollToEnd && contentNodes.isNotEmpty()) {
                        listState.scrollToItem(if (reverseLayout) 0 else contentNodes.lastIndex)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = applyScopedCommonModifier(Modifier.fillMaxSize(), props, modifierResolver),
                    horizontalAlignment = props.horizontalAlignment("horizontalAlignment"),
                    reverseLayout = reverseLayout,
                    verticalArrangement = props.verticalArrangement("verticalArrangement", spacing),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    itemsIndexed(
                        items = contentNodes,
                        key = { index, child -> child.lazyItemRenderKey(nodePath, index) }
                    ) { index, child ->
                        val childPath = "$nodePath/$index"
                        key(child.lazyItemRenderKey(nodePath, index)) {
                            renderComposeDslNode(
                                node = child,
                                onAction = onAction,
                                nodePath = childPath
                            )
                        }
                    }
                }
            }
            """
        ).strip()

    if component == "LazyRow":
        return textwrap.dedent(
            """
            @Composable
            internal fun renderLazyRowNode(
                node: ToolPkgComposeDslNode,
                onAction: (String, Any?) -> Unit,
                nodePath: String,
                modifierResolver: ComposeDslModifierResolver
            ) {
                val props = node.props
                val spacing = props.dp("spacing")
                val contentNodes = node.slotChildren("content", fallbackToChildren = true)
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
                    horizontalArrangement = props.horizontalArrangement("horizontalArrangement", spacing),
                    verticalAlignment = props.verticalAlignment("verticalAlignment")
                ) {
                    itemsIndexed(
                        items = contentNodes,
                        key = { index, child -> child.lazyItemRenderKey(nodePath, index) }
                    ) { index, child ->
                        val childPath = "$nodePath/$index"
                        key(child.lazyItemRenderKey(nodePath, index)) {
                            renderComposeDslNode(
                                node = child,
                                onAction = onAction,
                                nodePath = childPath
                            )
                        }
                    }
                }
            }
            """
        ).strip()

    if component == "Text":
        return textwrap.dedent(
            """
            @Composable
            internal fun renderTextNode(
                node: ToolPkgComposeDslNode,
                onAction: (String, Any?) -> Unit,
                nodePath: String,
                modifierResolver: ComposeDslModifierResolver
            ) {
                val props = node.props
                val resolvedStyle = props.resolvedTextStyle("style")
                val textColor = props.colorOrNull("color")
                Text(
                    text = props.string("text"),
                    style = resolvedStyle,
                    color = textColor ?: Color.Unspecified,
                    maxLines = props.int("maxLines", Int.MAX_VALUE),
                    softWrap = props.bool("softWrap", true),
                    overflow = props.textOverflow("overflow"),
                    modifier = applyScopedCommonModifier(Modifier, props, modifierResolver)
                )
            }
            """
        ).strip()

    if component == "TextField":
        return textwrap.dedent(
            """
            @Composable
            internal fun renderTextFieldNode(
                node: ToolPkgComposeDslNode,
                onAction: (String, Any?) -> Unit,
                nodePath: String,
                modifierResolver: ComposeDslModifierResolver
            ) {
                val props = node.props
                val actionId = ToolPkgComposeDslParser.extractActionId(props["onValueChange"])
                val onTextInputAction = LocalComposeDslTextInputActionHandler.current
                val flushTextInputState = LocalComposeDslFlushTextInputHandler.current
                val label = props.stringOrNull("label")
                val placeholder = props.stringOrNull("placeholder")
                val externalValue = props.string("value")
                val isPassword = props.bool("isPassword", false)
                val textFieldIdentity = props["key"]?.toString()?.trim()?.ifBlank { null } ?: nodePath
                val styleMap = props["style"] as? Map<*, *>
                val hasLabelSlot = node.slotChildren("label").isNotEmpty()
                val hasPlaceholderSlot = node.slotChildren("placeholder").isNotEmpty()
                val hasPrefixSlot = node.slotChildren("prefix").isNotEmpty()
                val hasSuffixSlot = node.slotChildren("suffix").isNotEmpty()
                val hasLeadingIconSlot = node.slotChildren("leadingIcon").isNotEmpty()
                val hasTrailingIconSlot = node.slotChildren("trailingIcon").isNotEmpty()
                val hasSupportingTextSlot = node.slotChildren("supportingText").isNotEmpty()

                var textFieldValue by remember(textFieldIdentity) {
                    mutableStateOf(
                        TextFieldValue(
                            text = externalValue,
                            selection = TextRange(externalValue.length)
                        )
                    )
                }
                var lastAppliedExternalValue by remember(textFieldIdentity) { mutableStateOf(externalValue) }
                var isFocused by remember(textFieldIdentity) { mutableStateOf(false) }

                LaunchedEffect(textFieldIdentity, externalValue, isFocused) {
                    if (externalValue == textFieldValue.text) {
                        lastAppliedExternalValue = externalValue
                        return@LaunchedEffect
                    }
                    val externalValueChanged = externalValue != lastAppliedExternalValue
                    if (isFocused && !externalValueChanged) {
                        return@LaunchedEffect
                    }
                    val start = textFieldValue.selection.start.coerceIn(0, externalValue.length)
                    val end = textFieldValue.selection.end.coerceIn(0, externalValue.length)
                    textFieldValue =
                        TextFieldValue(
                            text = externalValue,
                            selection = TextRange(start, end)
                        )
                    lastAppliedExternalValue = externalValue
                }
                val textStyle =
                    composeDslTextFieldStyleFromValue(styleMap)
                val textFieldModifier =
                    applyScopedCommonModifier(Modifier.fillMaxWidth(), props, modifierResolver)
                        .onFocusChanged { focusState ->
                            val nextFocused = focusState.isFocused
                            if (isFocused && !nextFocused && externalValue != textFieldValue.text) {
                                flushTextInputState()
                            }
                            isFocused = nextFocused
                        }

                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { nextValue ->
                        if (!actionId.isNullOrBlank()) {
                            val previousText = textFieldValue.text
                            textFieldValue = nextValue
                            if (nextValue.text != previousText) {
                                onTextInputAction(actionId, nextValue.text)
                            }
                        }
                    },
                    label =
                        when {
                            hasLabelSlot -> {
                                {
                                    renderSlotChildren(
                                        node = node,
                                        slotName = "label",
                                        onAction = onAction,
                                        nodePath = nodePath
                                    )
                                }
                            }
                            else -> label?.let { labelText -> { Text(labelText) } }
                        },
                    placeholder =
                        when {
                            hasPlaceholderSlot -> {
                                {
                                    renderSlotChildren(
                                        node = node,
                                        slotName = "placeholder",
                                        onAction = onAction,
                                        nodePath = nodePath
                                    )
                                }
                            }
                            else -> placeholder?.let { placeholderText -> { Text(placeholderText) } }
                        },
                    prefix =
                        if (hasPrefixSlot) {
                            {
                                renderSlotChildren(
                                    node = node,
                                    slotName = "prefix",
                                    onAction = onAction,
                                    nodePath = nodePath
                                )
                            }
                        } else null,
                    suffix =
                        if (hasSuffixSlot) {
                            {
                                renderSlotChildren(
                                    node = node,
                                    slotName = "suffix",
                                    onAction = onAction,
                                    nodePath = nodePath
                                )
                            }
                        } else null,
                    leadingIcon =
                        if (hasLeadingIconSlot) {
                            {
                                renderSlotChildren(
                                    node = node,
                                    slotName = "leadingIcon",
                                    onAction = onAction,
                                    nodePath = nodePath
                                )
                            }
                        } else null,
                    trailingIcon =
                        if (hasTrailingIconSlot) {
                            {
                                renderSlotChildren(
                                    node = node,
                                    slotName = "trailingIcon",
                                    onAction = onAction,
                                    nodePath = nodePath
                                )
                            }
                        } else null,
                    supportingText =
                        if (hasSupportingTextSlot) {
                            {
                                renderSlotChildren(
                                    node = node,
                                    slotName = "supportingText",
                                    onAction = onAction,
                                    nodePath = nodePath
                                )
                            }
                        } else null,
                    singleLine = props.bool("singleLine", false),
                    minLines = props.int("minLines", 1),
                    maxLines = props.int("maxLines", if (props.bool("singleLine", false)) 1 else Int.MAX_VALUE),
                    readOnly = props.bool("readOnly", false),
                    isError = props.bool("isError", false),
                    textStyle = textStyle ?: androidx.compose.ui.text.TextStyle.Default,
                    visualTransformation = if (isPassword) {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    } else {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    },
                    modifier = textFieldModifier
                )
            }
            """
        ).strip()

    if component == "Switch":
        return textwrap.dedent(
            """
            @Composable
            internal fun renderSwitchNode(
                node: ToolPkgComposeDslNode,
                onAction: (String, Any?) -> Unit,
                nodePath: String,
                modifierResolver: ComposeDslModifierResolver
            ) {
                val props = node.props
                val actionId = ToolPkgComposeDslParser.extractActionId(props["onCheckedChange"])
                val checkedThumbColor = props.colorOrNull("checkedThumbColor")
                val checkedTrackColor = props.colorOrNull("checkedTrackColor")
                val uncheckedThumbColor = props.colorOrNull("uncheckedThumbColor")
                val uncheckedTrackColor = props.colorOrNull("uncheckedTrackColor")
                val hasThumbContentSlot = node.slotChildren("thumbContent").isNotEmpty()
                val switchColors =
                    if (
                        checkedThumbColor != null ||
                            checkedTrackColor != null ||
                            uncheckedThumbColor != null ||
                            uncheckedTrackColor != null
                    ) {
                        androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = checkedThumbColor ?: MaterialTheme.colorScheme.primary,
                            checkedTrackColor = checkedTrackColor ?: MaterialTheme.colorScheme.primaryContainer,
                            uncheckedThumbColor = uncheckedThumbColor ?: MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = uncheckedTrackColor ?: MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        androidx.compose.material3.SwitchDefaults.colors()
                    }
                Switch(
                    checked = props.bool("checked", false),
                    onCheckedChange = { checked ->
                        if (!actionId.isNullOrBlank()) {
                            onAction(actionId, checked)
                        }
                    },
                    enabled = !actionId.isNullOrBlank() && props.bool("enabled", true),
                    modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
                    thumbContent =
                        if (hasThumbContentSlot) {
                            {
                                renderSlotChildren(
                                    node = node,
                                    slotName = "thumbContent",
                                    onAction = onAction,
                                    nodePath = nodePath
                                )
                            }
                        } else null,
                    colors = switchColors
                )
            }
            """
        ).strip()

    if component == "Checkbox":
        return textwrap.dedent(
            """
            @Composable
            internal fun renderCheckboxNode(
                node: ToolPkgComposeDslNode,
                onAction: (String, Any?) -> Unit,
                nodePath: String,
                modifierResolver: ComposeDslModifierResolver
            ) {
                val props = node.props
                val actionId = ToolPkgComposeDslParser.extractActionId(props["onCheckedChange"])
                Checkbox(
                    checked = props.bool("checked", false),
                    onCheckedChange = { checked ->
                        if (!actionId.isNullOrBlank()) {
                            onAction(actionId, checked)
                        }
                    },
                    enabled = !actionId.isNullOrBlank() && props.bool("enabled", true),
                    modifier = applyScopedCommonModifier(Modifier, props, modifierResolver)
                )
            }
            """
        ).strip()

    if component == "Card":
        return textwrap.dedent(
            """
            @Composable
            internal fun renderCardNode(
                node: ToolPkgComposeDslNode,
                onAction: (String, Any?) -> Unit,
                nodePath: String,
                modifierResolver: ComposeDslModifierResolver
            ) {
                val props = node.props
                val containerColor = props.colorOrNull("containerColor")
                val containerAlpha = props.floatOrNull("containerAlpha")
                val alpha = props.floatOrNull("alpha")
                val contentColor = props.colorOrNull("contentColor")
                val contentAlpha = props.floatOrNull("contentAlpha")
                val finalContainerColor = containerColor?.let { color ->
                    when {
                        containerAlpha != null -> color.copy(alpha = containerAlpha)
                        alpha != null -> color.copy(alpha = alpha)
                        else -> color
                    }
                }
                val finalContentColor = contentColor?.let { color ->
                    if (contentAlpha != null) color.copy(alpha = contentAlpha) else color
                }
                val cardColors =
                    when {
                        finalContainerColor != null && finalContentColor != null ->
                            CardDefaults.cardColors(
                                containerColor = finalContainerColor,
                                contentColor = finalContentColor
                            )
                        finalContainerColor != null ->
                            CardDefaults.cardColors(containerColor = finalContainerColor)
                        finalContentColor != null ->
                            CardDefaults.cardColors(contentColor = finalContentColor)
                        else -> CardDefaults.cardColors()
                    }
                Card(
                    colors = cardColors,
                    modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
                    shape = props.shapeOrNull() ?: CardDefaults.shape,
                    border = props.borderOrNull(),
                    elevation = CardDefaults.cardElevation(defaultElevation = props.dp("elevation", 1.dp))
                ) {
                    renderSlotChildren(
                        node = node,
                        slotName = "content",
                        onAction = onAction,
                        nodePath = nodePath,
                        fallbackToChildren = true
                    )
                }
            }
            """
        ).strip()

    if component == "Icon":
        return textwrap.dedent(
            """
            @Composable
            internal fun renderIconNode(
                node: ToolPkgComposeDslNode,
                onAction: (String, Any?) -> Unit,
                nodePath: String,
                modifierResolver: ComposeDslModifierResolver
            ) {
                val props = node.props
                val iconName = props.string("name", props.string("icon", "info"))
                val tint = props.colorOrNull("tint") ?: MaterialTheme.colorScheme.onSurfaceVariant
                val size = props.floatOrNull("size")
                Icon(
                    imageVector = iconFromName(iconName),
                    contentDescription = null,
                    tint = tint,
                    modifier = if (size != null) {
                        applyScopedCommonModifier(Modifier, props, modifierResolver).width(size.dp).height(size.dp)
                    } else {
                        applyScopedCommonModifier(Modifier, props, modifierResolver)
                    }
                )
            }
            """
        ).strip()

    if component == "LinearProgressIndicator":
        return textwrap.dedent(
            """
            @Composable
            internal fun renderLinearProgressIndicatorNode(
                node: ToolPkgComposeDslNode,
                onAction: (String, Any?) -> Unit,
                nodePath: String,
                modifierResolver: ComposeDslModifierResolver
            ) {
                val props = node.props
                val progress = props.floatOrNull("progress")
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = applyScopedCommonModifier(Modifier.fillMaxWidth(), props, modifierResolver)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = applyScopedCommonModifier(Modifier.fillMaxWidth(), props, modifierResolver)
                    )
                }
            }
            """
        ).strip()

    if component == "CircularProgressIndicator":
        return textwrap.dedent(
            """
            @Composable
            internal fun renderCircularProgressIndicatorNode(
                node: ToolPkgComposeDslNode,
                onAction: (String, Any?) -> Unit,
                nodePath: String,
                modifierResolver: ComposeDslModifierResolver
            ) {
                val props = node.props
                val strokeWidth = props.floatOrNull("strokeWidth")
                val color = props.colorOrNull("color")
                CircularProgressIndicator(
                    modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
                    strokeWidth = if (strokeWidth != null) strokeWidth.dp else 4.dp,
                    color = color ?: MaterialTheme.colorScheme.primary
                )
            }
            """
        ).strip()

    if component == "SnackbarHost":
        return textwrap.dedent(
            """
            @Composable
            internal fun renderSnackbarHostNode(
                node: ToolPkgComposeDslNode,
                onAction: (String, Any?) -> Unit,
                nodePath: String,
                modifierResolver: ComposeDslModifierResolver
            ) {
                Spacer(modifier = applyScopedCommonModifier(Modifier, node.props, modifierResolver))
            }
            """
        ).strip()

    if component == "BasicText":
        return textwrap.dedent(
            """
            @Composable
            internal fun renderBasicTextNode(
                node: ToolPkgComposeDslNode,
                onAction: (String, Any?) -> Unit,
                nodePath: String,
                modifierResolver: ComposeDslModifierResolver
            ) {
                val props = node.props
                val onTextLayoutActionId = ToolPkgComposeDslParser.extractActionId(props["onTextLayout"])
                androidx.compose.foundation.text.BasicText(
                    text = props.string("text"),
                    modifier = applyScopedCommonModifier(Modifier, props, modifierResolver),
                    style = props.resolvedTextStyle("style", includeColor = true),
                    softWrap = props.bool("softWrap", false),
                    maxLines = props.int("maxLines", 0),
                    overflow = props.textOverflow("overflow"),
                    onTextLayout = {
                        if (!onTextLayoutActionId.isNullOrBlank()) {
                            onAction(onTextLayoutActionId, null)
                        }
                    }
                )
            }
            """
        ).strip()

    return build_generic_renderer_function(spec, params)


def _generic_default_value_expr(component: str, param: Param) -> Optional[str]:
    name = param.name
    type_name = param.type

    if name == "modifier":
        return "applyScopedCommonModifier(Modifier, props, modifierResolver)"
    if name == "imageVector":
        return 'iconFromName(props.string("name", props.string("icon", "info")))'
    if name == "shape":
        if component == "Button":
            return "props.shapeOrNull() ?: androidx.compose.material3.ButtonDefaults.shape"
        if component == "ElevatedButton":
            return "props.shapeOrNull() ?: androidx.compose.material3.ButtonDefaults.elevatedShape"
        if component == "FilledTonalButton":
            return "props.shapeOrNull() ?: androidx.compose.material3.ButtonDefaults.filledTonalShape"
        if component == "OutlinedButton":
            return "props.shapeOrNull() ?: androidx.compose.material3.ButtonDefaults.outlinedShape"
        if component == "TextButton":
            return "props.shapeOrNull() ?: androidx.compose.material3.ButtonDefaults.textShape"
        if component == "IconButton" or component == "IconToggleButton":
            return "props.shapeOrNull() ?: androidx.compose.material3.IconButtonDefaults.standardShape"
        if component in {"FilledIconButton", "FilledTonalIconButton", "FilledIconToggleButton", "FilledTonalIconToggleButton"}:
            return "props.shapeOrNull() ?: androidx.compose.material3.IconButtonDefaults.filledShape"
        if component in {"OutlinedIconButton", "OutlinedIconToggleButton"}:
            return "props.shapeOrNull() ?: androidx.compose.material3.IconButtonDefaults.outlinedShape"
        return "props.shapeOrNull() ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp)"
    if name == "border":
        return "props.borderOrNull()"
    if name == "contentPadding":
        if component.endswith("Button") and "Icon" not in component and "FloatingAction" not in component:
            return 'props.paddingValuesOrNull("contentPadding") ?: androidx.compose.material3.ButtonDefaults.ContentPadding'
        return 'props.paddingValuesOrNull("contentPadding") ?: PaddingValues(0.dp)'
    if name == "contentDescription":
        return 'props.stringOrNull("contentDescription")'
    if name == "text":
        return 'props.string("text")'
    if name == "value" and _is_string_type(type_name):
        return 'props.string("value")'
    if name == "enabled":
        return 'props.bool("enabled", true)'
    if name in {"checked", "selected", "expanded"}:
        return f'props.bool("{name}", false)'
    if "Arrangement." in type_name:
        if "Vertical" in type_name:
            return 'props.verticalArrangement("verticalArrangement", spacing)'
        return 'props.horizontalArrangement("horizontalArrangement", spacing)'
    if "Alignment." in type_name:
        if "Horizontal" in type_name:
            return 'props.horizontalAlignment("horizontalAlignment")'
        if "Vertical" in type_name:
            return 'props.verticalAlignment("verticalAlignment")'
        return 'props.boxAlignment("contentAlignment")'
    if _is_box_alignment_type(type_name):
        return 'props.boxAlignment("contentAlignment")'
    if "DpOffset" in type_name:
        return f'DpOffset(props.dp("{name}"), 0.dp)'
    if "PopupProperties" in type_name:
        return f'popupPropertiesFromValue(props["{name}"])'
    if "TextStyle" in type_name:
        return 'props.textStyle("style")'
    if "FontWeight" in type_name:
        return 'props.fontWeightOrNull("fontWeight") ?: FontWeight.Normal'
    if "FontFamily" in type_name:
        return 'props.fontFamilyOrNull("fontFamily") ?: androidx.compose.ui.text.font.FontFamily.Default'
    if "Dp" in type_name:
        return f'props.dp("{name}")'
    if _is_bool_type(type_name):
        return f'props.bool("{name}", false)'
    if type_name in {"Int", "kotlin.Int"}:
        return f'props.int("{name}", 0)'
    if type_name in {"Long", "kotlin.Long"}:
        return f'props.int("{name}", 0).toLong()'
    if type_name in {"Float", "Double", "kotlin.Float", "kotlin.Double"}:
        expr = f'(props.floatOrNull("{name}") ?: 0f)'
        if type_name in {"Double", "kotlin.Double"}:
            return f"{expr}.toDouble()"
        return expr
    if _is_string_type(type_name):
        return f'props.string("{name}")'
    if name == "colors" and _is_plain_button_like(component):
        return "buttonColors"
    if _is_color_type(type_name):
        if component == "Surface" and name == "color":
            return (
                '(props.colorOrNull("color") ?: props.colorOrNull("containerColor"))'
                '.let { baseColor -> '
                'baseColor?.let { color -> props.floatOrNull("alpha")?.let { color.copy(alpha = it) } ?: color }'
                ' ?: Color.Transparent }'
            )
        if name == "tint":
            return 'props.colorOrNull("tint") ?: MaterialTheme.colorScheme.onSurfaceVariant'
        return f'props.colorOrNull("{name}") ?: Color.Unspecified'
    return None


def build_action_lambda_arg_name(param_name: str) -> str:
    if param_name == "onCheckedChange":
        return "checked"
    if param_name == "onValueChange":
        return "value"
    return "value"


def _supports_text_fallback_for_content(component: str) -> bool:
    return (
        component.endswith("Button")
        and "Icon" not in component
        and "FloatingAction" not in component
    )


def _is_plain_button_like(component: str) -> bool:
    return component in {
        "Button",
        "ElevatedButton",
        "FilledTonalButton",
        "OutlinedButton",
        "TextButton",
    }


def _button_colors_defaults_expr(component: str) -> str | None:
    defaults_by_component = {
        "Button": "androidx.compose.material3.ButtonDefaults.buttonColors",
        "ElevatedButton": "androidx.compose.material3.ButtonDefaults.elevatedButtonColors",
        "FilledTonalButton": "androidx.compose.material3.ButtonDefaults.filledTonalButtonColors",
        "OutlinedButton": "androidx.compose.material3.ButtonDefaults.outlinedButtonColors",
        "TextButton": "androidx.compose.material3.ButtonDefaults.textButtonColors",
    }
    return defaults_by_component.get(component)


def _button_colors_renderer_prelude(component: str) -> List[str]:
    factory = _button_colors_defaults_expr(component)
    if factory is None:
        return []
    return [
        '    val containerColor = props.colorOrNull("containerColor")',
        '    val contentColor = props.colorOrNull("contentColor")',
        '    val disabledContainerColor = props.colorOrNull("disabledContainerColor")',
        '    val disabledContentColor = props.colorOrNull("disabledContentColor")',
        '    val buttonColors =',
        '        if (',
        '            containerColor != null ||',
        '                contentColor != null ||',
        '                disabledContainerColor != null ||',
        '                disabledContentColor != null',
        '        ) {',
        f'            {factory}(',
        '                containerColor = containerColor ?: Color.Unspecified,',
        '                contentColor = contentColor ?: Color.Unspecified,',
        '                disabledContainerColor = disabledContainerColor ?: Color.Unspecified,',
        '                disabledContentColor = disabledContentColor ?: Color.Unspecified',
        '            )',
        '        } else {',
        f'            {factory}()',
        '        }',
    ]


def build_generic_renderer_function(spec: ComponentSpec, params: Sequence[Param]) -> str:
    component = spec.dsl_name
    func_name = f"render{component}Node"
    fq_fn = f"{spec.package_name}.{spec.function_name}"
    lines: List[str] = []
    lines.append("@Composable")
    lines.append(f"internal fun {func_name}(")
    lines.append("    node: ToolPkgComposeDslNode,")
    lines.append("    onAction: (String, Any?) -> Unit,")
    lines.append("    nodePath: String,")
    lines.append("    modifierResolver: ComposeDslModifierResolver")
    lines.append(") {")
    lines.append("    val props = node.props")
    lines.extend(_button_colors_renderer_prelude(component))

    has_spacing = any("Arrangement." in p.type for p in params)
    if has_spacing:
        lines.append('    val spacing = props.dp("spacing")')

    action_params: List[str] = []
    for param in params:
        if is_action_lambda_param(param):
            action_params.append(param.name)
    for name in action_params:
        lines.append(f'    val {name}ActionId = ToolPkgComposeDslParser.extractActionId(props["{name}"])')

    arg_lines: List[str] = []
    icon_button_like = component.endswith("IconButton") or component == "IconToggleButton"
    for param in params:
        name = param.name
        type_name = param.type
        if _is_lambda_type(type_name):
            if is_action_lambda_param(param):
                arg_type = parse_lambda_single_arg_type(type_name)
                arg_ts = map_lambda_arg_to_ts(arg_type)
                action_id_var = f"{name}ActionId"
                if is_zero_arg_unit_lambda_type(type_name):
                    arg_lines.append(
                        f"        {name} = {{\n"
                        f"            if (!{action_id_var}.isNullOrBlank()) {{\n"
                        f"                onAction({action_id_var}, null)\n"
                        f"            }}\n"
                        f"        }}"
                    )
                    continue
                if arg_ts is not None:
                    value_name = build_action_lambda_arg_name(name)
                    arg_lines.append(
                        f"        {name} = {{ {value_name} ->\n"
                        f"            if (!{action_id_var}.isNullOrBlank()) {{\n"
                        f"                onAction({action_id_var}, {value_name})\n"
                        f"            }}\n"
                        f"        }}"
                    )
                    continue
                if param.has_default:
                    continue
                raise RuntimeError(f"unsupported lambda arg type for {component}.{name}: {type_name}")
            if is_slot_lambda_param(param):
                slot_modifier_resolver = scope_modifier_resolver_expr(parse_lambda_receiver_type(type_name))
                fallback_to_children = name == "content"
                if fallback_to_children and icon_button_like:
                    arg_lines.append(
                        f"        {name} = {{\n"
                        f'            val slotNodes = node.slotChildren("{name}", fallbackToChildren = true)\n'
                        f"            if (slotNodes.isNotEmpty()) {{\n"
                        f"                renderComposeDslNodes(\n"
                        f"                    nodes = slotNodes,\n"
                        f"                    onAction = onAction,\n"
                        f'                    nodePath = \"$nodePath:{name}\",\n'
                        f"                    modifierResolver = {slot_modifier_resolver}\n"
                        f"                )\n"
                        f"            }} else {{\n"
                        f'                val iconName = props.string("icon", props.string("name", "info"))\n'
                        f"                Icon(\n"
                        f"                    imageVector = iconFromName(iconName),\n"
                        f"                    contentDescription = null\n"
                        f"                )\n"
                        f"            }}\n"
                        f"        }}"
                    )
                elif fallback_to_children and _supports_text_fallback_for_content(component):
                    arg_lines.append(
                        f"        {name} = {{\n"
                        f'            val slotNodes = node.slotChildren("{name}", fallbackToChildren = true)\n'
                        f"            if (slotNodes.isNotEmpty()) {{\n"
                        f"                renderComposeDslNodes(\n"
                        f"                    nodes = slotNodes,\n"
                        f"                    onAction = onAction,\n"
                        f'                    nodePath = \"$nodePath:{name}\",\n'
                        f"                    modifierResolver = {slot_modifier_resolver}\n"
                        f"                )\n"
                        f"            }} else {{\n"
                        f'                Text(props.string("text", "{component}"))\n'
                        f"            }}\n"
                        f"        }}"
                    )
                else:
                    fallback_literal = "true" if fallback_to_children else "false"
                    arg_lines.append(
                        f"        {name} = {{\n"
                        f"            renderSlotChildren(\n"
                        f"                node = node,\n"
                        f'                slotName = "{name}",\n'
                        f"                onAction = onAction,\n"
                        f"                nodePath = nodePath,\n"
                        f"                modifierResolver = " + slot_modifier_resolver + ",\n"
                        f"                fallbackToChildren = {fallback_literal}\n"
                        f"            )\n"
                        f"        }}"
                    )
                continue
            if is_host_slot_lambda_param(param):
                fallback_to_children = name == "content"
                host_arg_type = host_slot_lambda_arg_type(type_name)
                if name == "content" and host_arg_type is not None and _is_padding_values_type(host_arg_type):
                    arg_lines.append(
                        f"        {name} = {{ innerPadding ->\n"
                        f"            Box(\n"
                        f"                modifier = Modifier\n"
                        f"                    .padding(innerPadding)\n"
                        f"                    .consumeWindowInsets(innerPadding)\n"
                        f"            ) {{\n"
                        f"                renderSlotChildren(\n"
                        f"                    node = node,\n"
                        f'                    slotName = "{name}",\n'
                        f"                    onAction = onAction,\n"
                        f"                    nodePath = nodePath,\n"
                        f"                    fallbackToChildren = " + ("true" if fallback_to_children else "false") + "\n"
                        f"                )\n"
                        f"            }}\n"
                        f"        }}"
                    )
                    continue
                if param.has_default:
                    continue
                raise RuntimeError(f"unsupported host slot lambda param: {component}.{name}: {type_name}")
            if not param.has_default:
                raise RuntimeError(f"unsupported required lambda param: {component}.{name}: {type_name}")
            continue

        expr = _generic_default_value_expr(component, param)
        if expr is None:
            if param.has_default:
                continue
            raise RuntimeError(f"unsupported required param mapping: {component}.{name}: {type_name}")
        if name == "border" and param.has_default:
            # Skip optional border in generic auto-generated nodes to avoid nullable/non-null
            # overload mismatches across Material3 variants.
            continue
        arg_lines.append(f"        {name} = {expr}")

    lines.append(f"    {fq_fn}(")
    if arg_lines:
        lines.append(",\n".join(arg_lines))
    lines.append("    )")
    lines.append("}")
    return "\n".join(lines)


def build_generated_renderer_functions(
    component_params: Dict[str, List[Param]],
    components: Sequence[ComponentSpec]
) -> str:
    rendered: List[str] = []
    for spec in components:
        params = component_params.get(spec.dsl_name, [])
        ensure_renderer_contract(spec.dsl_name, params)
        rendered.append(build_component_renderer_function(spec, params))
    return "\n\n".join(rendered)


def build_ts_generated_file(
    component_params: Dict[str, List[Param]],
    components: Sequence[ComponentSpec],
    output_path: Path,
    extra_ts_components: Optional[Dict[str, List[Tuple[str, str, bool]]]] = None,
    extra_ts_imports: Optional[List[str]] = None
) -> None:
    extra_ts_components = extra_ts_components or {}
    extra_ts_imports = extra_ts_imports or []

    lines: List[str] = []
    lines.append('import type {')
    lines.append("  ComposeAlignment,")
    lines.append("  ComposeArrangement,")
    lines.append("  ComposeBorder,")
    lines.append("  ComposeChildren,")
    lines.append("  ComposeColor,")
    lines.append("  ComposeCommonProps,")
    lines.append("  ComposeNodeFactory,")
    lines.append("  ComposePadding,")
    lines.append("  ComposeShape,")
    lines.append("  ComposeTextFieldStyle,")
    lines.append("  ComposeTextOverflow,")
    lines.append("  ComposeTextStyle,")
    for import_name in extra_ts_imports:
        lines.append(f"  {import_name},")
    lines.append('} from \"./compose-dsl\";')
    lines.append("")
    lines.append("/**")
    lines.append(" * AUTO-GENERATED from Compose Material3/Foundation source signatures.")
    lines.append(" * Do not edit manually. Regenerate via tools/compose_dsl/generate_compose_dsl_artifacts.py.")
    lines.append(" */")
    lines.append("")

    for component in [c.dsl_name for c in components]:
        iface = f"ComposeGenerated{component}Props"
        lines.append(f"export interface {iface} extends ComposeCommonProps {{")
        emitted: Dict[str, Tuple[str, bool]] = {}
        emitted.setdefault("zIndex", ("number", False))
        for param in component_params.get(component, []):
            mapped = map_param_to_ts(component, param)
            if mapped is None:
                continue
            prop_name, ts_type, required = mapped
            prev = emitted.get(prop_name)
            if prev is not None:
                continue
            emitted[prop_name] = (ts_type, required)

        if any(param.name == "content" and (is_slot_lambda_param(param) or is_host_slot_lambda_param(param)) for param in component_params.get(component, [])):
            emitted.setdefault("content", ("ComposeChildren", False))

        if component == "TextField":
            emitted["label"] = ("string | ComposeChildren", False)
            emitted["placeholder"] = ("string | ComposeChildren", False)
            emitted.setdefault("leadingIcon", ("ComposeChildren", False))
            emitted.setdefault("trailingIcon", ("ComposeChildren", False))
            emitted.setdefault("prefix", ("ComposeChildren", False))
            emitted.setdefault("suffix", ("ComposeChildren", False))
            emitted.setdefault("supportingText", ("ComposeChildren", False))
            emitted.setdefault("value", ("string", True))
            emitted.setdefault("isPassword", ("boolean", False))
            emitted.setdefault("style", ("ComposeTextFieldStyle", False))

        if component == "Text" or component == "BasicText":
            emitted.setdefault("fontSize", ("number", False))
            emitted.setdefault("fontFamily", ("string", False))

        if component == "Switch":
            emitted.setdefault("checkedThumbColor", ("ComposeColor", False))
            emitted.setdefault("checkedTrackColor", ("ComposeColor", False))
            emitted.setdefault("uncheckedThumbColor", ("ComposeColor", False))
            emitted.setdefault("uncheckedTrackColor", ("ComposeColor", False))
            emitted.setdefault("thumbContent", ("ComposeChildren", False))

        if component == "Scaffold":
            emitted.setdefault("containerColor", ("ComposeColor", False))

        if component == "IconButton" or component.endswith("IconButton") or component == "IconToggleButton":
            emitted.setdefault("icon", ("string", False))

        if _is_plain_button_like(component):
            emitted.setdefault("contentPadding", ("ComposePadding", False))

        if component == "Button":
            emitted.setdefault("text", ("string", False))

        if component == "Button" or component.endswith("Button"):
            emitted.setdefault("shape", ("ComposeShape", False))
        if _is_plain_button_like(component):
            emitted.setdefault("containerColor", ("ComposeColor", False))
            emitted.setdefault("contentColor", ("ComposeColor", False))
            emitted.setdefault("disabledContainerColor", ("ComposeColor", False))
            emitted.setdefault("disabledContentColor", ("ComposeColor", False))

        if component == "Card" or component.endswith("Card"):
            emitted.setdefault("containerColor", ("ComposeColor", False))
            emitted.setdefault("contentColor", ("ComposeColor", False))
            emitted.setdefault("shape", ("ComposeShape", False))
            emitted.setdefault("border", ("ComposeBorder", False))
            emitted.setdefault("elevation", ("number", False))

        if component == "Surface":
            emitted.setdefault("containerColor", ("ComposeColor", False))
            emitted.setdefault("contentColor", ("ComposeColor", False))
            emitted.setdefault("shape", ("ComposeShape", False))
            emitted.setdefault("alpha", ("number", False))
            emitted.setdefault("onClick", ("() => void | Promise<void>", False))

        if component == "Icon":
            emitted.setdefault("size", ("number", False))

        if component == "Image":
            emitted.setdefault("contentDescription", ("string", False))
            emitted.setdefault("url", ("string", False))
            emitted.setdefault("uri", ("string", False))
            emitted.setdefault("path", ("string", False))
            emitted.setdefault("fileUri", ("string", False))
            emitted.setdefault("src", ("string", False))
            emitted.setdefault("name", ("string", False))
            emitted.setdefault("icon", ("string", False))
            emitted.setdefault("alpha", ("number", False))
            emitted.setdefault("contentAlignment", ("ComposeAlignment", False))
            emitted.setdefault("contentScale", ("ComposeContentScale", False))

        if component == "Row":
            emitted.setdefault("onClick", ("() => void | Promise<void>", False))

        if component == "Column":
            emitted.setdefault("horizontalAlignment", ("ComposeAlignment", False))
            emitted.setdefault("verticalArrangement", ("ComposeArrangement", False))

        if component == "LazyColumn":
            emitted.setdefault("spacing", ("number", False))
            emitted.setdefault("autoScrollToEnd", ("boolean", False))

        for prop_name in sorted(emitted.keys()):
            ts_type, required = emitted[prop_name]
            opt = "" if required else "?"
            lines.append(f"  {prop_name}{opt}: {ts_type};")

        lines.append("}")
        lines.append("")

    if extra_ts_components:
        for component, props in extra_ts_components.items():
            lines.append(f"export interface ComposeGenerated{component}Props extends ComposeCommonProps {{")
            lines.append("  zIndex?: number;")
            for prop_name, ts_type, required in props:
                opt = "" if required else "?"
                lines.append(f"  {prop_name}{opt}: {ts_type};")
            lines.append("}")
            lines.append("")

    lines.append("export interface ComposeMaterial3GeneratedUiFactoryRegistry {")
    for component in [c.dsl_name for c in components]:
        iface = f"ComposeGenerated{component}Props"
        lines.append(f"  {component}: ComposeNodeFactory<{iface}>;")
    for component in extra_ts_components.keys():
        iface = f"ComposeGenerated{component}Props"
        lines.append(f"  {component}: ComposeNodeFactory<{iface}>;")
    lines.append("}")
    lines.append("")

    output_path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


def build_kotlin_registry_file(components: Sequence[ComponentSpec], output_path: Path) -> None:
    entries = [
        (
            f'    normalizeToken("{c.dsl_name}") to '
            f'{{ node, onAction, nodePath, modifierResolver -> render{c.dsl_name}Node(node, onAction, nodePath, modifierResolver) }},'
        )
        for c in components
    ]
    content = """package com.ai.assistance.operit.ui.common.composedsl

/**
 * AUTO-GENERATED from Compose Material3/Foundation component bindings.
 * Do not edit manually. Regenerate via tools/compose_dsl/generate_compose_dsl_artifacts.py.
 */
internal val composeDslGeneratedNodeRendererRegistry: Map<String, ComposeDslNodeRenderer> = mapOf(
{entries}
)
""".replace("{entries}", "\n".join(entries).rstrip(","))
    output_path.write_text(content, encoding="utf-8", newline="\n")


def build_kotlin_renderers_file(
    component_params: Dict[str, List[Param]],
    components: Sequence[ComponentSpec],
    output_path: Path
) -> None:
    template_path = SCRIPT_DIR / "templates" / "ToolPkgComposeDslGeneratedRenderers.kt.tpl"
    if not template_path.exists():
        raise RuntimeError(f"renderer template not found: {template_path}")
    template = template_path.read_text(encoding="utf-8")
    marker = "// __GENERATED_COMPONENT_RENDERERS__"
    if marker not in template:
        raise RuntimeError(f"renderer template marker not found: {marker}")
    generated = build_generated_renderer_functions(component_params, components)
    output_path.write_text(
        template.replace(marker, generated),
        encoding="utf-8",
        newline="\n"
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate compose_dsl artifacts from Compose source jars.")
    parser.parse_args()

    jars = resolve_source_jars()
    base_components = list(COMPONENTS)
    base_params = load_component_params(jars, base_components)
    extra_components, extra_params = discover_additional_components(jars, base_components)

    all_components = base_components + extra_components
    all_params: Dict[str, List[Param]] = {}
    all_params.update(base_params)
    all_params.update(extra_params)

    ts_output = ROOT / "examples" / "types" / "compose-dsl.material3.generated.d.ts"
    kt_output = (
        ROOT
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "ai"
        / "assistance"
        / "operit"
        / "ui"
        / "common"
        / "composedsl"
        / "ToolPkgComposeDslGeneratedRegistry.kt"
    )
    kt_renderers_output = (
        ROOT
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "ai"
        / "assistance"
        / "operit"
        / "ui"
        / "common"
        / "composedsl"
        / "ToolPkgComposeDslGeneratedRenderers.kt"
    )

    extra_ts_components = {
        "Canvas": [
            ("commands", "ComposeCanvasCommand[]", False)
        ]
    }
    build_ts_generated_file(
        all_params,
        all_components,
        ts_output,
        extra_ts_components=extra_ts_components,
        extra_ts_imports=["ComposeCanvasCommand", "ComposeContentScale"]
    )
    build_kotlin_registry_file(all_components, kt_output)
    build_kotlin_renderers_file(all_params, all_components, kt_renderers_output)

    print(f"generated: {ts_output}")
    print(f"generated: {kt_output}")
    print(f"generated: {kt_renderers_output}")
    print(f"components: base={len(base_components)}, auto={len(extra_components)}, total={len(all_components)}")


if __name__ == "__main__":
    main()
