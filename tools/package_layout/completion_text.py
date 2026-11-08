"""按显式清单执行名称替换与已确认无调用的资源清理，用于迁移和固定基线核对。"""
import re


def transform(text, path, manifest):
    if path.startswith('app/src/main/res/values') and path.endswith('.xml'):
        for key in manifest['removedStringKeys']:
            text = re.sub(
                r'(?m)^[ \t]*<string\s+name="' + re.escape(key) + r'"[^>]*>.*?</string>[ \t]*\r?\n?',
                '', text, flags=re.DOTALL
            )
    mapping = manifest['replacements']
    pattern = re.compile('|'.join(re.escape(key) for key in sorted(mapping, key=len, reverse=True)))
    text = pattern.sub(lambda match: mapping[match.group()], text)
    for item in manifest.get('fileReplacements', {}).get(path, []):
        text = text.replace(item['before'], item['after'])
    if path.startswith('app/src/main/res/values') and path.endswith('.xml'):
        text = re.sub(
            r'(<string\s+name="screen_title_ffmpeg"[^>]*>).*?(</string>)',
            r'\g<1>FFmpeg\g<2>', text, flags=re.DOTALL
        )
        text = re.sub(
            r'<!--.*?-->',
            lambda match: match.group().replace('FFmpeg Toolbox', 'FFmpeg 页面')
            .replace('Toolbox Categories', '工具页面分类')
            .replace('Toolbox Permission Manager', '工具权限管理')
            .replace('Toolbox Screen', '工具页面'),
            text, flags=re.DOTALL
        )
    return text