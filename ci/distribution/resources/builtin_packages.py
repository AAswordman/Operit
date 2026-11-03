"""将白名单中的完整扩展产物固定为独立资源，内置身份由随 APK 分发的目录绑定。"""
from __future__ import annotations
import hashlib
import io
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile
from prepare_hub import ROOT, APK_FILES, digest, package
from builtin_source_snapshot import fingerprint


def whitelist() -> list[str]:
    return [line.strip() for line in (ROOT / 'tools/example_packages/packages_whitelist.txt').read_text().splitlines()
            if line.strip() and not line.lstrip().startswith('#')]


def source_fingerprint(source: str) -> str:
    entries = subprocess.check_output(['git', 'ls-files', '--stage', '-z', '--', 'examples/' + source,
        'examples/' + source.removesuffix('.js') + '.ts', 'examples/types', 'examples/tsconfig.json',
        'tools/example_packages/sync_example_packages.py', 'tools/example_packages/packages_whitelist.txt'], cwd=ROOT).split(b'\0')
    objects: dict[str, bytes] = {}
    for entry in entries:
        if not entry:
            continue
        metadata, path = entry.split(b'\t', 1)
        _, object_id, stage = metadata.split()
        if stage != b'0':
            raise ValueError('内置扩展源码存在未解决冲突：' + path.decode())
        objects[path.decode()] = object_id
    if not objects:
        raise ValueError('内置扩展缺少源码：' + source)

    # 构建会重写已跟踪的 JS/dist 文件；指纹必须读取索引快照，不能依赖生成后的工作树。
    names = sorted(objects)
    content = subprocess.check_output(['git', 'cat-file', '--batch'], cwd=ROOT,
        input=b'\n'.join(objects[name] for name in names) + b'\n')
    stream = io.BytesIO(content)
    files = []
    for name in names:
        header = stream.readline().split()
        if len(header) != 3 or header[0] != objects[name] or header[1] != b'blob':
            raise ValueError('内置扩展源码快照不可读：' + name)
        size = int(header[2])
        data = stream.read(size)
        if len(data) != size or stream.read(1) != b'\n':
            raise ValueError('内置扩展源码快照不完整：' + name)
        files.append((name, hashlib.sha256(data).hexdigest()))
    return fingerprint(dict(files), source)


def normalize_plugin(source: Path, destination: Path, reverse: bool) -> dict:
    with zipfile.ZipFile(source) as archive, zipfile.ZipFile(destination, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        names = archive.namelist()
        if len(names) != len(set(names)) or 'manifest.json' not in names:
            raise ValueError('ToolPkg 清单缺失或条目重复：' + source.name)
        manifest = json.loads(archive.read('manifest.json'))
        external = {'resources/apktool/' + name for name in APK_FILES} if reverse else set()
        if reverse:
            if manifest['toolpkg_id'] != 'com.operit.apk_reverse_toolkit' or not external.issubset(names):
                raise ValueError('APK 逆向插件缺少完整运行资源')
            for resource in manifest['resources']:
                if resource['path'] in external:
                    resource['external_group'] = 'apk-reverse'
        for name in sorted(names):
            if name.endswith('/') or name in external:
                continue
            if name.startswith('/') or '..' in Path(name).parts:
                raise ValueError('ToolPkg 路径无效：' + name)
            info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = archive.getinfo(name).external_attr
            data = json.dumps(manifest, ensure_ascii=False).encode() if name == 'manifest.json' else archive.read(name)
            target.writestr(info, data)
    return manifest


def extension_packs(output: Path, packages: Path) -> list[dict]:
    expected = {item if item.endswith('.js') else item + '.toolpkg' for item in whitelist()}
    actual = {p.name for p in packages.iterdir() if p.is_file() and p.suffix in {'.js', '.toolpkg'}}
    if actual != expected:
        raise ValueError('构建产物与内置白名单不一致：' + repr(actual ^ expected))
    result = []
    with tempfile.TemporaryDirectory(prefix='operit-builtin-packs-') as temporary:
        root = Path(temporary)
        for item in whitelist():
            source = packages / (item if item.endswith('.js') else item + '.toolpkg')
            if source.suffix == '.js':
                meta = source.read_text().split('*/', 1)[0]
                match = re.search(r"name[^a-zA-Z0-9_:]*:[^a-zA-Z0-9_]*([a-zA-Z0-9_.-]+)", meta)
                if not match:
                    raise ValueError('沙盒包缺少名称：' + source.name)
                names = [match.group(1)]
                group = 'sandbox-' + source.stem.replace('_', '-')
                title = names[0]
                files = [('packages/' + source.name, source)]
                kind = 'sandbox'
            else:
                normalized = root / source.name
                manifest = normalize_plugin(source, normalized, item == 'apktool')
                names = [manifest['toolpkg_id']] + [p['id'] for p in manifest.get('subpackages', [])]
                group = 'apk-reverse' if item == 'apktool' else 'plugin-' + item.replace('_', '-')
                title = manifest['display_name']
                if isinstance(title, dict):
                    title = title['zh']
                files = [('packages/' + source.name, normalized)]
                kind = 'plugin'
                if item == 'apktool':
                    with zipfile.ZipFile(source) as archive:
                        for name in APK_FILES:
                            relative = 'resources/apktool/' + name
                            data = archive.read(relative)
                            fixed = ROOT / 'examples/apktool' / relative
                            if data != fixed.read_bytes():
                                raise ValueError('逆向运行资源与源码不一致：' + name)
                            files.append((relative, fixed))
            extension = {'kind': kind, 'path': 'packages/' + source.name, 'packageNames': names,
                         'source': item, 'sourceFingerprint': source_fingerprint(item)}
            result.append(package(output, group, title, files, extension=extension))
    return result


def reuse_pack(base: Path, output: Path, group: str) -> dict:
    catalog = json.loads((base / 'offline-packs.json').read_text())
    spec = next(p for p in catalog['packs'] if p['id'] == group)
    archive = base / spec['archive']['path']
    if archive.stat().st_size != spec['archive']['bytes'] or digest(archive) != spec['archive']['sha256']:
        raise ValueError('原资源归档校验失败：' + group)
    with zipfile.ZipFile(archive) as package_file:
        expected = {f['path'] for f in spec['files']} | {'operit-resource.json'}
        if set(package_file.namelist()) != expected:
            raise ValueError('原资源文件清单不一致：' + group)
        for f in spec['files']:
            data = package_file.read(f['path'])
            if len(data) != f['bytes'] or hashlib.sha256(data).hexdigest() != f['sha256']:
                raise ValueError('原资源内容校验失败：' + f['path'])
    shutil.copyfile(archive, output / archive.name)
    spec['archive'].pop('url', None)
    return spec
