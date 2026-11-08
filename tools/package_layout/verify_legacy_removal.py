"""核对废弃原生界面和虚拟形象清理的固定实现快照。"""

from pathlib import Path
import ast
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET

from source_text import tokens

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / 'docs/TODO/chore/package-layout/removal-migration.json'


def git(*arguments: str) -> bytes:
    return subprocess.check_output(['git', *arguments], cwd=ROOT)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def function_body(text: str, marker: str) -> str:
    start = text.index(marker)
    opened = False
    depth = 0
    for token in tokens(text):
        if token.start < start:
            continue
        if token.kind not in {'punctuation', 'interpolation_open', 'interpolation_close'}:
            continue
        value = '{' if token.kind == 'interpolation_open' else token.text
        if value == '{':
            depth += 1
            opened = True
        elif value == '}':
            depth -= 1
            if opened and depth == 0:
                return text[start:token.end]
    raise AssertionError(f'声明未闭合：{marker}')


def main() -> None:
    manifest = json.loads(MANIFEST.read_text())
    baseline = manifest['baseline']
    records = manifest['files']
    for record in records:
        path = ROOT / record['path']
        before = git('show', baseline + ':' + record['path']) if record['beforeSha256'] else None
        assert (digest(before) if before is not None else None) == record['beforeSha256'], record['path']
        if record['afterSha256'] is None:
            assert not path.exists(), record['path']
        else:
            assert path.is_file() and digest(path.read_bytes()) == record['afterSha256'], record['path']

    # 全部未纳入清理的应用源码逐字节保持，不把摘要一致当作设备验收。
    changed = {record['path'] for record in records}
    baseline_files = {}
    for line in git('ls-tree', '-r', '--full-tree', baseline).decode().splitlines():
        metadata, name = line.split('\t')
        mode, kind, oid = metadata.split()
        if kind == 'blob':
            baseline_files[name] = oid
    unchanged = [name for name in baseline_files if name.startswith('app/src/') and name.endswith(('.kt', '.java')) and name not in changed]
    for name in unchanged:
        data = (ROOT / name).read_bytes()
        oid = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
        assert oid == baseline_files[name], name
    for record in manifest['protectedFiles']:
        assert digest((ROOT / record['path']).read_bytes()) == record['sha256'], record['path']
    for record in manifest['preservedWorkflowFiles']:
        before = git('show', baseline + ':' + record['path'])
        assert (ROOT / record['path']).read_bytes() == before, record['path']
        assert digest(before) == record['sha256'], record['path']

    # 保留的字符串条目与基线一致，删除集合在九种语言中一致。
    keys = set(manifest['removedStringKeys'])
    retained_count = 0
    for name in manifest['resourceFiles']:
        left_root = ET.fromstring(git('show', baseline + ':' + name))
        right_root = ET.parse(ROOT / name).getroot()
        def entries(element):
            return {(child.tag, child.get('name')): ET.tostring(child, encoding='unicode').rstrip() for child in element}
        left, right = entries(left_root), entries(right_root)
        assert set(left) - set(right) == {('string', key) for key in keys}, name
        assert set(right) <= set(left), name
        assert all(left[key] == right[key] for key in right), name
        retained_count += len(right)

    for move in manifest['bodyPreservingMoves']:
        before = git('show', baseline + ':' + move['from']).decode().split('\n', 1)[1]
        after = (ROOT / move['to']).read_text().split('\n', 1)[1]
        assert before == after, move
    for check in manifest['preservedFunctions']:
        before = git('show', baseline + ':' + check['from']).decode()
        after = (ROOT / check['to']).read_text()
        assert function_body(before, check['marker']) == function_body(after, check['marker']), check

    changed_kotlin = [record['path'] for record in records if record['path'].endswith('.kt') and record['afterSha256']]
    for name in changed_kotlin:
        stack = []
        for token in tokens((ROOT / name).read_text()):
            if token.kind not in {'punctuation', 'interpolation_open', 'interpolation_close'}:
                continue
            value = '{' if token.kind == 'interpolation_open' else token.text
            if value in '([{':
                stack.append(value)
            elif value in ')]}':
                assert stack and stack.pop() == {')': '(', ']': '[', '}': '{'}[value], (name, token.start)
        assert not stack, name
    for name in ['ci/script/pr_check.py', 'ci/test/test_pr_check.py']:
        ast.parse((ROOT / name).read_text(), filename=name)
    forbidden = re.compile(manifest['forbiddenRuntimeReferences'])
    for name in git('ls-files', '--cached', '--others', '--exclude-standard', 'app/src', 'app/build.gradle.kts', 'settings.gradle.kts', 'ci').decode().splitlines():
        if not name.endswith(('.kt', '.java', '.kts', '.yml', '.py')) or not (ROOT / name).exists():
            continue
        assert not forbidden.search((ROOT / name).read_text()), name
    subprocess.run(['git', 'diff', '--check'], cwd=ROOT, check=True)
    print(json.dumps({
        'success': True,
        'baseline': baseline,
        'changedFilesVerified': len(records),
        'unchangedKotlinJavaFilesVerified': len(unchanged),
        'protectedFilesVerified': len(manifest['protectedFiles']),
        'workflowFilesUnchanged': len(manifest['preservedWorkflowFiles']),
        'retainedStringResourcesVerified': retained_count,
        'removedStringKeys': len(keys),
        'removedStringDefinitions': len(keys) * len(manifest['resourceFiles']),
        'sharedBodiesVerified': len(manifest['bodyPreservingMoves']) + len(manifest['preservedFunctions']),
        'localApplicationBuildOrTestsRun': False,
    }, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
