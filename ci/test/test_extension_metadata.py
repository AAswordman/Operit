from __future__ import annotations

import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "ci/distribution/resources"))
import prepare_extension_metadata


class ExtensionMetadataTest(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.examples = Path(temporary.name)

    def pack(self, source: str, kind: str = "sandbox", id: str = "sample") -> dict:
        return {"id": id, "extension": {"source": source, "kind": kind}}

    def collect(self, *packs: dict) -> dict[str, str]:
        return prepare_extension_metadata.collect({"packs": list(packs)}, self.examples)

    def test_script_body_is_excluded_and_hjson_is_preserved_as_data(self) -> None:
        header = '{name: "sample"\n description: {zh: "用途", en: "Purpose"}}'
        (self.examples / "sample.js").write_text('/* METADATA\n' + header + '\n*/\nthrow new Error("must not execute");')
        result = self.collect(self.pack("sample.js"))
        self.assertEqual({"sample": header}, result)
        self.assertNotIn("must not execute", json.dumps(result))

    def test_plugin_manifest_is_available_without_installing_the_plugin(self) -> None:
        folder = self.examples / "plugin"
        folder.mkdir()
        manifest = '{"description":{"zh":"插件用途","en":"Plugin purpose"}}'
        (folder / "manifest.json").write_text(manifest)
        self.assertEqual({"plugin-id": manifest}, self.collect(self.pack("plugin", "plugin", "plugin-id")))

    def test_missing_metadata_and_duplicate_ids_are_rejected(self) -> None:
        path = self.examples / "sample.js"
        path.write_text('module.exports = {};')
        with self.assertRaisesRegex(ValueError, "Missing script METADATA"):
            self.collect(self.pack("sample.js"))
        path.write_text('/* METADATA {description: "sample"} */')
        with self.assertRaisesRegex(ValueError, "duplicate"):
            self.collect(self.pack("sample.js"), self.pack("sample.js"))

    def test_source_path_cannot_escape_the_examples_directory(self) -> None:
        with self.assertRaisesRegex(ValueError, "Invalid extension source"):
            self.collect(self.pack("../sample.js"))


if __name__ == "__main__":
    unittest.main()
