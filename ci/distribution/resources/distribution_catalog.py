"""离线资源与内置市场独立目录，归档版本、校验和及发行标签保持共同验证。"""
import json
from pathlib import Path


def load_catalog(directory: Path) -> dict:
    resources = json.loads((directory / 'offline-packs.json').read_text())
    extensions = json.loads((directory / 'builtin-extensions.json').read_text())
    if resources['releaseTag'] != extensions['releaseTag'] or resources['schemaVersion'] != extensions['schemaVersion']:
        raise ValueError('资源与内置市场目录不是同一次准备')
    if any('extension' in p for p in resources['packs']) or any('extension' not in p for p in extensions['packs']):
        raise ValueError('扩展必须只出现在内置市场目录')
    # Preserve the original immutable fingerprint ordering: templates, extensions, shared runtime.
    common = {'engine-sherpa-onnx', 'ocr-google-mlkit'}
    return {**resources, 'packs': [p for p in resources['packs'] if p['id'] not in common] + extensions['packs'] +
            [p for p in resources['packs'] if p['id'] in common]}
