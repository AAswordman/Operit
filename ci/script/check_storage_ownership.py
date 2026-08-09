from __future__ import annotations

import argparse
import re
from collections import Counter
from pathlib import Path


CATALOG_PATH = Path(
    "app/src/main/java/com/ai/assistance/operit/data/persistence/PreferenceStoreCatalog.kt"
)
REGISTRY_PATH = Path(
    "app/src/main/java/com/ai/assistance/operit/data/persistence/RecoverablePreferencesDataStore.kt"
)
SOURCE_ROOT = Path("app/src/main/java")

CATALOG_CONSTANT = re.compile(
    r'^\s*const\s+val\s+([A-Z][A-Z0-9_]*)\s*=\s*"([^"]+)"',
    re.MULTILINE,
)
CATALOG_ALL = re.compile(
    r"val\s+all\s*:\s*List<String>\s*=\s*listOf\((.*?)\)",
    re.DOTALL,
)
RECOVERABLE_DECLARATION = re.compile(
    r'recoverablePreferencesDataStore\s*\(\s*name\s*=\s*"([^"]+)"\s*\)',
    re.DOTALL,
)
DIRECT_DELEGATE = re.compile(r"(?<![A-Za-z0-9_])preferencesDataStore\s*\(")
DIRECT_FACTORY = re.compile(r"PreferenceDataStoreFactory\s*\.\s*create\s*\(")


def storage_ownership_errors(repository: Path) -> list[str]:
    catalog_file = repository / CATALOG_PATH
    source_root = repository / SOURCE_ROOT
    if not catalog_file.is_file():
        return [f"missing Preferences catalog: {CATALOG_PATH.as_posix()}"]
    if not source_root.is_dir():
        return [f"missing Kotlin source root: {SOURCE_ROOT.as_posix()}"]

    errors: list[str] = []
    catalog_text = catalog_file.read_text(encoding="utf-8")
    constants = CATALOG_CONSTANT.findall(catalog_text)
    constant_names = [name for name, _ in constants]
    catalog_names = [value for _, value in constants]

    duplicate_catalog_names = sorted(
        name for name, count in Counter(catalog_names).items() if count > 1
    )
    if duplicate_catalog_names:
        errors.append(
            "duplicate Preferences names in catalog: " + ", ".join(duplicate_catalog_names)
        )

    all_match = CATALOG_ALL.search(catalog_text)
    if all_match is None:
        errors.append("PreferenceStoreCatalog.all must be a literal listOf declaration")
    else:
        all_constants = re.findall(r"\b[A-Z][A-Z0-9_]*\b", all_match.group(1))
        if all_constants != constant_names:
            errors.append(
                "PreferenceStoreCatalog.all must list every catalog constant exactly once "
                "and in declaration order"
            )

    declarations: list[tuple[str, Path]] = []
    factory_locations: list[Path] = []
    for source_file in sorted(source_root.rglob("*.kt")):
        text = source_file.read_text(encoding="utf-8")
        relative = source_file.relative_to(repository)
        if DIRECT_DELEGATE.search(text):
            errors.append(f"direct preferencesDataStore declaration: {relative.as_posix()}")
        for _ in DIRECT_FACTORY.finditer(text):
            factory_locations.append(relative)
        declarations.extend(
            (store_name, relative)
            for store_name in RECOVERABLE_DECLARATION.findall(text)
        )

    if factory_locations != [REGISTRY_PATH]:
        rendered = ", ".join(path.as_posix() for path in factory_locations) or "none"
        errors.append(
            "PreferenceDataStoreFactory.create must occur exactly once in the registry; "
            f"found: {rendered}"
        )

    declaration_counts = Counter(name for name, _ in declarations)
    for store_name in sorted(set(catalog_names) - set(declaration_counts)):
        errors.append(f"cataloged Preferences store has no owner: {store_name}")
    for store_name in sorted(set(declaration_counts) - set(catalog_names)):
        locations = ", ".join(
            path.as_posix() for name, path in declarations if name == store_name
        )
        errors.append(f"uncataloged Preferences store {store_name}: {locations}")
    for store_name, count in sorted(declaration_counts.items()):
        if count > 1:
            locations = ", ".join(
                path.as_posix() for name, path in declarations if name == store_name
            )
            errors.append(
                f"Preferences store {store_name} has {count} owners: {locations}"
            )

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify single ownership of every Preferences DataStore file."
    )
    parser.add_argument(
        "--repository",
        type=Path,
        default=Path(__file__).resolve().parents[2],
        help="Repository root. Defaults to the checkout containing this script.",
    )
    args = parser.parse_args()

    errors = storage_ownership_errors(args.repository.resolve())
    if errors:
        for error in errors:
            print(f"storage ownership error: {error}")
        return 1

    print("Storage ownership check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
