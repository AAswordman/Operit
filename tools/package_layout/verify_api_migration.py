"""核对 API 包迁移的文件范围、正文、字面量与对外入口；不执行应用构建或测试。"""

from collections import defaultdict
from pathlib import Path
import hashlib
import json
import re
import subprocess
import sys

from source_text import body_hash, header, replace_names, tokens

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / 'docs/TODO/chore/package-layout/api-migration.json'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def git(*arguments, input_bytes=None):
    result = subprocess.run(['git', *arguments], cwd=ROOT, input=input_bytes, capture_output=True)
    require(result.returncode == 0, result.stderr.decode())
    return result.stdout


def baseline_blobs(commit, paths):
    request = ''.join(commit + ':' + path + '\n' for path in paths).encode()
    response = git('cat-file', '--batch', input_bytes=request)
    position = 0
    result = {}
    for path in paths:
        end = response.index(b'\n', position)
        descriptor = response[position:end].split()
        require(len(descriptor) == 3 and descriptor[1] == b'blob', '基线源文件不可读取：' + path)
        size = int(descriptor[2])
        result[path] = response[end + 1:end + 1 + size]
        position = end + size + 2
    require(position == len(response), '基线批量读取长度不匹配')
    return result


def literal_values(text, kotlin):
    return [token.text for token in tokens(text, kotlin) if token.kind == 'literal']


def verify_bindings(before, after, manifest, by_package, path):
    old_info, new_info = header(before), header(after)
    names = {token.text.strip('`') for token in tokens(before[old_info['end']:], path.endswith('.kt')) if token.kind == 'identifier'}
    explicit = {}
    stars = set()
    for entry in old_info['imports']:
        target, *alias = re.split(r'\s+as\s+', entry['value'].removeprefix('static '))
        if target.endswith('.*'):
            stars.add(target[:-2])
        else:
            explicit[alias[0] if alias else target.rsplit('.', 1)[-1]] = target
    new_imports = {entry['value'] for entry in new_info['imports']}
    checked = 0
    for package in stars | {old_info['package']}:
        for symbol in by_package[package]:
            name = symbol['name']
            if name not in names or (name in explicit and explicit[name] != symbol['oldName']):
                continue
            checked += 1
            visible = symbol['newPackage'] == new_info['package'] or symbol['newName'] in new_imports or symbol['newPackage'] + '.*' in new_imports or symbol['file'] == path
            require(visible, '原同包引用未更新：' + path + ' → ' + symbol['newName'])
    for entry in old_info['imports']:
        value = entry['value']
        if value.endswith('.*') and value[:-2] in manifest['retiredPackages']:
            continue
        require(replace_names(value, manifest['qualifiedNames']) in new_imports, '原显式导入未保留：' + path + ' → ' + value)
    return checked


