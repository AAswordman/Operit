use globset::{Glob, GlobSet, GlobSetBuilder};
use grep_matcher::Matcher;
use grep_regex::{RegexMatcher, RegexMatcherBuilder};
use ignore::WalkBuilder;
use jni::objects::{JClass, JObjectArray, JString};
use jni::sys::{jboolean, jint, jstring, JNI_FALSE};
use jni::JNIEnv;
use serde::Serialize;
use std::cmp::{max, min};
use std::collections::BTreeSet;
use std::fs::File;
use std::io::{BufRead, BufReader, Read};
use std::path::{Path, PathBuf};

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SearchResponse {
    success: bool,
    error: String,
    files_searched: usize,
    blocks: Vec<SearchBlock>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SearchBlock {
    file_path: String,
    first_match_line: usize,
    line_content: String,
    match_context: String,
    match_count: usize,
}

struct SearchOptions {
    path: PathBuf,
    patterns: Vec<String>,
    file_pattern: String,
    case_insensitive: bool,
    context_lines: usize,
    max_results: usize,
}

struct FileMatch {
    line_number: usize,
    text: String,
}

#[no_mangle]
pub extern "system" fn Java_com_ai_assistance_operit_util_ripgrep_NativeRipgrep_searchJson(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
    patterns: JObjectArray,
    file_pattern: JString,
    case_insensitive: jboolean,
    _literal: jboolean,
    context_lines: jint,
    max_results: jint,
) -> jstring {
    let result = read_options(
        &mut env,
        path,
        patterns,
        file_pattern,
        case_insensitive != JNI_FALSE,
        context_lines,
        max_results,
    )
    .and_then(run_search);

    let response = match result {
        Ok(response) => response,
        Err(error) => SearchResponse {
            success: false,
            error,
            files_searched: 0,
            blocks: Vec::new(),
        },
    };

    let json = serde_json::to_string(&response).unwrap_or_else(|error| {
        format!(
            r#"{{"success":false,"error":"failed to encode native grep result: {}","filesSearched":0,"blocks":[]}}"#,
            escape_json_fragment(&error.to_string())
        )
    });
    env.new_string(json)
        .map(|value| value.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

fn read_options(
    env: &mut JNIEnv,
    path: JString,
    patterns: JObjectArray,
    file_pattern: JString,
    case_insensitive: bool,
    context_lines: jint,
    max_results: jint,
) -> Result<SearchOptions, String> {
    let path: String = env
        .get_string(&path)
        .map_err(|error| format!("failed to read path: {error}"))?
        .into();
    let file_pattern: String = env
        .get_string(&file_pattern)
        .map_err(|error| format!("failed to read file pattern: {error}"))?
        .into();
    let pattern_count = env
        .get_array_length(&patterns)
        .map_err(|error| format!("failed to read pattern count: {error}"))?;
    let mut pattern_values = Vec::with_capacity(pattern_count as usize);
    for index in 0..pattern_count {
        let pattern = env
            .get_object_array_element(&patterns, index)
            .map_err(|error| format!("failed to read pattern {index}: {error}"))?;
        let pattern: String = env
            .get_string(&JString::from(pattern))
            .map_err(|error| format!("failed to convert pattern {index}: {error}"))?
            .into();
        if !pattern.trim().is_empty() {
            pattern_values.push(pattern);
        }
    }

    if path.trim().is_empty() {
        return Err("path is required".to_string());
    }
    if pattern_values.is_empty() {
        return Err("pattern is required".to_string());
    }

    Ok(SearchOptions {
        path: PathBuf::from(path),
        patterns: pattern_values,
        file_pattern,
        case_insensitive,
        context_lines: context_lines.max(0) as usize,
        max_results: max_results.max(0) as usize,
    })
}

fn run_search(options: SearchOptions) -> Result<SearchResponse, String> {
    let matchers = build_matchers(&options)?;
    let include_globs = build_include_globs(&options.file_pattern)?;
    let exclude_globs = build_exclude_globs()?;
    let mut files_searched = 0usize;
    let mut blocks = Vec::new();

    if options.max_results == 0 {
        return Ok(SearchResponse {
            success: true,
            error: String::new(),
            files_searched,
            blocks,
        });
    }

    let mut walker = WalkBuilder::new(&options.path);
    walker.hidden(false);
    walker.git_ignore(true);
    walker.git_global(true);
    walker.git_exclude(true);
    walker.parents(true);

    for entry in walker.build() {
        let entry = entry.map_err(|error| error.to_string())?;
        let file_type = entry.file_type();
        if !file_type.map(|value| value.is_file()).unwrap_or(false) {
            continue;
        }

        let path = entry.path();
        if !path_matches(path, &options.path, include_globs.as_ref(), &exclude_globs) {
            continue;
        }
        if is_probably_binary(path) {
            continue;
        }

        files_searched += 1;
        if let Some(block) = search_file(path, &matchers, options.context_lines)? {
            blocks.push(block);
            if blocks.len() >= options.max_results {
                break;
            }
        }
    }

    Ok(SearchResponse {
        success: true,
        error: String::new(),
        files_searched,
        blocks,
    })
}

fn build_matchers(options: &SearchOptions) -> Result<Vec<RegexMatcher>, String> {
    options
        .patterns
        .iter()
        .map(|pattern| {
            let mut builder = RegexMatcherBuilder::new();
            builder.case_insensitive(options.case_insensitive);
            builder
                .build(pattern)
                .map_err(|error| format!("invalid regex `{pattern}`: {error}"))
        })
        .collect()
}

fn build_include_globs(file_pattern: &str) -> Result<Option<GlobSet>, String> {
    let pattern = file_pattern.trim();
    if pattern.is_empty() || pattern == "*" {
        return Ok(None);
    }

    let mut builder = GlobSetBuilder::new();
    builder.add(Glob::new(pattern).map_err(|error| error.to_string())?);
    if !pattern.contains('/') && !pattern.contains('\\') {
        builder.add(Glob::new(&format!("**/{pattern}")).map_err(|error| error.to_string())?);
    }
    builder.build().map(Some).map_err(|error| error.to_string())
}

fn build_exclude_globs() -> Result<GlobSet, String> {
    let mut builder = GlobSetBuilder::new();
    for pattern in [
        ".backup/**",
        "**/.backup/**",
        ".operit/**",
        "**/.operit/**",
        "backup/**",
        "**/backup/**",
    ] {
        builder.add(Glob::new(pattern).map_err(|error| error.to_string())?);
    }
    builder.build().map_err(|error| error.to_string())
}

fn path_matches(
    path: &Path,
    root: &Path,
    include_globs: Option<&GlobSet>,
    exclude_globs: &GlobSet,
) -> bool {
    let relative = path.strip_prefix(root).unwrap_or(path);
    if exclude_globs.is_match(relative) || exclude_globs.is_match(path) {
        return false;
    }
    match include_globs {
        Some(globs) => globs.is_match(relative) || globs.is_match(path),
        None => true,
    }
}

fn is_probably_binary(path: &Path) -> bool {
    let mut file = match File::open(path) {
        Ok(file) => file,
        Err(_) => return true,
    };
    let mut buffer = [0u8; 8192];
    match file.read(&mut buffer) {
        Ok(size) => buffer[..size].contains(&0),
        Err(_) => true,
    }
}

fn search_file(
    path: &Path,
    matchers: &[RegexMatcher],
    context_lines: usize,
) -> Result<Option<SearchBlock>, String> {
    let file =
        File::open(path).map_err(|error| format!("failed to open {}: {error}", path.display()))?;
    let reader = BufReader::new(file);
    let mut lines = Vec::new();
    let mut matches = Vec::new();

    for (index, line) in reader.split(b'\n').enumerate() {
        let bytes = line.map_err(|error| format!("failed to read {}: {error}", path.display()))?;
        let text = String::from_utf8_lossy(&bytes)
            .trim_end_matches('\r')
            .to_string();
        let is_match = matchers
            .iter()
            .any(|matcher| matcher.is_match(text.as_bytes()).unwrap_or(false));
        let line_number = index + 1;
        if is_match {
            matches.push(FileMatch {
                line_number,
                text: text.clone(),
            });
        }
        lines.push(text);
    }

    if matches.is_empty() {
        return Ok(None);
    }

    let mut context_indexes = BTreeSet::new();
    for item in &matches {
        let start = max(item.line_number.saturating_sub(context_lines), 1);
        let end = min(item.line_number + context_lines, lines.len());
        for line_number in start..=end {
            context_indexes.insert(line_number);
        }
    }

    // Emit every context line with its real source line number ("<n>|<text>",
    // matched lines additionally marked with ">"). The Kotlin renderer trusts
    // these embedded numbers, so clamped windows at file start/end and merged
    // multi-match blocks can no longer desync the rendered line numbers.
    let match_lines: BTreeSet<usize> = matches.iter().map(|item| item.line_number).collect();
    let number_width = lines.len().to_string().len();

    const MAX_CONTEXT_CHARS: usize = 4000;
    let mut match_context = String::new();
    let mut context_truncated = false;
    for line_number in context_indexes {
        let Some(line) = lines.get(line_number - 1) else {
            continue;
        };
        let clipped = clip_text(line, 400);
        let formatted = if match_lines.contains(&line_number) {
            format!("{:>number_width$}|>{clipped}", line_number)
        } else {
            format!("{:>number_width$}| {clipped}", line_number)
        };
        // Clip at line boundaries only: a mid-line cut could sever the number
        // prefix and break the "<n>|" contract the renderer relies on.
        if !match_context.is_empty() && match_context.len() + 1 + formatted.len() > MAX_CONTEXT_CHARS
        {
            context_truncated = true;
            break;
        }
        if !match_context.is_empty() {
            match_context.push('\n');
        }
        match_context.push_str(&formatted);
    }
    if context_truncated {
        match_context.push_str("\n...");
    }

    let line_content = if matches.len() == 1 {
        clip_text(&matches[0].text, 300)
    } else {
        let digest = matches
            .iter()
            .take(5)
            .map(|item| clip_text(&item.text, 80))
            .collect::<Vec<_>>()
            .join(" | ");
        format!("{} matches: {}...", matches.len(), clip_text(&digest, 200))
    };

    Ok(Some(SearchBlock {
        file_path: path.to_string_lossy().to_string(),
        first_match_line: matches[0].line_number,
        line_content,
        match_context,
        match_count: matches.len(),
    }))
}

fn clip_text(text: &str, max_chars: usize) -> String {
    if text.chars().count() <= max_chars {
        return text.to_string();
    }
    let mut clipped = text.chars().take(max_chars).collect::<String>();
    clipped.push_str("...");
    clipped
}

fn escape_json_fragment(text: &str) -> String {
    text.replace('\\', "\\\\").replace('"', "\\\"")
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicU64, Ordering};

    static TEMP_FILE_SEQ: AtomicU64 = AtomicU64::new(0);

    struct TestFile {
        path: PathBuf,
    }

    impl TestFile {
        /// Writes `total_lines` numbered lines ("line 1" .. "line N") and plants
        /// `needle ` at the given 1-based match line numbers.
        fn write(total_lines: usize, match_line_numbers: &[usize]) -> TestFile {
            let seq = TEMP_FILE_SEQ.fetch_add(1, Ordering::Relaxed);
            let path = std::env::temp_dir().join(format!(
                "operit_ripgrep_test_{}_{}.txt",
                std::process::id(),
                seq
            ));
            let mut content = String::new();
            for line_number in 1..=total_lines {
                if match_line_numbers.contains(&line_number) {
                    content.push_str(&format!("needle line {line_number}\n"));
                } else {
                    content.push_str(&format!("line {line_number}\n"));
                }
            }
            std::fs::write(&path, content).expect("write temp grep test file");
            TestFile { path }
        }
    }

    impl Drop for TestFile {
        fn drop(&mut self) {
            let _ = std::fs::remove_file(&self.path);
        }
    }

    fn needle_matcher() -> Vec<RegexMatcher> {
        vec![RegexMatcherBuilder::new()
            .build("needle")
            .expect("valid test regex")]
    }

    fn search(test_file: &TestFile, context_lines: usize) -> SearchBlock {
        search_file(&test_file.path, &needle_matcher(), context_lines)
            .expect("search_file should succeed")
            .expect("test file always contains a needle match")
    }

    /// Parses one emitted context line into (line number, is match marker, text).
    fn parse_context_line(line: &str) -> (usize, bool, &str) {
        let separator = line
            .find('|')
            .unwrap_or_else(|| panic!("context line must contain '|': {line}"));
        let number: usize = line[..separator]
            .trim()
            .parse()
            .unwrap_or_else(|_| panic!("context line must start with its line number: {line}"));
        let rest = &line[separator + 1..];
        match rest.strip_prefix('>') {
            Some(text) => (number, true, text),
            None => (
                number,
                false,
                rest.strip_prefix(' ').expect("context line must be '<n>| <text>'"),
            ),
        }
    }

    fn context_lines(block: &SearchBlock) -> Vec<(usize, bool, String)> {
        block
            .match_context
            .lines()
            .map(|line| {
                let (number, is_match, text) = parse_context_line(line);
                (number, is_match, text.to_string())
            })
            .collect()
    }

    #[test]
    fn match_at_file_start_keeps_real_line_numbers() {
        let file = TestFile::write(20, &[1]);
        let block = search(&file, 3);

        let lines = context_lines(&block);
        assert_eq!(
            lines.iter().map(|entry| entry.0).collect::<Vec<_>>(),
            vec![1, 2, 3, 4],
            "clamped window at file start must still carry the true line numbers"
        );
        assert_eq!(lines[0], (1, true, "needle line 1".to_string()));
        assert!(lines.iter().all(|entry| entry.0 >= 1));
    }

    #[test]
    fn match_at_file_end_keeps_real_line_numbers() {
        let file = TestFile::write(20, &[20]);
        let block = search(&file, 3);

        let lines = context_lines(&block);
        assert_eq!(
            lines.iter().map(|entry| entry.0).collect::<Vec<_>>(),
            vec![17, 18, 19, 20]
        );
        assert_eq!(lines.last().unwrap().0, 20);
        assert!(lines.last().unwrap().1, "final line must be marked as match");
    }

    #[test]
    fn match_in_file_middle_keeps_symmetric_window() {
        let file = TestFile::write(20, &[10]);
        let block = search(&file, 3);

        let lines = context_lines(&block);
        assert_eq!(
            lines.iter().map(|entry| entry.0).collect::<Vec<_>>(),
            vec![7, 8, 9, 10, 11, 12, 13]
        );
        let match_entry = lines.iter().find(|entry| entry.1).expect("one match line");
        assert_eq!(match_entry.0, 10);
        assert_eq!(match_entry.2, "needle line 10");
    }

    #[test]
    fn different_context_line_counts() {
        let file = TestFile::write(20, &[10]);

        let zero = context_lines(&search(&file, 0));
        assert_eq!(zero.len(), 1);
        assert_eq!(zero[0].0, 10);
        assert!(zero[0].1);

        let one = context_lines(&search(&file, 1));
        assert_eq!(
            one.iter().map(|entry| entry.0).collect::<Vec<_>>(),
            vec![9, 10, 11]
        );

        let five = context_lines(&search(&file, 5));
        assert_eq!(
            five.iter().map(|entry| entry.0).collect::<Vec<_>>(),
            (5..=15).collect::<Vec<_>>()
        );
    }

    #[test]
    fn merged_multi_match_block_marks_every_match_with_true_numbers() {
        // Two matches whose context windows do not touch: the merged context is
        // non-contiguous, which is exactly what broke center-based rendering.
        let file = TestFile::write(30, &[2, 25]);
        let block = search(&file, 1);

        let lines = context_lines(&block);
        assert_eq!(
            lines.iter().map(|entry| entry.0).collect::<Vec<_>>(),
            vec![1, 2, 3, 24, 25, 26]
        );
        let marked: Vec<usize> = lines
            .iter()
            .filter(|entry| entry.1)
            .map(|entry| entry.0)
            .collect();
        assert_eq!(marked, vec![2, 25]);
        assert_eq!(block.first_match_line, 2);
        assert_eq!(block.match_count, 2);
    }

    #[test]
    fn context_clipping_keeps_complete_numbered_lines() {
        // 4000 chars fits roughly ten 400-char lines; with context 10 around a
        // single match the 21-line window exceeds the budget and must clip at a
        // line boundary so every retained line keeps a parseable number prefix.
        let total_lines = 40;
        let seq = TEMP_FILE_SEQ.fetch_add(1, Ordering::Relaxed);
        let path = std::env::temp_dir().join(format!(
            "operit_ripgrep_clip_test_{}_{}.txt",
            std::process::id(),
            seq
        ));
        let filler = "x".repeat(380);
        let mut content = String::new();
        for line_number in 1..=total_lines {
            if line_number == 20 {
                content.push_str(&format!("needle {filler} {line_number}\n"));
            } else {
                content.push_str(&format!("{filler} {line_number}\n"));
            }
        }
        std::fs::write(&path, content).expect("write temp grep clip test file");
        let file = TestFile { path };

        let block = search(&file, 10);
        let raw_lines: Vec<&str> = block.match_context.lines().collect();
        assert_eq!(raw_lines.last(), Some(&"..."), "truncated context ends with marker");
        for line in &raw_lines[..raw_lines.len() - 1] {
            parse_context_line(line);
        }
        assert!(block.match_context.len() <= 4000 + "\n...".len());
    }
}
