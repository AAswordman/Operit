from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))

from check_storage_ownership import storage_ownership_errors  # noqa: E402


CATALOG = """\
package com.example.persistence

object PreferenceStoreCatalog {
    const val FIRST = "first"
    const val SECOND = "second"

    val all: List<String> = listOf(
        FIRST,
        SECOND
    )
}
"""

REGISTRY = """\
package com.example.persistence

object Registry {
    fun create() = PreferenceDataStoreFactory.create(produceFile = { TODO() })
}
"""


class StorageOwnershipCheckTest(unittest.TestCase):
    def create_repository(self, root: Path) -> Path:
        source = root / "app" / "src" / "main" / "java"
        persistence = (
            source
            / "com"
            / "ai"
            / "assistance"
            / "operit"
            / "data"
            / "persistence"
        )
        persistence.mkdir(parents=True)
        (persistence / "PreferenceStoreCatalog.kt").write_text(CATALOG, encoding="utf-8")
        (persistence / "RecoverablePreferencesDataStore.kt").write_text(
            REGISTRY,
            encoding="utf-8",
        )
        (source / "Owners.kt").write_text(
            """
            val first by recoverablePreferencesDataStore(name = "first")
            val second by recoverablePreferencesDataStore(name = "second")
            """,
            encoding="utf-8",
        )
        return source

    def test_valid_single_ownership_passes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self.create_repository(repository)
            self.assertEqual([], storage_ownership_errors(repository))

    def test_duplicate_owner_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            source = self.create_repository(repository)
            (source / "Duplicate.kt").write_text(
                'val duplicate by recoverablePreferencesDataStore(name = "first")',
                encoding="utf-8",
            )
            errors = storage_ownership_errors(repository)
            self.assertTrue(any("first has 2 owners" in error for error in errors))

    def test_direct_delegate_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            source = self.create_repository(repository)
            (source / "Direct.kt").write_text(
                'val direct by preferencesDataStore(name = "third")',
                encoding="utf-8",
            )
            errors = storage_ownership_errors(repository)
            self.assertTrue(any("direct preferencesDataStore" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
