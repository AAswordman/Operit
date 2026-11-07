"""发布已通过固定清单核对的资源，不修改已有正式资源版本。"""
from __future__ import annotations
import argparse
import hashlib
import json
from distribution_catalog import load_catalog
import os
from pathlib import Path
import subprocess
from urllib.parse import quote
ROOT = Path(__file__).resolve().parents[3]
import sys
sys.path.insert(0, str(ROOT / "ci/distribution"))
from repositories.config import RESOURCE_REPOSITORY, SOURCE_REPOSITORY

def request(endpoint: str, payload: dict | None = None, missing_ok: bool = False) -> dict | list | None:
    command = ['gh', 'api', endpoint]
    if payload is not None:
        command += ['--method', 'POST', '--input', '-']
    result = subprocess.run(command, input=json.dumps(payload) if payload is not None else None, capture_output=True, text=True, timeout=60)
    if result.returncode:
        if missing_ok and 'HTTP 404' in result.stderr:
            return None
        raise RuntimeError(result.stderr.strip())
    return json.loads(result.stdout) if result.stdout.strip() else None

def find_resource_release(repository: str, tag: str) -> dict | None:
    published = request(f'repos/{repository}/releases/tags/{quote(tag, safe="")}', missing_ok=True)
    if published is not None:
        return published
    # 未公开的草稿可能没有 Git tag，必须按列表中的 Release ID 继续处理。
    matches = []
    page = 1
    while True:
        releases = request(f'repos/{repository}/releases?per_page=100&page={page}')
        matches.extend(release for release in releases if release['tag_name'] == tag)
        if len(matches) > 1:
            raise ValueError('同一资源标签存在多个草稿，停止发布以避免重复创建')
        if len(releases) < 100:
            break
        page += 1
    return matches[0] if matches else None


def upload_resource_asset(repository: str, release_id: int, path: Path) -> None:
    endpoint = f'https://uploads.github.com/repos/{repository}/releases/{release_id}/assets?name={quote(path.name, safe="")}'
    subprocess.run([
        'gh', 'api', endpoint, '--method', 'POST',
        '--header', 'Content-Type: application/octet-stream', '--input', str(path),
    ], check=True, capture_output=True, text=True, timeout=900)


def digest(path: Path) -> str:
    with path.open('rb') as stream:
        return 'sha256:' + hashlib.file_digest(stream, 'sha256').hexdigest()

def publish(assets: Path, repository: str, commit: str) -> None:
    if repository != RESOURCE_REPOSITORY:
        raise ValueError('此资源目录固定指向维护者自己的仓库')
    pinned = ROOT / 'app/config/offline-packs.json'
    if (assets / pinned.name).read_bytes() != pinned.read_bytes():
        raise ValueError('准备的资源目录与本次源码清单不一致')
    if (assets / 'builtin-extensions.json').read_bytes() != (pinned.parent / 'builtin-extensions.json').read_bytes():
        raise ValueError('内置市场目录与打包输入不一致')
    catalog = load_catalog(pinned.parent)
    expected = {p['archive']['path']: p['archive'] for p in catalog['packs']}
    files = sorted(p for p in assets.iterdir() if p.is_file())
    if {p.name for p in files} != set(expected) | {'offline-packs.json', 'builtin-extensions.json', 'SHA256SUMS.txt'}:
        raise ValueError('发行文件列表与目录不一致')
    for path in files:
        spec = expected.get(path.name)
        if spec and (path.stat().st_size != spec['bytes'] or digest(path) != 'sha256:' + spec['sha256']):
            raise ValueError('发行资源校验失败：' + path.name)
    sums = (assets / 'SHA256SUMS.txt').read_text().splitlines()
    expected_sums = sorted(f'{digest(path).removeprefix("sha256:")}  {path.name}' for path in files if path.name != 'SHA256SUMS.txt')
    if sorted(sums) != expected_sums:
        raise ValueError('总校验清单不匹配')
    tag = catalog['releaseTag']
    release = find_resource_release(repository, tag)
    if release is None:
        release = request(f'repos/{repository}/releases', {
            'tag_name': tag, 'name': '离线资源 · ' + tag,
            'body': f'九种工程模板、40 个按需下载的内置扩展、Sherpa ONNX 共用引擎和 ML Kit 多语言模型资产。APK 逆向插件与四个运行 JAR 统一准备，下载后仍标记为内置。各文件的大小及 SHA-256 分别固定在随附的 offline-packs.json 与 builtin-extensions.json 中。本资源版本发布后不改写。\n\n准备源码：https://github.com/{SOURCE_REPOSITORY}/tree/{commit}。资源及模型沿用各自随附许可证。',
            'draft': True, 'prerelease': False, 'make_latest': 'false',
        })
    release_id = release['id']
    def remote_files() -> dict:
        return {item['name']: item for item in request(f'repos/{repository}/releases/{release_id}/assets?per_page=100')}
    remote = remote_files()
    if set(remote) - {p.name for p in files}:
        raise ValueError('现有资源版本中有未声明文件，禁止覆盖')
    for path in files:
        item = remote.get(path.name)
        if item:
            if item['state'] != 'uploaded' or item['size'] != path.stat().st_size or item.get('digest') != digest(path):
                raise ValueError('现有远端资源与固定清单不一致，禁止覆盖：' + path.name)
        else:
            if not release['draft']:
                raise ValueError('已发布资源版本不完整，禁止修改：' + path.name)
            upload_resource_asset(repository, release_id, path)
            print('已上传：' + path.name, flush=True)
    remote = remote_files()
    if set(remote) != {p.name for p in files}:
        raise ValueError('上传后的资源文件列表不匹配')
    for path in files:
        item = remote[path.name]
        if item['state'] != 'uploaded' or item['size'] != path.stat().st_size or item.get('digest') != digest(path):
            raise ValueError('上传后大小或摘要不匹配：' + path.name)
    if release['draft']:
        result = subprocess.run(['gh', 'api', f'repos/{repository}/releases/{release_id}', '--method', 'PATCH', '--input', '-'],
            input=json.dumps({'draft': False, 'make_latest': 'false'}), text=True, capture_output=True, timeout=60)
        if result.returncode:
            raise RuntimeError(result.stderr.strip())
        release = json.loads(result.stdout)
    report = {'tag': tag, 'url': release['html_url'], 'files_verified': len(files), 'published': not release['draft']}
    print(json.dumps(report, ensure_ascii=False), flush=True)
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a') as summary:
            summary.write('已发布并核对固定资源：' + release['html_url'] + '\n')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--assets', type=Path, required=True)
    parser.add_argument('--repository', required=True)
    parser.add_argument('--commit', required=True)
    args = parser.parse_args()
    publish(args.assets, args.repository, args.commit)
