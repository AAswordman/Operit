mod check;
mod options;
mod output;
mod preview;

use codex_exec_server::ExecutorFileSystem;
use codex_utils_absolute_path::AbsolutePathBuf;
use codex_utils_path_uri::PathUri;
use options::Options;
use output::{VERSION, document, emit_json, failure};
use std::io::{self, Read, Write};

const HELP: &str = r#"Usage: apply_patch [OPTIONS] [PATCH]
       apply_patch [OPTIONS] --file PATH
       apply_patch [OPTIONS] < patch.txt

Apply a Codex-format patch. Relative paths use the current directory.

Options:
  -v, -V, --version  Print the Operit command version
  -h, --help        Show this help
  -c, --check       Check sequential patch operations without writing files
      --dry-run     Alias for --check
  -D, --diff        Preview per-operation unified diffs without writing files
      --no-overwrite
                    Refuse existing Add File and Move to destinations
  -q, --quiet       Suppress success summaries; keep errors and requested output
  -f, --file PATH   Read UTF-8 patch text from PATH; '-' means standard input
      --json        Print one JSON result object, including errors
      --            End option parsing; the following argument is PATCH

Operit rules:
  --no-overwrite is opt-in. Existing files, directories and symbolic links
  (including dangling links) block Add File and Move to destinations.
  Update File may still edit existing files. Delete then Add is allowed.
  The same policy applies to --check and --diff; actual creation is exclusive.
  Without --no-overwrite, the original Add File/Move to overwrite rules apply.

  --diff implies a read-only check; it can be combined with --check.
  Changes appear in execution order; moves show destination write and source
  removal. Binary changes are marked, and empty-file changes keep path headers.
  A failed preview emits an error, not a partial diff. Input stays Codex format.
  Color is automatic on a terminal; redirects, NO_COLOR or TERM=dumb disable it.
  --diff --json puts the plain preview in the JSON 'diff' field (null on error).
  A successful diff exits 0 even when changes exist; it does not run git diff.

  --quiet hides only human success summaries. Diffs, JSON, help/version and
  errors remain visible. No files are written by --check or --diff, even if
  a later operation fails. Checks cannot guarantee permissions, disk space
  or absence of concurrent changes during a later apply.
  Apply mode runs sequentially: failure does not undo earlier file changes.

Examples:
  apply_patch --no-overwrite --file change.patch
  apply_patch --diff --no-overwrite --file change.patch
  apply_patch --diff --file change.patch --json
  apply_patch --check --no-overwrite --quiet < change.patch

Patch format:
  *** Begin Patch
  *** Add File: example.txt
  +hello
  *** Update File: example.txt
  @@
  -hello
  +world
  *** End Patch

Delete a file with '*** Delete File: path'.
Move a file with '*** Move to: new/path' after an Update File header.
Quote heredoc delimiters (<<'PATCH') to preserve shell characters.
Exit codes: 0 = success, 1 = patch/I/O error, 2 = usage error.
"#;

pub fn main() -> ! {
    std::process::exit(run());
}