def main():
    manifest = json.loads(MANIFEST.read_text())
    baseline = manifest['baselineCommit']
    records = manifest['files']
    by_package = defaultdict(list)
    for symbol in manifest['symbols']:
        by_package[symbol['oldPackage']].append(symbol)
    changes = {item['oldPath']: item for item in manifest['sourceChanges'] if item['source']}
    require(len(records) == 117, 'API 清单必须覆盖原始 117 个源文件')
    require(sum(item['oldPath'] != item['newPath'] for item in records) == 74, 'API 移动文件数与已批准范围不符')
    require(len({item['newPath'] for item in records}) == 117, 'API 目标路径重复')
    tree = {}
    for entry in git('ls-tree', '-r', '-z', baseline).split(b'\0'):
        if not entry:
            continue
        metadata, path = entry.split(b'\t', 1)
        mode, kind, oid = metadata.decode().split()
        if kind == 'blob':
            tree[path.decode()] = oid
    source_paths = sorted(path for path in tree if path.endswith(('.kt', '.java')))
    baseline_paths = sorted(set(changes) | {item['oldPath'] for item in records})
    originals = baseline_blobs(baseline, baseline_paths)
    for item in records:
        old_path, new_path = item['oldPath'], item['newPath']
        current = ROOT / new_path
        require(current.is_file(), '目标文件缺失：' + new_path)
        require(hashlib.sha256(originals[old_path]).hexdigest() == item['originalSha256'], '原始文件摘要不匹配：' + old_path)
        require(header(current.read_bytes().decode())['package'] == item['newPackage'], '目标包声明不匹配：' + new_path)
        if old_path != new_path:
            require(not (ROOT / old_path).exists(), '旧文件未删除：' + old_path)
    expected_paths = set()
    unchanged = bodies = implicit_bindings = 0
    for old_path in source_paths:
        change = changes.get(old_path)
        new_path = change['newPath'] if change else old_path
        expected_paths.add(new_path)
        current = ROOT / new_path
        require(current.is_file(), '源文件缺失：' + new_path)
        data = current.read_bytes()
        if change is None:
            blob = b'blob ' + str(len(data)).encode() + b'\0' + data
            require(hashlib.sha1(blob).hexdigest() == tree[old_path], '范围外源码发生变化：' + old_path)
            unchanged += 1
            continue
        if old_path != new_path and not old_path.startswith('app/src/main/java/com/ai/assistance/operit/api/'):
            require(old_path.startswith(('app/src/test/', 'app/src/androidTest/')), '范围外业务源码被移动：' + old_path)
        original = originals[old_path]
        require(hashlib.sha256(original).hexdigest() == change['originalSha256'], '引用文件基线摘要不匹配：' + old_path)
        kotlin = old_path.endswith('.kt')
        before, after = original.decode(), data.decode()
        require(body_hash(before, manifest, kotlin) == body_hash(after, manifest, kotlin), '出现包、import 或批准改名以外的正文变化：' + new_path)
        require(literal_values(before, kotlin) == literal_values(after, kotlin), '字符串或字符字面量变化：' + new_path)
        info = header(after)
        if old_path != new_path:
            require('/java/' + info['package'].replace('.', '/') + '/' in new_path, '移动文件的路径与包声明不一致：' + new_path)
        bindings = defaultdict(set)
        for item in info['imports']:
            target, *alias = re.split(r'\s+as\s+', item['value'].removeprefix('static '))
            if not target.endswith('.*'):
                bindings[alias[0] if alias else target.rsplit('.', 1)[-1]].add(target)
        original_bindings = defaultdict(set)
        for item in header(before)['imports']:
            target, *alias = re.split(r'\s+as\s+', item['value'].removeprefix('static '))
            if not target.endswith('.*'):
                name = alias[0] if alias else target.rsplit('.', 1)[-1]
                name = manifest['classRenames'].get(name, name)
                original_bindings[name].add(replace_names(target, manifest['qualifiedNames']))
        require(all(len(values) <= 1 or values <= original_bindings[name] for name, values in bindings.items()), '新增显式导入名称冲突：' + new_path)
        implicit_bindings += verify_bindings(before, after, manifest, by_package, old_path)
        bodies += 1
    current_sources = set(git('ls-files', '--cached', '--others', '--exclude-standard', '-z').decode().split('\0'))
    current_sources = {path for path in current_sources if path.endswith(('.kt', '.java')) and (ROOT / path).is_file()}
    require(current_sources == expected_paths, '源文件总集合与迁移映射不一致')
    for path, expected in manifest['protectedFiles'].items():
        require(hashlib.sha256((ROOT / path).read_bytes()).hexdigest() == expected, '对外组件、构建标识或 Java 桥入口发生变化：' + path)
    old_names = [name for name, target in manifest['qualifiedNames'].items() if name != target]
    pattern = re.compile(r'(?<![\w$])(?:' + '|'.join(re.escape(name) for name in sorted(old_names, key=len, reverse=True)) + r')(?!\w)')
    scanned = 0
    paths = set(tree) | {item['newPath'] for item in manifest['sourceChanges']}
    for path in sorted(paths):
        if not path.startswith(('app/', 'examples/', 'tools/', 'ci/', '.github/')) or path.startswith('tools/package_layout/'):
            continue
        file = ROOT / path
        if not file.is_file():
            continue
        data = file.read_bytes()
        if b'\0' in data:
            continue
        try:
            text = data.decode()
        except UnicodeDecodeError:
            continue
        require(pattern.search(text) is None, '可执行目录仍引用已迁出的完整名称：' + path)
        require(not any(re.search(r'(?m)^\s*import\s+' + re.escape(package) + r'\.\*', text) for package in manifest['retiredPackages']), '仍有退役包的星号导入：' + path)
        scanned += 1
    result = {'success': True, 'baselineCommit': baseline, 'apiFiles': 117, 'movedApiFiles': 74, 'retainedApiFiles': 43, 'changedSourceBodiesVerified': bodies, 'unchangedSourceFilesVerified': unchanged, 'testFilesMoved': sum(item['oldPath'] != item['newPath'] and item['oldPath'].startswith(('app/src/test/', 'app/src/androidTest/')) for item in changes.values()), 'literalChanges': 0, 'implicitBindingsVerified': implicit_bindings, 'protectedFilesVerified': len(manifest['protectedFiles']), 'activeTextFilesScanned': scanned, 'localApplicationBuildOrTestsRun': False}
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError) as error:
        print(json.dumps({'success': False, 'error': str(error)}, ensure_ascii=False), file=sys.stderr)
        raise SystemExit(1)