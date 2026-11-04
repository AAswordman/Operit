from pathlib import Path
import argparse
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
COMMAND_PACKAGERS = (
    ROOT / 'tools/native_apply_patch/package_assets.py',
    ROOT / 'tools/web_chat_cli/package_assets.py',
)


def verify_commands(output, apk_directory=None):
    action = ['--verify'] if apk_directory is None else ['--verify-apks', str(apk_directory)]
    for packager in COMMAND_PACKAGERS:
        print(f'校验终端命令资源：{packager.parent.name}', flush=True)
        subprocess.run([
            sys.executable, str(packager), *action, '--output', str(output),
        ], check=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='统一校验全部内置终端命令资源')
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--verify', action='store_true')
    mode.add_argument('--verify-apks', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    verify_commands(args.output, args.verify_apks)
