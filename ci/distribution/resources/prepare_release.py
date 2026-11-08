from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import shutil
import zipfile


ROOT = Path(__file__).resolve().parents[3]


def prepare(sources: Path, output: Path) -> None:
    catalog = ROOT / "app/config/offline-resources.properties"
    groups: dict[str, list[Path]] = {}
    output.mkdir(parents=True, exist_ok=True)
    for line in catalog.read_text().splitlines():
        if not line or line.startswith("#"):
            continue
        group, name, size, digest, source, license_note = line.split("|")
        if Path(name).name != name or name in {".", ".."}:
            raise ValueError(f"Invalid resource filename: {name}")
        resource = sources / name
        with resource.open("rb") as stream:
            actual_digest = hashlib.file_digest(stream, "sha256").hexdigest()
        if resource.stat().st_size != int(size) or actual_digest != digest:
            raise ValueError(f"Resource does not match pinned catalog: {name}")
        target = output / name
        shutil.copyfile(resource, target)
        groups.setdefault(group, []).append(target)
    for group, files in groups.items():
        with zipfile.ZipFile(output / f"operit-{group}-resources-v1.zip", "w", compression=zipfile.ZIP_STORED) as archive:
            for resource in files:
                info = zipfile.ZipInfo(resource.name, date_time=(2026, 1, 1, 0, 0, 0))
                info.external_attr = 0o100644 << 16
                info.file_size = resource.stat().st_size
                with resource.open("rb") as source, archive.open(info, "w") as target:
                    shutil.copyfileobj(source, target)
    shutil.copyfile(catalog, output / catalog.name)
    lines = []
    for resource in sorted(output.iterdir()):
        if resource.is_file() and resource.name != "SHA256SUMS.txt":
            with resource.open("rb") as stream:
                digest = hashlib.file_digest(stream, "sha256").hexdigest()
            lines.append(f"{digest}  {resource.name}")
    (output / "SHA256SUMS.txt").write_text("\n".join(lines) + "\n")
    print(f"Prepared {len(groups)} groups and {len(lines)} verified release assets")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--sources", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    prepare(arguments.sources, arguments.output)
