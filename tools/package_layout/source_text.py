from dataclasses import dataclass
from functools import lru_cache
import hashlib
import re


@dataclass(frozen=True)
class Token:
    kind: str
    text: str
    start: int
    end: int


def tokens(text: str, kotlin: bool = True):
    result = []
    length = len(text)

    def add(kind, start, end):
        if end > start:
            result.append(Token(kind, text[start:end], start, end))

    def quoted(position, quote, triple=False):
        delimiter = quote * (3 if triple else 1)
        start = position
        position += len(delimiter)
        literal_start = start
        while position < length:
            if text.startswith(delimiter, position):
                position += len(delimiter)
                if triple:
                    while position < length and text[position] == quote:
                        position += 1
                add('literal', literal_start, position)
                return position
            if not triple and text[position] == '\\':
                position += 2
                continue
            if kotlin and quote == '"' and text[position] == '$':
                if text.startswith('${', position):
                    add('literal', literal_start, position)
                    add('interpolation_open', position, position + 2)
                    position = code(position + 2, True)
                    literal_start = position
                    continue
                match = re.match(r'[A-Za-z_][A-Za-z_0-9]*', text[position + 1:])
                if match:
                    add('literal', literal_start, position + 1)
                    end = position + 1 + len(match.group())
                    add('identifier', position + 1, end)
                    position = end
                    literal_start = position
                    continue
            position += 1
        raise ValueError(f'字符串未闭合，位置 {start}')

    def code(position, stop_at_brace=False):
        braces = 0
        while position < length:
            start = position
            char = text[position]
            if char.isspace():
                position += 1
                while position < length and text[position].isspace():
                    position += 1
                add('space', start, position)
            elif text.startswith('//', position):
                end = text.find('\n', position)
                position = length if end < 0 else end
                add('comment', start, position)
            elif text.startswith('/*', position):
                position += 2
                nesting = 1
                while position < length and nesting:
                    if text.startswith('/*', position):
                        nesting += 1
                        position += 2
                    elif text.startswith('*/', position):
                        nesting -= 1
                        position += 2
                    else:
                        position += 1
                if nesting:
                    raise ValueError(f'注释未闭合，位置 {start}')
                add('comment', start, position)
            elif text.startswith('"""', position):
                position = quoted(position, '"', True)
            elif char in {'"', "'"}:
                position = quoted(position, char)
            elif kotlin and char == '`':
                end = text.find('`', position + 1)
                if end < 0:
                    raise ValueError(f'反引号未闭合，位置 {start}')
                position = end + 1
                add('identifier', start, position)
            elif char.isalpha() or char == '_':
                position += 1
                while position < length and (text[position].isalnum() or text[position] == '_'):
                    position += 1
                add('identifier', start, position)
            else:
                position += 1
                if char == '}' and stop_at_brace and braces == 0:
                    add('interpolation_close', start, position)
                    return position
                add('punctuation', start, position)
                if char == '{':
                    braces += 1
                elif char == '}':
                    braces -= 1
        if stop_at_brace:
            raise ValueError('字符串插值未闭合')
        return position

    code(0)
    return result


def header(text: str):
    package = re.search(r'(?m)^[ \t]*package[ \t]+([^\s;]+)[ \t]*;?[ \t]*(?:\r?\n|$)', text)
    if not package:
        return {'package': '', 'prefix': '', 'end': 0, 'imports': []}
    position = package.end()
    last_header = position
    imports = []
    # 声明头结束后不再匹配正文或多行字符串中的 import 文本。
    block_depth = 0
    for line in text[position:].splitlines(keepends=True):
        stripped = line.strip()
        if block_depth:
            block_depth += line.count('/*') - line.count('*/')
            position += len(line)
            continue
        if stripped.startswith('/*'):
            block_depth = line.count('/*') - line.count('*/')
            position += len(line)
            continue
        if not stripped or stripped.startswith('//'):
            position += len(line)
            continue
        match = re.fullmatch(r'import[ \t]+([^;\r\n]+?)[ \t]*;?', stripped)
        if not match:
            break
        imports.append({'value': match.group(1).strip(), 'start': position, 'end': position + len(line)})
        position += len(line)
        last_header = position
    return {'package': package.group(1), 'package_start': package.start(), 'package_end': package.end(), 'prefix': text[:package.start()], 'end': last_header, 'imports': imports}


@lru_cache(maxsize=16)
def name_pattern(keys):
    # 将公共包前缀合并为正则树，避免每个字符都重复尝试上千个完整名称。
    trie = {}
    for key in keys:
        branch = trie
        for char in key:
            branch = branch.setdefault(char, {})
        branch[''] = {}
    def emit(branch):
        choices = [re.escape(char) + emit(child) for char, child in sorted(branch.items()) if char]
        expression = choices[0] if len(choices) == 1 else '(?:' + '|'.join(choices) + ')'
        if not choices:
            return ''
        return '(?:' + expression + ')?' if '' in branch else expression
    return re.compile(r'(?<![\w$])' + emit(trie) + r'(?![\w])')

def replace_names(text, mapping):
    if not mapping:
        return text
    pattern = name_pattern(tuple(sorted(mapping)))
    return pattern.sub(lambda match: mapping[match.group()], text)
def rename_identifiers(text, mapping, kotlin=True):
    if not mapping:
        return text
    output = []
    position = 0
    for token in tokens(text, kotlin):
        value = token.text.strip('`')
        if token.kind != 'identifier' or value not in mapping:
            continue
        output.append(text[position:token.start])
        output.append('`' + mapping[value] + '`' if token.text.startswith('`') else mapping[value])
        position = token.end
    output.append(text[position:])
    return ''.join(output)


def canonical_body(text, manifest, kotlin=True):
    info = header(text)
    body = info['prefix'] + text[info['end']:].lstrip('\r\n') if info['package'] else text
    reverse = {new: old for old, new in {**manifest.get('packagePrefixes', {}), **manifest['qualifiedNames']}.items() if old != new}
    body = replace_names(body, reverse)
    reverse_paths = {new: old for old, new in manifest.get('referencePaths', {}).items() if old != new}
    body = replace_names(body, reverse_paths)
    body = rename_identifiers(body, {new: old for old, new in manifest['classRenames'].items()}, kotlin)
    for replacement in manifest.get('allowedWhitespaceReplacements', []):
        body = body.replace(replacement['before'], replacement['after'])
    return body


def body_hash(text, manifest, kotlin=True):
    return hashlib.sha256(canonical_body(text, manifest, kotlin).encode()).hexdigest()
