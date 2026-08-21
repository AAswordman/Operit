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
SOURCE_ROOT = Path("app/src/main")

CATALOG_CONSTANT = re.compile(
    r'^\s*const\s+val\s+([A-Z][A-Z0-9_]*)\s*=\s*"([^"]+)"',
    re.MULTILINE,
)
CATALOG_LIST = re.compile(
    r"val\s+(managed|recoverable)\s*:\s*List<String>\s*=\s*listOf\((.*?)\)",
    re.DOTALL,
)
RECOVERABLE_DECLARATION = re.compile(
    r'recoverablePreferencesDataStore\s*\(\s*name\s*=\s*"([^"]+)"\s*\)',
    re.DOTALL,
)
MANAGED_DECLARATION = re.compile(
    r'managedPreferencesDataStore\s*\(\s*name\s*=\s*"([^"]+)"\s*\)',
    re.DOTALL,
)
DIRECT_DELEGATE = re.compile(r"(?<![A-Za-z0-9_])preferencesDataStore\s*\(")
DIRECT_FACTORY = re.compile(r"PreferenceDataStoreFactory\s*\.\s*create\s*\(")
DATASTORE_ALIAS = re.compile(
    r"^\s*import\s+androidx\.datastore\.preferences\.preferencesDataStore\s+as\s+",
    re.MULTILINE,
)
FACTORY_ALIAS = re.compile(
    r"^\s*import\s+androidx\.datastore\.preferences\.core\.PreferenceDataStoreFactory"
    r"\s+as\s+",
    re.MULTILINE,
)


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

    catalog_lists = {
        list_name: re.findall(r"\b[A-Z][A-Z0-9_]*\b", body)
        for list_name, body in CATALOG_LIST.findall(catalog_text)
    }
    managed_constants = catalog_lists.get("managed")
    recoverable_constants = catalog_lists.get("recoverable")
    if managed_constants is None:
        errors.append("PreferenceStoreCatalog.managed must be a literal listOf declaration")
        managed_names: list[str] = []
    else:
        values_by_constant = dict(constants)
        managed_names = [
            values_by_constant[name]
            for name in managed_constants
            if name in values_by_constant
        ]
        if managed_constants != constant_names:
            errors.append(
                "PreferenceStoreCatalog.managed must list every catalog constant exactly once "
                "and in declaration order"
            )
    if recoverable_constants is None:
        errors.append("PreferenceStoreCatalog.recoverable must be a literal listOf declaration")
        recoverable_names: list[str] = []
    else:
        values_by_constant = dict(constants)
        unknown_recoverable = [
            name for name in recoverable_constants if name not in values_by_constant
        ]
        if unknown_recoverable:
            errors.append(
                "PreferenceStoreCatalog.recoverable contains unknown constants: "
                + ", ".join(unknown_recoverable)
            )
        recoverable_names = [
            values_by_constant[name]
            for name in recoverable_constants
            if name in values_by_constant
        ]
        if len(recoverable_names) != len(set(recoverable_names)):
            errors.append("PreferenceStoreCatalog.recoverable contains duplicate stores")
        if not set(recoverable_names).issubset(set(managed_names)):
            errors.append("PreferenceStoreCatalog.recoverable must be a subset of managed")
        managed_only_names = set(managed_names) - set(recoverable_names)
        token_stats_name = dict(constants).get("TOKEN_STATS")
        if token_stats_name is not None and managed_only_names != {token_stats_name}:
            errors.append(
                "PreferenceStoreCatalog managed-only set must contain only TOKEN_STATS"
            )

    declarations: list[tuple[str, Path]] = []
    recoverable_declarations: list[tuple[str, Path]] = []
    managed_declarations: list[tuple[str, Path]] = []
    factory_locations: list[Path] = []
    source_files = sorted(source_root.rglob("*.kt")) + sorted(source_root.rglob("*.java"))
    for source_file in source_files:
        text = source_file.read_text(encoding="utf-8")
        relative = source_file.relative_to(repository)
        if DATASTORE_ALIAS.search(text):
            errors.append(f"aliased preferencesDataStore import: {relative.as_posix()}")
        if FACTORY_ALIAS.search(text):
            errors.append(f"aliased PreferenceDataStoreFactory import: {relative.as_posix()}")
        if DIRECT_DELEGATE.search(text):
            errors.append(f"direct preferencesDataStore declaration: {relative.as_posix()}")
        for _ in DIRECT_FACTORY.finditer(text):
            factory_locations.append(relative)
        file_recoverable = [
            (store_name, relative)
            for store_name in RECOVERABLE_DECLARATION.findall(text)
        ]
        file_managed = [
            (store_name, relative)
            for store_name in MANAGED_DECLARATION.findall(text)
        ]
        recoverable_declarations.extend(file_recoverable)
        managed_declarations.extend(file_managed)
        declarations.extend(file_recoverable + file_managed)

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

    declared_recoverable = {name for name, _ in recoverable_declarations}
    declared_managed_only = {name for name, _ in managed_declarations}
    for store_name in sorted(set(recoverable_names) - declared_recoverable):
        if store_name in declaration_counts:
            errors.append(f"recoverable Preferences store uses managed-only owner: {store_name}")
    for store_name in sorted(declared_recoverable - set(recoverable_names)):
        errors.append(f"managed-only Preferences store uses recovery owner: {store_name}")

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
