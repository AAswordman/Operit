"""校验 ML Kit 原始 AAR，将模型资产与保留原接口的 SDK 分开准备。"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import urllib.request
import zipfile
ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'assets/mlkit-google-ocr-models/'

def catalog() -> list[dict]:
    data = json.loads((ROOT / 'app/config/mlkit-ocr-assets.json').read_text())
    if data['schemaVersion'] != 1:
        raise ValueError('不支持的 OCR 来源清单')
    return data['artifacts']

def verify(path: Path, spec: dict) -> None:
    with path.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    if path.stat().st_size != spec['bytes'] or digest != spec['sha256']:
        raise ValueError('ML Kit 原始 AAR 校验失败：' + path.name)

def build_models(output: Path, cache: Path) -> Path:
    cache.mkdir(parents=True, exist_ok=True)
    merged: dict[str, bytes] = {}
    notices: dict[str, bytes] = {}
    for spec in catalog():
        path = cache / f'{spec["artifact"]}-{spec["version"]}.aar'
        if not path.is_file():
            partial = path.with_suffix('.part')
            with urllib.request.urlopen(spec['url'], timeout=90) as response, partial.open('wb') as target:
                shutil.copyfileobj(response, target)
            verify(partial, spec)
            partial.replace(path)
        verify(path, spec)
        with zipfile.ZipFile(path) as archive:
            for entry in archive.infolist():
                if entry.filename.startswith(PREFIX) and not entry.is_dir():
                    data = archive.read(entry)
                    if entry.filename in merged and merged[entry.filename] != data:
                        raise ValueError('多语言模型的共用文件不一致：' + entry.filename)
                    merged[entry.filename] = data
            # 相同许可只保留一份，SDK 原归档中的版权说明仍随精简 AAR 保留。
            for name in ['third_party_licenses.txt', 'third_party_licenses.json']:
                data = archive.read(name)
                if name in notices and notices[name] != data:
                    raise ValueError('ML Kit 许可清单发生变化：' + name)
                notices[name] = data
    output.mkdir(parents=True, exist_ok=True)
    models = output / 'mlkit-models.apk'
    # 内层资产包采用存储模式，让 Android 的 openFd/mmap 仍可读取模型。
    with zipfile.ZipFile(models, 'w', compression=zipfile.ZIP_STORED) as archive:
        for name, data in sorted(merged.items()):
            info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data)
    for name, data in notices.items():
        (output / name).write_bytes(data)
    (output / 'NOTICE.txt').write_text('Google ML Kit 文字识别模型资产，来自固定版本 16.0.0 的五个原始 AAR。\n原始来源与摘要见 mlkit-ocr-assets.json。\n条款：https://developers.google.com/ml-kit/terms\n保留的第三方许可见同目录 third_party_licenses.txt。\n')
    shutil.copyfile(ROOT / 'app/config/mlkit-ocr-assets.json', output / 'mlkit-ocr-assets.json')
    print(json.dumps({'ocr_asset_files': len(merged), 'ocr_model_bytes': sum(map(len, merged.values()))}, ensure_ascii=False))
    return models

def strip_aars(sources: list[Path], output: Path) -> None:
    specs = {f'{s["artifact"]}-{s["version"]}.aar': s for s in catalog()}
    actual = {p.name: p for p in sources}
    if actual.keys() != specs.keys() or len(actual) != len(sources):
        raise ValueError('OCR SDK 依赖与固定来源清单不一致')
    output.mkdir(parents=True, exist_ok=True)
    for name, path in sorted(actual.items()):
        verify(path, specs[name])
        with zipfile.ZipFile(path) as original, zipfile.ZipFile(output / name, 'w') as target:
            removed = 0
            for entry in original.infolist():
                if entry.filename.startswith(PREFIX):
                    removed += 1
                    continue
                target.writestr(entry, original.read(entry))
            if not removed:
                raise ValueError('原始 AAR 没有预期的模型资产：' + name)
    for path in output.iterdir():
        if path.is_file() and path.name not in specs:
            path.unlink()

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--aar', action='append', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    strip_aars(args.aar, args.output)
