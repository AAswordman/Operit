from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import zipfile


ROOT = Path(__file__).resolve().parents[3]


def main() -> None:
    apks = sorted((ROOT / "app/build/outputs/apk").rglob("*.apk"))
    if not apks:
        raise ValueError("No built APK was found")
    manifest = (ROOT / "app/config/offline-resources.properties").read_bytes()
    vad = next(
        line.split("|") for line in (ROOT / "app/config/stt-model-assets.properties").read_text().splitlines()
        if line.startswith("models/silero_vad.onnx|")
    )
    summaries = []
    for apk in apks:
        with zipfile.ZipFile(apk) as package:
            categories = {name: {"compressed": 0, "uncompressed": 0, "entries": 0}
                          for name in ("dex", "native", "assets", "resources", "other")}
            for entry in package.infolist():
                name = entry.filename
                category = ("dex" if name.endswith(".dex") else "native" if name.startswith("lib/")
                            else "assets" if name.startswith("assets/") else "resources"
                            if name.startswith("res/") or name == "resources.arsc" else "other")
                categories[category]["compressed"] += entry.compress_size
                categories[category]["uncompressed"] += entry.file_size
                categories[category]["entries"] += 1
            report_dir = ROOT / "app/build/reports/apk-size"
            report_dir.mkdir(parents=True, exist_ok=True)
            report = {"apk": str(apk.relative_to(ROOT)), "bytes": apk.stat().st_size,
                      "categories": categories,
                      "largest_entries": [{"name": item.filename, "compressed": item.compress_size,
                                           "uncompressed": item.file_size}
                                          for item in sorted(package.infolist(), key=lambda item: item.compress_size, reverse=True)[:20]]}
            (report_dir / (apk.parent.name + "-" + apk.name + ".json")).write_text(
                json.dumps(report, ensure_ascii=False, indent=2) + "\n")
            forbidden = [
                name for name in package.namelist()
                if name.startswith(("assets/packages/", "assets/subpack/", "assets/models/sherpa-ncnn-", "assets/templates/", "assets/mlkit-google-ocr-models/"))
                or name == "assets/ubuntu-noble-aarch64-pd-v4.18.0.tar.xz"
            ]
            if forbidden:
                raise ValueError(f"Large resources were bundled in {apk.name}: {forbidden}")
            if package.read("assets/offline-resources.properties") != manifest:
                raise ValueError("Packaged resource catalog differs from source")
            for catalog_name in ["local-speech-models.json", "offline-packs.json", "builtin-extensions.json"]:
                if package.read("assets/" + catalog_name) != (ROOT / "app/config" / catalog_name).read_bytes():
                    raise ValueError("Packaged hub catalog differs from source: " + catalog_name)
            # 内置身份与安装入口由固定目录保留，默认 APK 不包含脚本或插件本体。
            catalog = json.loads(package.read("assets/offline-packs.json"))
            extensions = [p["extension"] for p in json.loads(package.read("assets/builtin-extensions.json"))["packs"]]
            if len(extensions) != 40 or sum(e["kind"] == "plugin" for e in extensions) != 10:
                raise ValueError("内置扩展目录不完整")
            vad_content = package.read("assets/models/silero_vad.onnx")
            if len(vad_content) != int(vad[2]) or hashlib.sha256(vad_content).hexdigest() != vad[3]:
                raise ValueError("Shared Silero VAD is missing or failed verification")
        summary = f"{apk.relative_to(ROOT)}: {apk.stat().st_size:,} bytes ({apk.stat().st_size / 1048576:.2f} MiB); 外置资源未打包，共用 VAD 校验通过。"
        print(summary)
        breakdown = "；".join(f"{key} {value['compressed'] / 1048576:.2f} MiB" for key, value in categories.items())
        summaries.append(summary + "\n\n压缩条目分项：" + breakdown)
    if step_summary := os.environ.get("GITHUB_STEP_SUMMARY"):
        with Path(step_summary).open("a") as output:
            output.write("\n" + "\n\n".join(summaries) + "\n")


if __name__ == "__main__":
    main()
