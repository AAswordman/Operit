"""按源码核对固定资源目录，不下载模型，不构建 APK。"""
from __future__ import annotations
import hashlib
import json
from builtin_packages import whitelist, source_fingerprint
from builtin_source_snapshot import load_snapshot, fingerprint as source_snapshot_fingerprint
from pathlib import Path
ROOT = Path(__file__).resolve().parents[3]
import sys
sys.path.insert(0, str(ROOT / "ci/distribution"))
from repositories.config import RESOURCE_REPOSITORY
TEMPLATES = ['android', 'flutter', 'go', 'java', 'node', 'office', 'python', 'typescript', 'web']

def sha(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def main(*, current_source: bool = False) -> None:
    catalog = json.loads((ROOT / 'app/config/offline-packs.json').read_text())
    snapshot = load_snapshot(ROOT / 'app/config/offline-pack-sources.json', catalog['releaseTag'])
    sources = snapshot['sources']
    source_files = dict(snapshot['files'])
    if current_source and sources != whitelist():
        raise ValueError('当前内置白名单与固定资源源码快照不一致，必须重新准备资源')
    packs = catalog['packs']
    extension_ids = {'sandbox-' + Path(name).stem.replace('_', '-') if name.endswith('.js') else 'apk-reverse' if name == 'apktool' else 'plugin-' + name.replace('_', '-') for name in sources}
    expected_ids = {'template-' + name for name in TEMPLATES} | extension_ids | {'engine-sherpa-onnx', 'ocr-google-mlkit'}
    if catalog['schemaVersion'] != 1 or {p['id'] for p in packs} != expected_ids or len(packs) != len(expected_ids):
        raise ValueError('固定资源组不完整或存在重复组')
    checked = 0
    for pack in packs:
        source_root = None
        if pack['id'].startswith('template-'):
            source_root = ROOT / 'app/src/main/assets/templates' / pack['id'].removeprefix('template-')
        paths = [f['path'] for f in pack['files']]
        if len(paths) != len(set(paths)):
            raise ValueError('资源文件重复：' + pack['id'])
        if source_root:
            for spec in pack['files']:
                source = source_root / spec['path']
                if not source.is_file() or source.stat().st_size != spec['bytes'] or sha(source) != spec['sha256']:
                    raise ValueError('源码资源变动后必须重新准备并发布固定资源版本：' + str(source))
                checked += 1
            if pack['id'].startswith('template-') and set(paths) != {p.relative_to(source_root).as_posix() for p in source_root.rglob('*') if p.is_file()}:
                raise ValueError('工程模板文件增删后未更新清单：' + pack['id'])
        identity = {'id': pack['id'], 'files': pack['files'], 'loadOrder': pack['loadOrder']}
        extension = pack.get('extension')
        if extension is not None:
            if extension['source'] not in sources or extension['sourceFingerprint'] != source_snapshot_fingerprint(source_files, extension['source']):
                raise ValueError('固定资源源码快照与扩展指纹不一致：' + pack['id'])
            if current_source and extension['sourceFingerprint'] != source_fingerprint(extension['source']):
                raise ValueError('内置扩展源码变动后必须重新准备并发布：' + pack['id'])
            if extension['path'] not in paths or not extension['packageNames'] or extension['kind'] not in {'plugin', 'sandbox'}:
                raise ValueError('内置扩展安装入口无效：' + pack['id'])
            identity['extension'] = extension
        content = json.dumps(identity, sort_keys=True).encode()
        if hashlib.sha256(content).hexdigest()[:20] != pack['version']:
            raise ValueError('资源版本与内容指纹不一致：' + pack['id'])
        expected_url = f'https://github.com/{RESOURCE_REPOSITORY}/releases/download/{catalog["releaseTag"]}/{pack["archive"]["path"]}'
        if pack['archive']['url'] != expected_url:
            raise ValueError('固定资源地址与发行标签不一致')
    without_urls = [{**p, 'archive': {k: v for k, v in p['archive'].items() if k != 'url'}} for p in packs]
    fingerprint = hashlib.sha256(json.dumps(without_urls, sort_keys=True, ensure_ascii=False).encode()).hexdigest()[:20]
    if catalog['releaseTag'] != 'resources-v2-' + fingerprint:
        raise ValueError('资源发行标签与目录内容不一致')
    print(json.dumps({'packs': len(packs), 'source_files_verified': checked, 'tag': catalog['releaseTag']}, ensure_ascii=False))

if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--current-source', action='store_true', help='发布前额外校验当前索引源码')
    main(current_source=parser.parse_args().current_source)
