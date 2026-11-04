from pathlib import Path
import argparse
import hashlib
import json
import shutil
import struct
import zipfile


ROOT_NAME = 'terminal_commands'
TARGET = 'aarch64-unknown-linux-gnu'
BINARY_PATH = 'aarch64/apply_patch'


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def verify_elf(data):
    if (len(data) < 64 or data[:6] != b'\x7fELF\x02\x01'
            or struct.unpack_from('<H', data, 18)[0] != 183
            or struct.unpack_from('<H', data, 16)[0] not in (2, 3)):
        raise ValueError('需要 ARM64 Linux ELF 命令产物')


def verify_contents(manifest, binary, installer):
    if (manifest['format'] != 1 or manifest['target'] != TARGET
            or manifest['binary']['file'] != BINARY_PATH
            or manifest['installer']['file'] != 'install.sh'):
        raise ValueError('终端命令资源清单无效')
    verify_elf(binary)
    if sha256(binary) != manifest['binary']['sha256']:
        raise ValueError('终端命令二进制校验失败')
    if sha256(installer) != manifest['installer']['sha256']:
        raise ValueError('终端命令安装脚本校验失败')
    if installer != Path(__file__).with_name('install.sh').read_bytes():
        raise ValueError('终端命令安装脚本与源码不一致')


def verify_assets(output):
    root = output / ROOT_NAME
    manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
    verify_contents(manifest, (root / BINARY_PATH).read_bytes(), (root / 'install.sh').read_bytes())
    for name in ('LICENSE', 'NOTICE'):
        if (root / name).read_bytes() != Path(__file__).with_name(name).read_bytes():
            raise ValueError('终端命令许可文件与源码不一致')
    return manifest


def package_assets(binary, output):
    data = binary.read_bytes()
    verify_elf(data)
    root = output / ROOT_NAME
    target = root / BINARY_PATH
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(binary, target)
    installer = Path(__file__).with_name('install.sh')
    shutil.copyfile(installer, root / 'install.sh')
    for name in ('LICENSE', 'NOTICE'):
        shutil.copyfile(Path(__file__).with_name(name), root / name)
    manifest = {
        'format': 1,
        'target': TARGET,
        'binary': {'file': BINARY_PATH, 'sha256': sha256(data)},
        'installer': {'file': 'install.sh', 'sha256': sha256(installer.read_bytes())},
    }
    (root / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    verify_assets(output)
    print(json.dumps({'output': str(root), 'sha256': manifest['binary']['sha256'], 'bytes': len(data)}, ensure_ascii=False))


def verify_apks(directory, output):
    expected = verify_assets(output)
    apks = sorted(directory.rglob('*.apk'))
    if not apks:
        raise ValueError('没有可核对终端命令的 APK')
    for apk in apks:
        with zipfile.ZipFile(apk) as archive:
            prefix = 'assets/' + ROOT_NAME + '/'
            manifest = json.loads(archive.read(prefix + 'manifest.json'))
            verify_contents(manifest, archive.read(prefix + BINARY_PATH), archive.read(prefix + 'install.sh'))
            for name in ('LICENSE', 'NOTICE'):
                if archive.read(prefix + name) != Path(__file__).with_name(name).read_bytes():
                    raise ValueError(f'{apk} 的终端命令许可文件与源码不一致')
            if manifest != expected:
                raise ValueError(f'{apk} 的终端命令与本次构建不一致')
        print(f'终端命令 APK 资源校验通过：{apk}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='打包并校验终端命令资源')
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument('--binary', type=Path)
    action.add_argument('--verify', action='store_true')
    action.add_argument('--verify-apks', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.binary is not None:
        package_assets(args.binary, args.output)
    elif args.verify:
        print(json.dumps(verify_assets(args.output), ensure_ascii=False))
    else:
        verify_apks(args.verify_apks, args.output)
