from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = REPO_ROOT / "tools" / "ffmpeg" / "verify_license_lock.py"
SPEC = importlib.util.spec_from_file_location("verify_license_lock", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class FFmpegLicenseLockTest(unittest.TestCase):
    def write(self, root: Path, name: str, content: str) -> Path:
        path = root / name
        path.write_text(content)
        return path

    def test_repository_locks_have_complete_coverage(self) -> None:
        review = MODULE.validate(
            REPO_ROOT / "tools" / "ffmpeg" / "source-lock.properties",
            REPO_ROOT / "tools" / "ffmpeg" / "license-lock.properties",
        )
        self.assertEqual(
            [item.component for item in review],
            ["ffmpeg", "ffmpeg-kit", "gnutls"],
        )

    def test_missing_license_component_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = self.write(
                root,
                "source.properties",
                "component|https://example.invalid/repo.git|v1|" + "a" * 40 + "\n",
            )
            license_lock = self.write(root, "license.properties", "")
            with self.assertRaisesRegex(ValueError, "component mismatch"):
                MODULE.validate(source, license_lock)

    def test_unsafe_evidence_path_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = self.write(
                Path(directory),
                "license.properties",
                "component|MIT|runtime|verified|../LICENSE|" + "b" * 64 + "\n",
            )
            with self.assertRaisesRegex(ValueError, "unsafe evidence path"):
                MODULE.read_license_evidence(path)

    def test_unknown_review_status_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = self.write(
                Path(directory),
                "license.properties",
                "component|MIT|runtime|done|LICENSE|" + "b" * 64 + "\n",
            )
            with self.assertRaisesRegex(ValueError, "invalid status"):
                MODULE.read_license_evidence(path)


if __name__ == "__main__":
    unittest.main()