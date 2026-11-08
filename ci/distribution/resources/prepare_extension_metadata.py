"""Keep declarative extension descriptions available when executable packages are externalized."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re


def collect(catalog: dict, examples: Path) -> dict[str, str]:
    metadata: dict[str, str] = {}
    for pack in catalog["packs"]:
        extension = pack.get("extension")
        if extension is None:
            continue
        source = extension["source"]
        if not source or Path(source).name != source or source in {".", ".."}:
            raise ValueError("Invalid extension source: " + source)
        if extension["kind"] == "sandbox":
            text = (examples / source).read_text(encoding="utf-8")
            header = re.search(r"/\*\s*METADATA\b([\s\S]*?)\*/", text)
            if header is None:
                raise ValueError("Missing script METADATA: " + source)
            declared = header.group(1).strip()
        elif extension["kind"] == "plugin":
            manifests = [examples / source / name for name in ("manifest.json", "manifest.hjson")
                         if (examples / source / name).is_file()]
            if len(manifests) != 1:
                raise ValueError("Expected exactly one plugin manifest: " + source)
            declared = manifests[0].read_text(encoding="utf-8").strip()
        else:
            raise ValueError("Unknown extension kind: " + extension["kind"])
        if not declared or pack["id"] in metadata:
            raise ValueError("Empty or duplicate extension metadata: " + pack["id"])
        # Preserve HJSON as data. Android already uses the same parser for script metadata.
        metadata[pack["id"]] = declared
    return metadata


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, required=True)
    parser.add_argument("--examples", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    metadata = collect(json.loads(args.catalog.read_text(encoding="utf-8")), args.examples)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(metadata, ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"extension_metadata_entries": len(metadata), "bytes": args.output.stat().st_size}))


if __name__ == "__main__":
    main()
