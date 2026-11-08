"""构建可独立分发的资源包及 APK 内的逐文件校验目录。"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import tempfile
import urllib.request
import zipfile
from prepare_ocr_sdk import build_models

ROOT = Path(__file__).resolve().parents[3]
import sys
sys.path.insert(0, str(ROOT / "ci/distribution"))
from repositories.config import RESOURCE_REPOSITORY
ENGINE_URL = 'https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.2/sherpa-onnx-v1.13.2-android.tar.bz2'
ENGINE_HASH = 'fc4d17941152941a883b0cfabfc9acac118682324e9f97df6c1ae1360bc7bc8e'
ENGINE_BYTES = 52236308
TEMPLATES = ['android', 'flutter', 'go', 'java', 'node', 'office', 'python', 'typescript', 'web']
APK_FILES = ['apktool-runtime-android.jar', 'android-framework.jar', 'jadx-runtime-android.jar', 'apk-reverse-helper-runtime-android.jar']


def digest(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def write_zip(target: Path, files: list[tuple[str, Path]], metadata: dict) -> None:
    with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for relative, source in sorted(files):
            info = zipfile.ZipInfo(relative, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (0o100755 if os.access(source, os.X_OK) else 0o100644) << 16
            with source.open('rb') as stream, archive.open(info, 'w') as output:
                shutil.copyfileobj(stream, output)
        info = zipfile.ZipInfo('operit-resource.json', date_time=(2026, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o100644 << 16
        archive.writestr(info, json.dumps(metadata, sort_keys=True, ensure_ascii=False).encode())


def package(output: Path, group: str, name: str, sources: list[tuple[str, Path]], load_order: list[str] | None = None, extension: dict | None = None) -> dict:
    specs = [{'path': relative, 'bytes': source.stat().st_size, 'sha256': digest(source), 'executable': os.access(source, os.X_OK)} for relative, source in sorted(sources)]
    identity = {'id': group, 'files': specs, 'loadOrder': load_order or []}
    if extension is not None:
        identity['extension'] = extension
    content = json.dumps(identity, sort_keys=True).encode()
    version = hashlib.sha256(content).hexdigest()[:20]
    metadata = {'id': group, 'name': name, 'version': version, 'files': specs, 'loadOrder': load_order or []}
    if extension is not None:
        metadata['extension'] = extension
    archive = output / f'operit-{group}-{version}.zip'
    write_zip(archive, sources, metadata)
    metadata['archive'] = {'path': archive.name, 'bytes': archive.stat().st_size, 'sha256': digest(archive)}
    return metadata


def engine_libraries(cache: Path, work: Path) -> tuple[list[tuple[str, Path]], list[str]]:
    cache.mkdir(parents=True, exist_ok=True)
    archive = cache / ('sherpa-onnx-1.13.2-' + ENGINE_HASH[:16] + '.tar.bz2')
    if not archive.is_file() or archive.stat().st_size != ENGINE_BYTES or digest(archive) != ENGINE_HASH:
        partial = archive.with_suffix('.part')
        with urllib.request.urlopen(ENGINE_URL, timeout=120) as response, partial.open('wb') as output:
            shutil.copyfileobj(response, output)
        if partial.stat().st_size != ENGINE_BYTES or digest(partial) != ENGINE_HASH:
            raise ValueError('Sherpa ONNX 固定归档校验失败')
        partial.replace(archive)
    version = subprocess.check_output(['patchelf', '--version'], text=True).strip()
    if version != 'patchelf 0.18.0':
        raise ValueError('需要 patchelf 0.18.0 以保持资源包输出可重现：' + version)
    original = work / 'original'
    original.mkdir()
    with tarfile.open(archive, 'r:bz2') as source:
        members = {m.name: m for m in source.getmembers()}
        selected = [m for m in members.values() if '/arm64-v8a/' in '/' + m.name and m.name.endswith('.so')]
        if not any(Path(m.name).name == 'libsherpa-onnx-jni.so' for m in selected):
            raise ValueError('归档缺少 Android arm64 JNI 引擎')
        for member in selected:
            with source.extractfile(member) as stream, (original / Path(member.name).name).open('wb') as destination:
                shutil.copyfileobj(stream, destination)
    names = {p.name: 'liboperit_' + p.name.removeprefix('lib').replace('-', '_').replace('+', 'x') for p in original.iterdir()}
    destinations: dict[str, Path] = {}
    dependencies: dict[str, list[str]] = {}
    for old, renamed in names.items():
        target = work / renamed
        shutil.copyfile(original / old, target)
        needed = subprocess.check_output(['patchelf', '--print-needed', str(target)], text=True).splitlines()
        replaced = []
        for dependency in needed:
            key = dependency if dependency in names else next((name for name in names if dependency.startswith(name + '.')), None)
            if key:
                subprocess.run(['patchelf', '--replace-needed', dependency, names[key], str(target)], check=True)
                replaced.append(names[key])
        subprocess.run(['patchelf', '--set-soname', renamed, str(target)], check=True)
        dependencies[renamed] = replaced
        destinations[renamed] = target
    order: list[str] = []
    visiting: set[str] = set()
    def visit(name: str) -> None:
        if name in order:
            return
        if name in visiting:
            raise ValueError('原生运行库存在循环依赖')
        visiting.add(name)
        for child in dependencies[name]:
            visit(child)
        visiting.remove(name)
        order.append(name)
    for name in sorted(destinations):
        visit(name)
    # 为共用引擎的 ORT 和 C++ 库设置独立 SONAME，避免与宿主 VAD 的 ORT 相互覆盖。
    return [(name, destinations[name]) for name in sorted(destinations)], order


def prepare(output: Path, cache: Path, packages: Path, base_assets: Path | None = None, source_snapshot: Path | None = None) -> dict:
    output.mkdir(parents=True, exist_ok=True)
    groups = []
    for template in TEMPLATES:
        source = ROOT / 'app/src/main/assets/templates' / template
        files = [(p.relative_to(source).as_posix(), p) for p in source.rglob('*') if p.is_file()]
        if not files:
            raise ValueError('工程模板为空：' + template)
        groups.append(package(output, 'template-' + template, template, files))
    from builtin_packages import extension_packs, reuse_pack
    groups += extension_packs(output, packages)
    if base_assets is not None:
        # 首轮引擎与 OCR 内容保持不变时，复用经过逐项摘要核对的已发布归档。
        for group in ['engine-sherpa-onnx', 'ocr-google-mlkit']:
            groups.append(reuse_pack(base_assets, output, group))
    else:
        with tempfile.TemporaryDirectory(prefix='operit-sherpa-package-') as directory:
            sources, order = engine_libraries(cache, Path(directory))
            sources += [("licenses/" + path.name, path) for path in sorted((ROOT / "app/src/main/assets/licenses").glob("*-LICENSE.txt"))]
            groups.append(package(output, 'engine-sherpa-onnx', 'Sherpa ONNX 1.13.2', sources, order))
        with tempfile.TemporaryDirectory(prefix='operit-ocr-models-') as directory:
            ocr_root = Path(directory)
            build_models(ocr_root, cache)
            groups.append(package(output, 'ocr-google-mlkit', 'Google ML Kit 文字识别', [(p.name, p) for p in ocr_root.iterdir() if p.is_file()]))
    fingerprint = hashlib.sha256(json.dumps(groups, sort_keys=True, ensure_ascii=False).encode()).hexdigest()[:20]
    tag = 'resources-v3-' + fingerprint
    for group in groups:
        group['archive']['url'] = f'https://github.com/{RESOURCE_REPOSITORY}/releases/download/{tag}/{group["archive"]["path"]}'
    result = {'schemaVersion': 1, 'releaseTag': tag, 'packs': groups}
    if source_snapshot is not None:
        from builtin_source_snapshot import create_snapshot, fingerprint as source_snapshot_fingerprint
        revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
        snapshot = create_snapshot(ROOT, [group['extension']['source'] for group in groups if 'extension' in group], tag, revision)
        for group in groups:
            extension = group.get('extension')
            if extension is not None and extension['sourceFingerprint'] != source_snapshot_fingerprint(dict(snapshot['files']), extension['source']):
                raise ValueError('准备资源前必须提交源码输入：' + extension['source'])
        source_snapshot.parent.mkdir(parents=True, exist_ok=True)
        source_snapshot.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2) + '\n')
    metadata = output / 'offline-packs.json'
    metadata.write_text(json.dumps({**result, 'packs': [g for g in groups if 'extension' not in g]}, ensure_ascii=False, indent=2) + '\n')
    builtin = output / 'builtin-extensions.json'
    builtin.write_text(json.dumps({**result, 'packs': [g for g in groups if 'extension' in g]}, ensure_ascii=False, indent=2) + '\n')
    sums = [f'{digest(path)}  {path.name}' for path in sorted(output.glob('*.zip'))]
    sums.append(f'{digest(metadata)}  {metadata.name}')
    sums.append(f'{digest(builtin)}  {builtin.name}')
    (output / 'SHA256SUMS.txt').write_text('\n'.join(sums) + '\n')
    print(json.dumps({'tag': tag, 'packs': len(groups), 'unpacked_bytes': sum(f['bytes'] for g in groups if not g['id'].startswith('engine-') for f in g['files']), 'download_bytes': sum(g['archive']['bytes'] for g in groups)}, ensure_ascii=False))
    return result


def slim_assets(assets: Path) -> None:
    # 默认 APK 不保留内置扩展；只处理生成目录，白名单源码与正式分发包不变。
    packages = assets / 'packages'
    removed = 0
    if packages.is_dir():
        for candidate in packages.iterdir():
            if candidate.is_file() and candidate.suffix.lower() in {'.js', '.toolpkg'}:
                candidate.unlink()
                removed += 1
    print(json.dumps({'builtin_packages_externalized': removed}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path)
    parser.add_argument('--cache', type=Path, default=ROOT / 'manual-deps/offline-cache')
    parser.add_argument('--slim-assets', type=Path)
    parser.add_argument('--packages', type=Path)
    parser.add_argument('--base-assets', type=Path)
    parser.add_argument('--source-snapshot', type=Path, help='单独写入随源码维护的固定资源输入摘要')
    args = parser.parse_args()
    if args.slim_assets:
        slim_assets(args.slim_assets)
    else:
        if not args.output:
            parser.error('需要 --output')
        if not args.packages:
            parser.error('需要 --packages 指定已构建的完整白名单扩展产物')
        prepare(args.output, args.cache, args.packages, args.base_assets, args.source_snapshot)