fn run() -> i32 {
    let args = std::env::args_os().skip(1).collect();
    let options = match Options::parse(args) {
        Ok(options) => options,
        Err((json, message)) => {
            return failure(json, "usage", 2, "usage", &format!("Error: {message}"));
        }
    };
    if options.help || options.version {
        let mode = if options.help { "help" } else { "version" };
        let message = if options.help {
            HELP.to_owned()
        } else {
            format!("apply_patch {VERSION}\n")
        };
        if options.json {
            return emit_json(&document(mode, 0, &message, None, None), 0);
        }
        print!("{message}");
        return 0;
    }
    let read_only = options.check || options.diff;
    let mode = if options.diff {
        "diff"
    } else if options.check {
        "check"
    } else {
        "apply"
    };
    let patch = if let Some(argument) = options.patch {
        match argument.into_string() {
            Ok(patch) => patch,
            Err(_) => {
                return failure(
                    options.json,
                    mode,
                    1,
                    "input",
                    "Error: apply_patch requires a UTF-8 PATCH argument.",
                );
            }
        }
    } else if let Some(path) = options.file.filter(|path| path.as_os_str() != "-") {
        match std::fs::read_to_string(&path) {
            Ok(patch) => patch,
            Err(error) => {
                return failure(
                    options.json,
                    mode,
                    1,
                    "input",
                    &format!(
                        "Error: Failed to read PATCH file {}.\n{error}",
                        path.display()
                    ),
                );
            }
        }
    } else {
        let mut patch = String::new();
        if let Err(error) = io::stdin().read_to_string(&mut patch) {
            return failure(
                options.json,
                mode,
                1,
                "input",
                &format!("Error: Failed to read PATCH from stdin.\n{error}"),
            );
        }
        if patch.is_empty() {
            return failure(
                options.json,
                mode,
                2,
                "usage",
                "Usage: apply_patch 'PATCH'\n       echo 'PATCH' | apply_patch",
            );
        }
        patch
    };
    let cwd = match AbsolutePathBuf::current_dir() {
        Ok(cwd) => PathUri::from_abs_path(&cwd),
        Err(error) => {
            return failure(
                options.json,
                mode,
                1,
                "io",
                &format!("Error: Failed to determine current directory.\n{error}"),
            );
        }
    };
    let runtime = match tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
    {
        Ok(runtime) => runtime,
        Err(error) => {
            return failure(
                options.json,
                mode,
                1,
                "io",
                &format!("Error: Failed to initialize runtime.\n{error}"),
            );
        }
    };
    let check_fs = if options.diff {
        check::CheckFileSystem::with_preview()
    } else {
        check::CheckFileSystem::default()
    };
    let filesystem: &dyn ExecutorFileSystem = if read_only {
        &check_fs
    } else {
        codex_exec_server::LOCAL_FS.as_ref()
    };
    let mut out = Vec::new();
    let mut err = Vec::new();
    let result = runtime.block_on(crate::apply_patch_with_options(
        &patch,
        crate::ApplyPatchOptions {
            no_overwrite: options.no_overwrite,
            ..Default::default()
        },
        &cwd,
        &mut out,
        &mut err,
        filesystem,
        None,
    ));
    let (code, delta) = match &result {
        Ok(delta) => (0, delta),
        Err(error) => (1, error.delta()),
    };
    if read_only && code == 0 {
        out = String::from_utf8_lossy(&out)
            .replacen(
                "Success. Updated the following files:",
                if options.diff {
                    "Success. Previewed the following files (no files written):"
                } else {
                    "Success. Checked the following files (no files written):"
                },
                1,
            )
            .into_bytes();
    }
    let diff = if options.diff && code == 0 {
        match check_fs.diff(&cwd) {
            Ok(diff) => Some(diff),
            Err(error) => {
                return failure(
                    options.json,
                    mode,
                    1,
                    "io",
                    &format!("Error: Failed to render preview.\n{error}"),
                );
            }
        }
    } else {
        None
    };
    if let Some(diff) = &diff
        && !options.json
    {
        out = if preview::terminal_colors_enabled() {
            preview::colorize(diff).into_bytes()
        } else {
            diff.as_bytes().to_vec()
        };
    }
    if options.json {
        let message = String::from_utf8_lossy(if code == 0 { &out } else { &err });
        let mut value = document(
            mode,
            code,
            message.trim_end(),
            if code == 0 { None } else { Some("patch") },
            Some(delta),
        );
        if code == 0 {
            value["changes"] = serde_json::json!(output::completed_changes(&patch, &cwd));
        }
        if options.diff {
            value["diff"] = serde_json::json!(diff);
        }
        return emit_json(&value, code);
    }
    if options.quiet && !options.diff && code == 0 {
        out.clear();
    }
    if let Err(error) = io::stdout()
        .write_all(&out)
        .and_then(|_| io::stdout().flush())
        .and_then(|_| io::stderr().write_all(&err))
    {
        eprintln!("Error: Failed to write result.\n{error}");
        return 1;
    }
    code
}
