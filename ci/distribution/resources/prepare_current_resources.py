"""在签名构建前准备当前资源，在公开 APK 前发布同一份资源。"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

from prepare_hub import ROOT, digest, prepare
from publish_hub import publish
from verify_hub_catalog import main as verify_catalog
from repositories.config import RESOURCE_REPOSITORY, SOURCE_REPOSITORY


SHARED_GROUPS = frozenset({'engine-sherpa-onnx', 'ocr-google-mlkit'})


def download_shared_assets(catalog: dict, base: Path) -> None:
    selected = [pack for pack in catalog['packs'] if pack['id'] in SHARED_GROUPS]
    if len(selected) != len(SHARED_GROUPS) or {pack['id'] for pack in selected} != SHARED_GROUPS:
        raise ValueError('固定目录必须包含唯一的共用引擎和 OCR 资源')
    for pack in selected:
        archive = pack['archive']
        name = archive['path']
        expected_url = f'https://github.com/{RESOURCE_REPOSITORY}/releases/download/{catalog["releaseTag"]}/{name}'
        if Path(name).name != name or archive['url'] != expected_url:
            raise ValueError('共用资源地址与固定发行目录不一致：' + pack['id'])
        target = base / name
        with urllib.request.urlopen(expected_url, timeout=120) as response, target.open('wb') as output:
            shutil.copyfileobj(response, output)
        if target.stat().st_size != archive['bytes'] or digest(target) != archive['sha256']:
            raise ValueError('已发布共用资源的大小或 SHA-256 不匹配：' + pack['id'])


def save_report(report: dict, output: Path) -> None:
    target = output / 'resource-preparation.json'
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    # 使用既有 Android reports 上传入口保存轻量证据，资源归档留在 runner 供正式发布使用。
    reports = ROOT / 'app/build/reports/offline-resources'
    reports.mkdir(parents=True, exist_ok=True)
    for source in (ROOT / 'app/config/offline-packs.json', ROOT / 'app/config/builtin-extensions.json', ROOT / 'app/config/offline-pack-sources.json',
                   output / 'assets/SHA256SUMS.txt', target):
        shutil.copyfile(source, reports / source.name)
    print(json.dumps(report, ensure_ascii=False), flush=True)


def prepare_current(packages: Path, output: Path) -> dict:
    revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    output.mkdir(parents=True)
    config = ROOT / 'app/config'
    pinned = config / 'offline-packs.json'
    base = output / 'base'
    base.mkdir()
    # 引擎和模型没有参与本轮扩展源码改动，按原清单逐字节核对后复用。
    shutil.copyfile(pinned, base / pinned.name)
    download_shared_assets(json.loads(pinned.read_text(encoding='utf-8')), base)
    assets = output / 'assets'
    snapshot = output / 'offline-pack-sources.json'
    catalog = prepare(assets, output / 'cache', packages, base, snapshot)
    # 两份目录与源码快照来自同一次准备，严格检查读取的目录与 APK 打包输入完全相同。
    shutil.copyfile(assets / pinned.name, pinned)
    shutil.copyfile(assets / 'builtin-extensions.json', config / 'builtin-extensions.json')
    shutil.copyfile(snapshot, config / snapshot.name)
    verify_catalog(current_source=True)
    report = {
        'sourceRevision': revision,
        'releaseTag': catalog['releaseTag'],
        'packs': len(catalog['packs']),
        'published': False,
        'catalogSha256': digest(pinned),
        'builtinCatalogSha256': digest(config / 'builtin-extensions.json'),
        'snapshotSha256': digest(snapshot),
    }
    save_report(report, output)
    return report


def release_prepared(apk: Path, commit: str, output: Path, *, publish_resources: bool) -> dict:
    revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    if commit != revision:
        raise ValueError('发行资源必须来自本次 Actions 固定的源码提交')
    if publish_resources and (os.environ['GITHUB_REPOSITORY'] != SOURCE_REPOSITORY or os.environ['GITHUB_SHA'] != commit):
        raise ValueError('发行资源必须来自本次 Actions 固定的源码提交')
    pinned = ROOT / 'app/config/offline-packs.json'
    snapshot = ROOT / 'app/config/offline-pack-sources.json'
    report = json.loads((output / 'resource-preparation.json').read_text(encoding='utf-8'))
    catalog = json.loads(pinned.read_text(encoding='utf-8'))
    if (report['sourceRevision'] != commit or report['releaseTag'] != catalog['releaseTag']
            or report['catalogSha256'] != digest(pinned) or report['snapshotSha256'] != digest(snapshot)
            or json.loads(snapshot.read_text(encoding='utf-8'))['sourceRevision'] != commit):
        raise ValueError('资源准备记录与本轮源码或打包输入不一致')
    if (output / 'assets/offline-packs.json').read_bytes() != pinned.read_bytes():
        raise ValueError('准备资源与本轮打包目录不一致')
    with zipfile.ZipFile(apk) as package:
        if package.read('assets/offline-packs.json') != pinned.read_bytes():
            raise ValueError('APK 资源目录与本轮准备资源不一致')
    builtin = ROOT / 'app/config/builtin-extensions.json'
    if report['builtinCatalogSha256'] != digest(builtin):
        raise ValueError('内置市场目录与准备记录不一致')
    with zipfile.ZipFile(apk) as package:
        if package.read('assets/builtin-extensions.json') != builtin.read_bytes():
            raise ValueError('APK 内置市场目录与本轮准备资源不一致')
    verify_catalog(current_source=True)
    if publish_resources:
        # 资源附件全部核对并公开成功后，调用方才允许创建和公开应用 Release。
        publish(output / 'assets', RESOURCE_REPOSITORY, commit)
        report['published'] = True
        save_report(report, output)
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--packages', type=Path, required=True, help='本轮已经构建的完整白名单扩展目录')
    parser.add_argument('--output', type=Path, required=True, help='本轮生成目录，不与旧运行共用')
    args = parser.parse_args()
    prepare_current(args.packages, args.output)
