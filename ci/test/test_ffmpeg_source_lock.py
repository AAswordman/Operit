from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = REPO_ROOT / "tools" / "ffmpeg" / "apply_source_lock.py"
SPEC = importlib.util.spec_from_file_location("apply_source_lock", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class FFmpegSourceLockTest(unittest.TestCase):
    def write_lock(self, root: Path, body: str) -> Path:
        path = root / "source-lock.properties"
        path.write_text(body)
        return path

    def test_read_lock_requires_ffmpeg_kit(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            lock = self.write_lock(
                Path(directory),
                "ffmpeg|https://example.invalid/ffmpeg.git|n6.0|"
                + "a" * 40
                + "\n",
            )
            with self.assertRaisesRegex(ValueError, "ffmpeg-kit pin is required"):
                MODULE.read_lock(lock)

    def test_read_lock_rejects_invalid_commit(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            lock = self.write_lock(
                Path(directory),
                "ffmpeg-kit|https://example.invalid/kit.git|v6.0|not-a-commit\n",
            )
            with self.assertRaisesRegex(ValueError, "invalid commit"):
                MODULE.read_lock(lock)

    def test_read_lock_rejects_duplicate_component(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            line = (
                "ffmpeg-kit|https://example.invalid/kit.git|v6.0|" + "a" * 40 + "\n"
            )
            lock = self.write_lock(Path(directory), line + line)
            with self.assertRaisesRegex(ValueError, "duplicate component"):
                MODULE.read_lock(lock)

    def test_pin_source_table_uses_commit_and_repository(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "source.sh"
            source.write_text(
                "  ffmpeg)\n"
                '    SOURCE_REPO_URL="https://old.invalid/FFmpeg"\n'
                '    SOURCE_ID="n6.0"\n'
                '    SOURCE_TYPE="TAG"\n'
                "    ;;\n"
            )
            pin = MODULE.SourcePin(
                "ffmpeg",
                "https://example.invalid/FFmpeg.git",
                "n6.0",
                "b" * 40,
            )
            MODULE.pin_source_table(source, {"ffmpeg": pin})
            text = source.read_text()
            self.assertIn('SOURCE_REPO_URL="https://example.invalid/FFmpeg"', text)
            self.assertIn(f'SOURCE_ID="{"b" * 40}"', text)
            self.assertIn('SOURCE_TYPE="COMMIT"', text)

    def test_pin_source_table_rejects_missing_component(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "source.sh"
            source.write_text("#!/bin/bash\n")
            pin = MODULE.SourcePin(
                "ffmpeg",
                "https://example.invalid/FFmpeg.git",
                "n6.0",
                "b" * 40,
            )
            with self.assertRaisesRegex(ValueError, "found 0"):
                MODULE.pin_source_table(source, {"ffmpeg": pin})


if __name__ == "__main__":
    unittest.main()