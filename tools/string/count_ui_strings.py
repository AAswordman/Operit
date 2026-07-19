#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
统计Android项目中需要国际化的中文字符串
排除：注释、AppLogger、日志、大段提示词
"""

import re
from pathlib import Path
from collections import defaultdict
from typing import List, Tuple, Dict

# 排除模式
PATTERNS_TO_EXCLUDE = [
    (r'//.*', '单行注释'),
    (r'/\*.*?\*/', '多行注释'),
    (r'AppLogger\.\w+.*?"', 'AppLogger调用'),
    (r'\b\.log[dewv]?\(', '日志调用'),
    (r'Log\.[\w\s]+', '日志类'),
]

LOG_CALL_PATTERNS = [
    re.compile(r'\bAppLogger\w*\.\w+\s*\('),
    re.compile(r'\bStreamLogger\w*\.\w+\s*\('),
    re.compile(r'\bTimber\.\w+\s*\('),
    re.compile(r'\bandroid\.util\.Log\.\w+\s*\('),
    re.compile(r'\bLog\.\w+\s*\('),
    re.compile(r'\blog(?:Verbose|Debug|Info|Warn|Warning|Error|Trace)\s*\('),
    re.compile(r'\blog[VDIWE]\s*\('),
    re.compile(r'\bprintln\s*\('),
    re.compile(r'\bprint\s*\('),
    re.compile(r'\bSystem\.out\.println\s*\('),
    re.compile(r'\b\w+\.log[dewv]?\s*\('),
]

EXCLUDED_PATH_SUBSTRINGS = [
    '\\core\\config\\',
    '\\ui\\features\\settings\\screens\\TagMarketBilingualData.kt',
    '\\data\\preferences\\CharacterCardBilingualData.kt',
    '\\data\\preferences\\PromptBilingualData.kt',
    '\\core\\tools\\defaultTool\\standard\\StandardUITools.kt'
]

def _should_skip_file(file_path: Path) -> bool:
    normalized = str(file_path).replace('/', '\\').lower()
    for sub in EXCLUDED_PATH_SUBSTRINGS:
        if sub.lower() in normalized:
            return True
    return False

def _format_string_for_report(value: str, max_len: int = 200) -> str:
    s = value.replace('\r', '\\r').replace('\n', '\\n').replace('\t', '\\t')
    if len(s) > max_len:
        return s[:max_len] + '...'
    return s

def _is_in_log_call(content: str, string_start_index: int) -> bool:
    window_start = max(0, string_start_index - 800)
    prefix = content[window_start:string_start_index]

    best_paren_index = -1
    for pattern in LOG_CALL_PATTERNS:
        last_match = None
        for m in pattern.finditer(prefix):
            last_match = m
        if last_match is None:
            continue
        paren_index = window_start + last_match.end() - 1
        if paren_index > best_paren_index:
            best_paren_index = paren_index

    if best_paren_index < 0:
        return False

    balance = 0
    for ch in content[best_paren_index:string_start_index]:
        if ch == '(':
            balance += 1
        elif ch == ')':
            balance -= 1
            if balance <= 0:
                return False
        elif ch == ';' and balance == 0:
            return False

    return balance > 0

def _iter_kotlin_string_literals(content: str) -> List[Dict]:
    results: List[Dict] = []

    i = 0
    line_num = 1
    line_start = 0

    state = 'NORMAL'
    block_depth = 0
    string_start = -1
    raw_string = False

    while i < len(content):
        ch = content[i]

        if ch == '\n':
            line_num += 1
            line_start = i + 1

        if state == 'NORMAL':
            if content.startswith('//', i):
                state = 'LINE_COMMENT'
                i += 2
                continue
            if content.startswith('/*', i):
                state = 'BLOCK_COMMENT'
                block_depth = 1
                i += 2
                continue
            if content.startswith('"""', i):
                state = 'STRING'
                raw_string = True
                string_start = i
                start_line = line_num
                start_col = i - line_start + 1
                i += 3
                continue
            if ch == '"':
                state = 'STRING'
                raw_string = False
                string_start = i
                start_line = line_num
                start_col = i - line_start + 1
                i += 1
                continue

            i += 1
            continue

        if state == 'LINE_COMMENT':
            if ch == '\n':
                state = 'NORMAL'
            i += 1
            continue

        if state == 'BLOCK_COMMENT':
            if content.startswith('/*', i):
                block_depth += 1
                i += 2
                continue
            if content.startswith('*/', i):
                block_depth -= 1
                i += 2
                if block_depth <= 0:
                    state = 'NORMAL'
                continue
            i += 1
            continue

        if state == 'STRING':
            if raw_string:
                if content.startswith('"""', i):
                    end_index = i + 3
                    value = content[string_start + 3:i]
                    results.append({
                        'start': string_start,
                        'end': end_index,
                        'value': value,
                        'line': start_line,
                        'col': start_col,
                        'raw': True,
                    })
                    state = 'NORMAL'
                    raw_string = False
                    string_start = -1
                    i = end_index
                    continue
                i += 1
                continue
 
            if ch == '\\':
                i += 2
                continue
            if ch == '"':
                end_index = i + 1
                value = content[string_start + 1:i]
                results.append({
                    'start': string_start,
                    'end': end_index,
                    'value': value,
                    'line': start_line,
                    'col': start_col,
                    'raw': False,
                })
                state = 'NORMAL'
                string_start = -1
                i = end_index
                continue
 
            i += 1
            continue

    return results

