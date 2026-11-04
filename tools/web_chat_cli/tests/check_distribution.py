from pathlib import Path
import argparse
import concurrent.futures
import hashlib
import json
import os
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from package_assets import package_assets, verify_assets, verify_apks


class DistributionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="operit web-chat '$ ")
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.payload = self.directory / 'web-chat'
        shutil.copyfile(BINARY, self.payload)
        self.digest = hashlib.sha256(self.payload.read_bytes()).hexdigest()
        self.command_dir = self.directory / 'usr/local/bin'
        self.command = self.command_dir / 'web-chat'
        self.installed_binary = self.command_dir / ('.web-chat-bin-' + self.digest)

    def install(self, digest=None):
        return subprocess.run(['bash', str(ROOT / 'install.sh'), str(self.payload),
                               self.digest if digest is None else digest, str(self.command_dir), 'operit.web-chat.10234'],
                              capture_output=True, text=True, timeout=15)

    def require_install(self):
        result = self.install()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.installed_binary.read_bytes(), self.payload.read_bytes())
        self.assertEqual(self.command.stat().st_mode & 0o777, 0o755)

    def run_command(self, args, channel=None):
        environment = dict(os.environ)
        environment.pop('OPERIT_WEB_CHAT_SOCKET', None)
        if channel is not None:
            environment['OPERIT_WEB_CHAT_SOCKET'] = channel
        return subprocess.run(['qemu-aarch64', '-L', SYSROOT, str(self.installed_binary), *args],
                              env=environment, capture_output=True, text=True, timeout=15)

    def test_install_wrapper_quoting_and_same_version(self):
        self.require_install()
        inode = self.command.stat().st_ino
        self.require_install()
        self.assertEqual(inode, self.command.stat().st_ino)
        # 拦截包装器的 exec，核对含引号路径、宿主通道和原始参数，不执行 ARM64 程序。
        script = 'exec() { printf "%s\\n" "$OPERIT_WEB_CHAT_SOCKET" "$@"; }; source "$1" "${@:2}"'
        result = subprocess.run(['bash', '-c', script, '_', str(self.command), 'start-server', '--port', '8080', '--login'],
                                capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.splitlines(), ['operit.web-chat.10234', str(self.installed_binary), 'start-server', '--port', '8080', '--login'])

    def test_bad_digest_does_not_replace_the_installed_command(self):
        self.require_install()
        original = self.command.read_bytes()
        self.assertNotEqual(self.install('invalid').returncode, 0)
        self.payload.write_bytes(b'corrupted')
        self.assertNotEqual(self.install().returncode, 0)
        self.assertEqual(self.command.read_bytes(), original)

    def test_concurrent_installations_leave_one_complete_command(self):
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            results = list(pool.map(lambda _: self.install(), range(4)))
        for result in results:
            self.assertEqual(result.returncode, 0, result.stderr)
        self.require_install()
        self.assertFalse(list(self.command_dir.glob('.web-chat-install.*')))

    def test_help_version_and_invalid_arguments_need_no_android_service(self):
        self.require_install()
        for option in ['--help', '--version']:
            self.assertEqual(self.run_command([option]).returncode, 0)
        for args in [['start-server', '--port', '0'], ['start-server', '--copy'], ['examples', 'bad']]:
            self.assertEqual(self.run_command(args).returncode, 2)
        self.assertEqual(self.run_command(['status']).returncode, 1)

    def test_arm64_command_sends_the_requested_action_and_propagates_failure(self):
        self.require_install()
        for success in [True, False]:
            name = 'operit-cli-distribution-' + os.urandom(8).hex()
            with socket.socket(socket.AF_UNIX) as listener:
                listener.bind(chr(0) + name)
                listener.listen(1)
                received = []
                def serve():
                    connection, _ = listener.accept()
                    with connection, connection.makefile('rb') as reader:
                        received.append(json.loads(reader.readline()))
                        connection.sendall((json.dumps({'version': 1, 'success': success, 'output': 'result'}) + chr(10)).encode())
                worker = threading.Thread(target=serve, daemon=True)
                worker.start()
                result = self.run_command(['start-server', '--login', '--port', '8080'], name)
                worker.join(timeout=10)
                self.assertFalse(worker.is_alive())
                self.assertEqual(received, [{'version': 1, 'command': 'start-server', 'port': 8080, 'login': True}])
                self.assertEqual(result.returncode, 0 if success else 1)
                self.assertEqual((result.stdout if success else result.stderr).strip(), 'result')

    def test_asset_and_apk_integrity(self):
        output = self.directory / 'assets'
        package_assets(self.payload, output)
        verify_assets(output)
        apk_dir = self.directory / 'apk'
        apk_dir.mkdir()
        with zipfile.ZipFile(apk_dir / 'app.apk', 'w') as archive:
            for path in output.rglob('*'):
                if path.is_file():
                    archive.write(path, 'assets/' + str(path.relative_to(output)))
        verify_apks(apk_dir, output)
        installed_asset = output / 'web_chat_command/aarch64/web-chat'
        installed_asset.write_bytes(installed_asset.read_bytes() + b'tamper')
        with self.assertRaisesRegex(ValueError, '二进制校验失败'):
            verify_assets(output)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--binary', type=Path, required=True)
    parser.add_argument('--sysroot', required=True)
    args = parser.parse_args()
    BINARY = args.binary.resolve(strict=True)
    SYSROOT = args.sysroot
    unittest.main(argv=[sys.argv[0]], verbosity=2)
