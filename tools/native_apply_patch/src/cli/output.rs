use crate::{AppliedPatchDelta, AppliedPatchFileChange};
use serde_json::{Value, json};
use std::io::{self, Write};

pub(super) const VERSION: &str = env!("CARGO_PKG_VERSION");

pub(super) fn document(
    mode: &str,
    code: i32,
    message: &str,
    error_kind: Option<&str>,
    delta: Option<&AppliedPatchDelta>,
) -> Value {
    let changes: Vec<Value> = delta.map(|delta| delta.changes().iter().map(|item| {
        let (operation, destination) = match &item.change {
            AppliedPatchFileChange::Add { .. } => ("add", None),
            AppliedPatchFileChange::Delete { .. } => ("delete", None),
            AppliedPatchFileChange::Update { move_path: Some(path), .. } => ("move", Some(path.inferred_native_path_string())),
            AppliedPatchFileChange::Update { .. } => ("update", None),
        };
        let mut value = json!({"operation": operation, "path": item.path.inferred_native_path_string()});
        if let Some(destination) = destination { value["destination"] = json!(destination); }
        value
    }).collect()).unwrap_or_default();
    let mut value = json!({
        "schema_version": 1,
        "tool": "apply_patch",
        "version": VERSION,
        "mode": mode,
        "success": code == 0,
        "exit_code": code,
        "changes": changes,
        "partial": code != 0 && mode == "apply" && delta.is_some_and(|delta| !delta.is_empty() || !delta.is_exact()),
        "exact": delta.map(AppliedPatchDelta::is_exact),
        "message": message,
        "error": error_kind.map(|kind| json!({"kind": kind, "message": message})),
    });
    if mode == "diff" {
        value["diff"] = Value::Null;
    }
    value
}

pub(super) fn emit_json(value: &Value, exit_code: i32) -> i32 {
    let mut out = io::stdout().lock();
    let result = serde_json::to_writer(&mut out, value)
        .map_err(io::Error::other)
        .and_then(|_| writeln!(out))
        .and_then(|_| out.flush());
    if let Err(error) = result {
        eprintln!("Error: Failed to write JSON output.\n{error}");
        return 1;
    }
    exit_code
}

pub(super) fn failure(json: bool, mode: &str, code: i32, kind: &str, message: &str) -> i32 {
    if json {
        emit_json(&document(mode, code, message, Some(kind), None), code)
    } else {
        eprintln!("{message}");
        code
    }
}

// 成功时所有操作均已完成，使用解析结果列出删除二进制文件等没有文本快照的操作。
pub(super) fn completed_changes(patch: &str, cwd: &codex_utils_path_uri::PathUri) -> Vec<Value> {
    crate::parse_patch(patch).expect("successful patch must parse").hunks.into_iter().map(|hunk| {
        let path = hunk.resolve_path(cwd).expect("successful patch path must resolve");
        match hunk {
            crate::Hunk::AddFile { .. } => json!({"operation": "add", "path": path.inferred_native_path_string()}),
            crate::Hunk::DeleteFile { .. } => json!({"operation": "delete", "path": path.inferred_native_path_string()}),
            crate::Hunk::UpdateFile { move_path: Some(destination), .. } => json!({
                "operation": "move", "path": path.inferred_native_path_string(),
                "destination": cwd.join(&destination.to_string_lossy()).expect("successful move path must resolve").inferred_native_path_string(),
            }),
            crate::Hunk::UpdateFile { .. } => json!({"operation": "update", "path": path.inferred_native_path_string()}),
        }
    }).collect()
}
