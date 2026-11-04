from pathlib import Path
import argparse
import hashlib
import json
import shutil
import struct
import zipfile

ASSET_ROOT = 'web_chat_command'
BINARY = 'aarch64/web-chat'
TARGET = 'aarch64-unknown-linux-gnu'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def verify_contents(manifest, binary, installer):
    if (manifest['format'] != 1 or manifest['target'] != TARGET
            or manifest['binary']['file'] != BINARY or manifest['installer']['file'] != 'install.sh'):
        raise ValueError('web-chat 资源清单无效')
    if (len(binary) < 64 or binary[:6] != b'\x7fELF\x02\x01'
            or struct.unpack_from('<H', binary, 18)[0] != 183
            or struct.unpack_from('<H', binary, 16)[0] not in (2, 3)):
        raise ValueError('web-chat 需要 ARM64 Linux ELF 产物')
    if digest(binary) != manifest['binary']['sha256']:
        raise ValueError('web-chat 二进制校验失败')
    if digest(installer) != manifest['installer']['sha256'] or installer != Path(__file__).with_name('install.sh').read_bytes():
        raise ValueError('web-chat 安装脚本与源码不一致')


def verify_assets(output):
    root = output / ASSET_ROOT
    manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
    verify_contents(manifest, (root / BINARY).read_bytes(), (root / 'install.sh').read_bytes())
    return manifest


def package_assets(binary, output):
    root = output / ASSET_ROOT
    target = root / BINARY
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(binary, target)
    installer = Path(__file__).with_name('install.sh')
    shutil.copyfile(installer, root / 'install.sh')
    manifest = {
        'format': 1, 'target': TARGET,
        'binary': {'file': BINARY, 'sha256': digest(binary.read_bytes())},
        'installer': {'file': 'install.sh', 'sha256': digest(installer.read_bytes())},
    }
    (root / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    verify_assets(output)
    print('web-chat 终端命令资源已打包')


def verify_apks(directory, output):
    expected = verify_assets(output)
    apks = sorted(directory.rglob('*.apk'))
    if not apks:
        raise ValueError('没有可校验 web-chat 的 APK')
    for apk in apks:
        with zipfile.ZipFile(apk) as archive:
            prefix = 'assets/' + ASSET_ROOT + '/'
            manifest = json.loads(archive.read(prefix + 'manifest.json'))
            verify_contents(manifest, archive.read(prefix + BINARY), archive.read(prefix + 'install.sh'))
            if manifest != expected:
                raise ValueError(f'{apk} 的 web-chat 命令与本次构建不一致')
        print(f'web-chat APK 资源校验通过：{apk}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='打包并校验 web-chat 终端命令')
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument('--binary', type=Path)
    action.add_argument('--verify', action='store_true')
    action.add_argument('--verify-apks', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.binary is not None:
        package_assets(args.binary, args.output)
    elif args.verify:
        verify_assets(args.output)
        print('web-chat 终端命令资源校验通过')
    else:
        verify_apks(args.verify_apks, args.output)