def should_exclude_string(content: str, line: str, string_pos: Tuple[int, int]) -> bool:
    """判断字符串是否应该被排除"""
    # 提取字符串内容
    try:
        quote_start, quote_end = string_pos
        string_content = line[quote_start+1:quote_end-1]
    except Exception as e:
        return False

    # 排除超长字符串（大段提示词）- 超过100个字符
    if len(string_content) > 100:
        return True

    # 排除纯数字、符号等
    if not re.search(r'[\u4e00-\u9fff]', string_content):
        return True

    # 排除占位符模板
    if string_content in ['%s', '%d', '%1$s', '%1$d', '%2$s', '%2$d', '']:
        return True

    return False

def count_chinese_strings_in_file(file_path: Path) -> Dict:
    """统计单个文件中的中文字符串"""
    result = {
        'file': str(file_path.relative_to('D:\\Code\\prog\\assistance')),
        'total': 0,
        'excluded_comments': 0,
        'excluded_logs': 0,
        'excluded_long_strings': 0,
        'remaining': 0,
        'examples': [],
        'items': []
    }

    try:
        content = file_path.read_text(encoding='utf-8', errors='ignore')
 
        literals = _iter_kotlin_string_literals(content)
        for lit in literals:
            string_content = lit['value']
            if not re.search(r'[\u4e00-\u9fff]', string_content):
                continue

            result['total'] += 1

            if _is_in_log_call(content, lit['start']):
                result['excluded_logs'] += 1
                continue

            mock_line = '"' + string_content + '"'
            if should_exclude_string(content, mock_line, (0, len(mock_line))):
                if len(string_content) > 100:
                    result['excluded_long_strings'] += 1
                continue

            item = {
                'line': lit['line'],
                'col': lit['col'],
                'string': '"' + _format_string_for_report(string_content) + '"',
                'length': len(string_content)
            }
            result['items'].append(item)

            if len(result['examples']) < 3:
                result['examples'].append({
                    'line': lit['line'],
                    'string': item['string'],
                    'length': item['length']
                })

            result['remaining'] += 1

    except Exception as e:
        print(f"Error processing {file_path}: {e}")

    return result

