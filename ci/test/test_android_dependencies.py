from __future__ import annotations

import stat
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))

from prepare_android_dependencies import (  # noqa: E402
    extract_archive,
    validate_member,
    verify_outputs,
)


class AndroidDependencyArchiveTest(unittest.TestCase):
    def test_regular_member_is_accepted(self) -> None:
        validate_member(zipfile.ZipInfo("app/libs/library.aar"), "app/libs")

    def test_root_ancestor_directory_is_accepted(self) -> None:
        validate_member(zipfile.ZipInfo("app/"), "app/libs")

    def test_root_ancestor_file_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "outside the allowed root"):
            validate_member(zipfile.ZipInfo("app"), "app/libs")

    def test_parent_traversal_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "escapes the repository"):
            validate_member(zipfile.ZipInfo("../outside.txt"), "app/libs")

    def test_absolute_path_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "escapes the repository"):
            validate_member(zipfile.ZipInfo("/tmp/outside.txt"), "app/libs")

    def test_other_repository_root_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "outside the allowed root"):
            validate_member(zipfile.ZipInfo("app/build.gradle.kts"), "app/libs")

    def test_symbolic_link_member_is_rejected(self) -> None:
        info = zipfile.ZipInfo("app/libs/library.aar")
        info.external_attr = (stat.S_IFLNK | 0o777) << 16

        with self.assertRaisesRegex(ValueError, "unsafe file type"):
            validate_member(info, "app/libs")

    def test_special_file_member_is_rejected(self) -> None:
        info = zipfile.ZipInfo("app/libs/library.aar")
        info.external_attr = (stat.S_IFIFO | 0o644) << 16

        with self.assertRaisesRegex(ValueError, "unsafe file type"):
            validate_member(info, "app/libs")

    def test_existing_symlink_in_destination_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repository = root / "repository"
            outside = root / "outside"
            repository.mkdir()
            outside.mkdir()
            (repository / "app").symlink_to(outside, target_is_directory=True)
            archive = root / "libs.zip"
            with zipfile.ZipFile(archive, "w") as stream:
                stream.writestr("app/libs/library.aar", b"library")

            with self.assertRaisesRegex(ValueError, "contains a symbolic link"):
                extract_archive(archive, repository)

    def test_library_verification_uses_current_regular_files(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repository = root / "repository"
            repository.mkdir()
            archive = root / "libs.zip"
            with zipfile.ZipFile(archive, "w") as stream:
                stream.mkdir("app/libs/fake.jar/")
                stream.writestr("app/libs/readme.txt", b"not a library")

            extracted = extract_archive(archive, repository)

            with self.assertRaisesRegex(ValueError, "did not provide"):
                verify_outputs("jvm", repository, extracted)


if __name__ == "__main__":
    unittest.main()
