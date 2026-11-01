"""核对废弃命名清理、插件界面 API 与 Android 组件包名收尾，不构建或执行应用。"""
from pathlib import Path
import hashlib
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from completion_text import transform
from source_text import header
from verify_layout_migration import baseline_blobs

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / 'docs/TODO/chore/package-layout/completion-migration.json'
ANDROID = '{http://schemas.android.com/apk/res/android}'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def git(*arguments):
    result = subprocess.run(['git', *arguments], cwd=ROOT, capture_output=True)
    require(result.returncode == 0, result.stderr.decode())
    return result.stdout


def blob_hash(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()


def main():
    manifest = json.loads(MANIFEST.read_text())
    baseline = manifest['baselineCommit']
    tree = {}
    for entry in git('ls-tree', '-r', '-z', baseline).split(b'\0'):
        if not entry:
            continue
        metadata, path = entry.split(b'\t', 1)
        mode, kind, oid = metadata.decode().split()
        if kind == 'blob':
            tree[path.decode()] = (mode, oid)
    records = {item['oldPath']: item for item in manifest['files']}
    require(len(records) == len(manifest['files']), '清单原始路径重复')
    require(len({item['newPath'] for item in records.values()}) == len(records), '清单目标路径重复')
    originals = baseline_blobs(baseline, sorted(records))
    source = lambda path: path.endswith(('.kt', '.java'))
    payload = lambda path: path.startswith(tuple(manifest['payloadPrefixes'])) or path in manifest['payloadPaths']
    tracked = set(filter(None, git('ls-files', '--cached', '--others', '--exclude-standard', '-z').decode().split('\0')))
    current = {path for path in tracked if (ROOT / path).is_file() or (ROOT / path).is_symlink()}
    baseline_scope = {path for path in tree if payload(path) or source(path)}
    expected = {manifest['moves'].get(path, path) for path in baseline_scope}
    actual = {path for path in current if payload(path) or source(path)}
    require(expected == actual, '文件集合不一致：' + json.dumps({'missing': sorted(expected - actual), 'unexpected': sorted(actual - expected)}, ensure_ascii=False))
    old_names = re.compile('|'.join(re.escape(name) for name in sorted(manifest['replacements'], key=len, reverse=True)))
    changed_sources = unchanged_sources = unchanged_payload = scanned = removed_strings = 0
    for path in sorted(baseline_scope):
        item = records.get(path)
        target_path = item['newPath'] if item else path
        target = ROOT / target_path
        data = os.readlink(target).encode() if target.is_symlink() else target.read_bytes()
        if item:
            original = originals[path]
            require(hashlib.sha256(original).hexdigest() == item['originalSha256'], '基线摘要错误：' + path)
            expected_data = transform(original.decode(), path, manifest).encode()
            require(data == expected_data, '出现清单外正文或字面量变化：' + target_path)
            require(hashlib.sha256(data).hexdigest() == item['expectedSha256'], '目标摘要错误：' + target_path)
            if path != target_path:
                require(not (ROOT / path).exists(), '旧文件仍存在：' + path)
            changed_sources += int(source(path))
            if path.startswith('app/src/main/res/values') and path.endswith('.xml'):
                before = ET.fromstring(original)
                after = ET.fromstring(data)
                removed_strings += sum(node.get('name') in manifest['removedStringKeys'] for node in before.findall('string'))
                require(not any(node.get('name') in manifest['removedStringKeys'] for node in after.findall('string')), '仍有废弃资源：' + path)
        else:
            require(blob_hash(data) == tree[path][1], '范围外文件变化：' + path)
            unchanged_sources += int(source(path))
            unchanged_payload += int(payload(path))
        if not (payload(path) or source(path)) or b'\0' in data:
            continue
        try:
            text = data.decode()
        except UnicodeDecodeError:
            continue
        for block in manifest.get('compatibilityBlocks', {}).get(path, []):
            require(text.count(block) == 1, '兼容入口内容不符：' + path)
            text = text.replace(block, '')
        require(old_names.search(text) is None, '仍有退役完整名称或标识：' + target_path)
        if path not in manifest['allowedToolboxDataPaths']:
            require(re.search('toolbox|工具箱|兼容旧声明|接受旧插件声明', text, re.IGNORECASE) is None, '仍有废弃工具箱名称或说明：' + target_path)
        scanned += 1
    for path in manifest['componentFiles']:
        package = header((ROOT / path).read_text())['package']
        require('/java/' + package.replace('.', '/') + '/' + Path(path).name in path, '组件路径与包声明不一致：' + path)
    old_manifest = ET.fromstring(originals['app/src/main/AndroidManifest.xml'])
    new_manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
    old_nodes, new_nodes = list(old_manifest.iter()), list(new_manifest.iter())
    require(len(old_nodes) == len(new_nodes), 'Manifest 结构发生变化')
    changed_names = []
    for old, new in zip(old_nodes, new_nodes):
        require(old.tag == new.tag and old.text == new.text and old.tail == new.tail, 'Manifest 结构或正文变化')
        old_attrs, new_attrs = dict(old.attrib), dict(new.attrib)
        old_name = old_attrs.pop(ANDROID + 'name', None)
        new_name = new_attrs.pop(ANDROID + 'name', None)
        require(old_attrs == new_attrs, 'Manifest 非组件属性变化：' + old.tag)
        if old_name != new_name:
            require(old.tag in {'activity', 'receiver', 'provider'}, '非组件名称变化')
            changed_names.append(new_name)
            full_name = 'com.ai.assistance.operit' + new_name if new_name.startswith('.') else new_name
            require((ROOT / 'app/src/main/java' / (full_name.replace('.', '/') + '.kt')).is_file(), 'Manifest 类不可定位：' + full_name)
    require(len(changed_names) == 7, 'Manifest 组件改名数量不符')
    widget_xml = ET.parse(ROOT / 'app/src/main/res/xml/toolpkg_desktop_widget_info.xml').getroot()
    configure = widget_xml.get(ANDROID + 'configure')
    require(configure == 'com.ai.assistance.operit.integrations.appwidget.ToolPkgDesktopWidgetConfigActivity', 'Widget 配置入口未同步')
    bridge_checks = {
        'app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsEngine.kt': 'fun registerToolPkgUiModule(specJson: String)',
        'app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsToolPkgRegistration.kt': 'registerUiModule: function(definition)',
        'examples/types/toolpkg.d.ts': 'registerUiModule(definition: UiModuleRegistration)',
        'examples/types/core.d.ts': 'function registerToolPkgUiModule(specJson: string)',
        'tools/toolpkg/toolpkg_hook_runner.js': 'registerUiModule() {}',
    }
    for path, declaration in bridge_checks.items():
        require(declaration in (ROOT / path).read_text(), '桥接或 SDK 声明不一致：' + path)
    api_files = [item['newPath'] for item in records.values() if item['newPath'].startswith('examples/') and 'ToolPkg.registerUiModule({' in (ROOT / item['newPath']).read_text()]
    expected_api_files = {item['newPath'] for item in records.values() if item['oldPath'].startswith('examples/') and 'ToolPkg.registerToolboxUiModule({' in originals[item['oldPath']].decode()}
    require(set(api_files) == expected_api_files and bool(api_files), '内置插件源码与已提交脚本未同步')
    for path in api_files:
        if '/src/' in path:
            require(path.replace('/src/', '/dist/').removesuffix('.ts') + '.js' in expected_api_files, '缺少配套脚本：' + path)
    native_files = [path for path in tree if path.endswith(('.aidl', '.cpp', '.h', '.rs'))]
    for path in native_files:
        require(blob_hash((ROOT / path).read_bytes()) == tree[path][1], 'JNI 或 Binder 协议文件发生变化：' + path)
    parser_text = (ROOT / 'app/src/main/java/com/ai/assistance/operit/core/tools/packTool/parser/ToolPkgMainRegistrationScriptParser.kt').read_text()
    require(parser_text.count('item.optString("runtime").trim().ifBlank { TOOLPKG_RUNTIME_COMPOSE_DSL }') == 2, '界面模块或路由的默认 Compose DSL 被修改')
    require('return@forEachIndexed' in parser_text, '旧导航声明未忽略')
    result = {
        'success': True, 'baselineCommit': baseline,
        'changedPayloadFilesVerified': len(records), 'changedSourceFilesVerified': changed_sources,
        'unchangedSourceFilesVerified': unchanged_sources, 'totalSourceFilesVerified': changed_sources + unchanged_sources,
        'unchangedPayloadFilesVerified': unchanged_payload, 'activeTextFilesScanned': scanned,
        'componentPackagesCompleted': len(manifest['componentFiles']), 'manifestComponentsUpdated': len(changed_names),
        'removedStringDefinitions': removed_strings, 'bridgeDeclarationsVerified': len(bridge_checks),
        'builtinSourceAndDistRegistrationsVerified': len(api_files), 'nativeProtocolFilesUnchanged': len(native_files),
        'legacyCompatibilityBlocksVerified': sum(len(items) for items in manifest['compatibilityBlocks'].values()),
        'composeDslDefaultsVerified': 2,
        'localApplicationBuildOrTestsRun': False,
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError, ET.ParseError) as error:
        print(json.dumps({'success': False, 'error': str(error)}, ensure_ascii=False), file=sys.stderr)
        raise SystemExit(1)