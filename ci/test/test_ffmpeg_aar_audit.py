from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = REPO_ROOT / "tools" / "ffmpeg" / "audit_android_aar.py"
SPEC = importlib.util.spec_from_file_location("audit_android_aar", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class FFmpegAarAuditTest(unittest.TestCase):
    def test_parent_traversal_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "unsafe AAR member"):
            MODULE.validate_member_path("../outside.so")

    def test_absolute_path_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "unsafe AAR member"):
            MODULE.validate_member_path("/tmp/outside.so")

    def test_missing_expected_libraries_is_rejected_before_readelf(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            aar = Path(directory) / "test.aar"
            with zipfile.ZipFile(aar, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"manifest")
                archive.writestr("jni/arm64-v8a/libavcodec.so", b"not-an-elf")
            with self.assertRaisesRegex(ValueError, "missing expected FFmpeg libraries"):
                MODULE.audit(aar, "readelf")

    def test_unexpected_abi_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            aar = Path(directory) / "test.aar"
            with zipfile.ZipFile(aar, "w") as archive:
                archive.writestr("jni/x86_64/libavcodec.so", b"not-an-elf")
            with self.assertRaisesRegex(ValueError, "unexpected FFmpeg AAR ABI"):
                MODULE.audit(aar, "readelf")

    def test_duplicate_member_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            aar = Path(directory) / "test.aar"
            with zipfile.ZipFile(aar, "w") as archive:
                archive.writestr("classes.jar", b"first")
                archive.writestr("classes.jar", b"second")
            with self.assertRaisesRegex(ValueError, "duplicate AAR member"):
                MODULE.audit(aar, "readelf")


if __name__ == "__main__":
    unittest.main()