def analyze_directory(root_dir: Path) -> Dict:
    """分析整个目录"""
    print(f"[SCAN] Scanning directory: {root_dir}")
    print("="*80)

    kt_files = list(root_dir.rglob('*.kt'))
    print(f"[FILES] Found {len(kt_files)} Kotlin files")

    results = []
    total_stats = {
        'total': 0,
        'excluded_comments': 0,
        'excluded_logs': 0,
        'excluded_long_strings': 0,
        'remaining': 0,
        'files_with_strings': 0
    }

    # 按模块统计
    module_stats = defaultdict(lambda: {
        'total': 0,
        'remaining': 0,
        'files': 0
    })

    for kt_file in kt_files:
        if _should_skip_file(kt_file):
            continue
        result = count_chinese_strings_in_file(kt_file)
        if result['total'] > 0:
            results.append(result)
            total_stats['total'] += result['total']
            total_stats['excluded_comments'] += result['excluded_comments']
            total_stats['excluded_logs'] += result['excluded_logs']
            total_stats['excluded_long_strings'] += result['excluded_long_strings']
            total_stats['remaining'] += result['remaining']
            total_stats['files_with_strings'] += 1

            # 按模块统计
            try:
                rel_path = str(result['file'])
                if 'ui/features' in rel_path:
                    module = rel_path.split('ui\\features\\')[1].split('\\')[0]
                    module_stats[module]['total'] += result['total']
                    module_stats[module]['remaining'] += result['remaining']
                    module_stats[module]['files'] += 1
            except:
                pass

    return {
        'results': results,
        'total_stats': total_stats,
        'module_stats': dict(module_stats)
    }

def print_report(analysis: Dict):
    """打印统计报告"""
    print("\n" + "="*80)
    print("[REPORT] Chinese Strings Statistics Report")
    print("="*80)

    stats = analysis['total_stats']

    print(f"\n[OVERALL] Overall Statistics:")
    print(f"  Total strings found: {stats['total']}")
    print(f"  Excluded logs: {stats['excluded_logs']}")
    print(f"  Excluded long prompts(>100 chars): {stats['excluded_long_strings']}")
    print(f"  Remaining UI strings: {stats['remaining']}")
    print(f"  Files with Chinese strings: {stats['files_with_strings']}")

    output_file = Path(__file__).resolve().parent / 'chinese_strings_detailed.txt'
    with open(output_file, 'w', encoding='utf-8') as f:
        stats = analysis['total_stats']
        f.write("[OVERALL]\n")
        f.write(f"Total strings found: {stats['total']}\n")
        f.write(f"Excluded logs: {stats['excluded_logs']}\n")
        f.write(f"Excluded long prompts(>100 chars): {stats['excluded_long_strings']}\n")
        f.write(f"Remaining UI strings: {stats['remaining']}\n")
        f.write(f"Files with Chinese strings: {stats['files_with_strings']}\n")
        f.write("\n")

        f.write("[BY MODULE]\n")
        module_items = sorted(
            analysis['module_stats'].items(),
            key=lambda x: x[1]['remaining'],
            reverse=True
        )
        for module, mod_stats in module_items:
            if mod_stats['remaining'] > 0:
                f.write(f"{module}: {mod_stats['remaining']} (files={mod_stats['files']})\n")
        f.write("\n")

        f.write("[DETAIL]\n")
        f.write("="*80 + "\n")
        for result in sorted(analysis['results'], key=lambda r: r['remaining'], reverse=True):
            if result['remaining'] <= 0:
                continue
            f.write(f"FILE: {result['file']}\n")
            f.write(
                f"  remaining={result['remaining']} total={result['total']} excluded_logs={result['excluded_logs']} excluded_long={result['excluded_long_strings']}\n"
            )
            for item in result.get('items', []):
                f.write(f"  L{item['line']}:{item['col']} ({item['length']} chars) {item['string']}\n")
            f.write("\n")

    print(f"[DONE] Results saved to: {output_file.absolute()}")

def main():
    repo_root = Path(__file__).resolve().parents[2]
    root_dir = repo_root / 'app' / 'src' / 'main' / 'java' / 'com' / 'ai' / 'assistance' / 'operit'

    if not root_dir.exists():
        print(f"[ERROR] Directory does not exist: {root_dir}")
        return

    analysis = analyze_directory(root_dir)
    print_report(analysis)

if __name__ == '__main__':
    main()
