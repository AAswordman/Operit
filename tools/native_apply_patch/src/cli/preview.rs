use similar::TextDiff;
use std::fmt::Write;
use std::io::{self, IsTerminal};
use std::os::unix::ffi::OsStrExt;
use std::path::{Path, PathBuf};

#[derive(Clone, PartialEq)]
pub(super) enum Content {
    Missing,
    Text(String),
    Binary(Option<Vec<u8>>),
    Link(PathBuf),
}

impl Content {
    pub fn from_bytes(bytes: Vec<u8>) -> Self {
        match String::from_utf8(bytes) {
            Ok(text) if !text.contains('\0') => Self::Text(text),
            Ok(text) => Self::Binary(Some(text.into_bytes())),
            Err(error) => Self::Binary(Some(error.into_bytes())),
        }
    }
}

pub(super) struct Change {
    pub path: PathBuf,
    pub before: Content,
    pub after: Content,
}

// 文件名使用可逆的引号/转义，避免空格、换行或非 UTF-8 路径破坏预览头。
fn quote(bytes: &[u8]) -> String {
    if let Ok(text) = std::str::from_utf8(bytes) {
        if text
            .chars()
            .all(|c| !c.is_whitespace() && !c.is_control() && c != '"' && c != '\\')
        {
            return text.to_owned();
        }
        return serde_json::to_string(text).expect("path text must serialize");
    }
    let mut result = String::from("\"");
    for byte in bytes {
        match *byte {
            b'"' | b'\\' => {
                result.push('\\');
                result.push(*byte as char);
            }
            0x20..=0x7e => result.push(*byte as char),
            _ => {
                write!(result, "\\{byte:03o}").expect("writing to a string cannot fail");
            }
        }
    }
    result.push('"');
    result
}

fn label(prefix: &str, path: &Path, cwd: &Path) -> String {
    let relative = path.strip_prefix(cwd).unwrap_or(path);
    let mut bytes = prefix.as_bytes().to_vec();
    bytes.extend_from_slice(relative.as_os_str().as_bytes());
    quote(&bytes)
}

/// 按实际模拟操作的顺序展示；移动显示目标写入和源路径删除。
pub(super) fn render(changes: &[Change], cwd: &Path) -> String {
    let mut output = String::new();
    for change in changes {
        // 已知快照相等时省略无变化操作；未解码的旧内容不视作空文件。
        if change.before == change.after {
            continue;
        }
        let old = label("a/", &change.path, cwd);
        let new = label("b/", &change.path, cwd);
        writeln!(output, "diff --git {old} {new}").expect("writing to a string cannot fail");
        let from = if matches!(change.before, Content::Missing) {
            "/dev/null"
        } else {
            &old
        };
        let to = if matches!(change.after, Content::Missing) {
            "/dev/null"
        } else {
            &new
        };
        if let Content::Link(target) = &change.before {
            writeln!(
                output,
                "deleted symbolic link {old} -> {}",
                quote(target.as_os_str().as_bytes())
            )
            .expect("writing to a string cannot fail");
            continue;
        }
        if matches!(change.before, Content::Binary(_)) || matches!(change.after, Content::Binary(_))
        {
            writeln!(output, "Binary files {from} and {to} differ")
                .expect("writing to a string cannot fail");
            continue;
        }
        let before = match &change.before {
            Content::Text(text) => text.as_str(),
            _ => "",
        };
        let after = match &change.after {
            Content::Text(text) => text.as_str(),
            _ => "",
        };
        if before == after {
            // 空文件新增/删除也要显示路径，不能因没有文本行而丢失操作。
            writeln!(output, "--- {from}\n+++ {to}").expect("writing to a string cannot fail");
        } else {
            output.push_str(
                &TextDiff::from_lines(before, after)
                    .unified_diff()
                    .context_radius(3)
                    .header(from, to)
                    .to_string(),
            );
        }
    }
    output
}

pub(super) fn terminal_colors_enabled() -> bool {
    io::stdout().is_terminal()
        && std::env::var_os("NO_COLOR").is_none()
        && std::env::var_os("TERM").is_none_or(|term| term != "dumb")
}

pub(super) fn colorize(text: &str) -> String {
    let mut output = String::new();
    for line in text.split_inclusive('\n') {
        let color = if line.starts_with("diff --git ")
            || line.starts_with("--- ")
            || line.starts_with("+++ ")
        {
            "\x1b[1m"
        } else if line.starts_with("@@") {
            "\x1b[36m"
        } else if line.starts_with('+') {
            "\x1b[32m"
        } else if line.starts_with('-') {
            "\x1b[31m"
        } else {
            ""
        };
        if color.is_empty() {
            output.push_str(line);
        } else {
            output.push_str(color);
            output.push_str(line.strip_suffix('\n').unwrap_or(line));
            output.push_str("\x1b[0m");
            if line.ends_with('\n') {
                output.push('\n');
            }
        }
    }
    output
}
