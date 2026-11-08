use assert_cmd::Command;
use serde_json::Value;
use std::fs;
use std::os::unix::fs::{MetadataExt, symlink};
use std::path::Path;
use std::process::{Output, Stdio};
use tempfile::tempdir;

const ADD: &str = "*** Begin Patch\n*** Add File: nested/new.txt\n+hello\n*** End Patch";

fn command(dir: &Path) -> Command {
    let mut result = Command::new(env!("CARGO_BIN_EXE_apply_patch"));
    result.current_dir(dir);
    result
}

fn json(output: &Output, code: i32) -> Value {
    assert_eq!(
        output.status.code(),
        Some(code),
        "{}",
        String::from_utf8_lossy(&output.stderr)
    );
    assert!(output.stderr.is_empty());
    let value: Value = serde_json::from_slice(&output.stdout).unwrap();
    assert_eq!(value["exit_code"], code);
    assert_eq!(value["success"], code == 0);
    value
}

#[test]
fn no_overwrite_rejects_existing_add_targets_in_all_modes() {
    for mode in ["", "--check", "--diff"] {
        for kind in ["file", "directory", "symlink", "dangling"] {
            let dir = tempdir().unwrap();
            let target = dir.path().join("target");
            let other = dir.path().join("other");
            fs::write(&other, "keep\n").unwrap();
            match kind {
                "file" => fs::write(&target, [0xff, 0xfe]).unwrap(),
                "directory" => fs::create_dir(&target).unwrap(),
                "symlink" => symlink(&other, &target).unwrap(),
                _ => symlink(dir.path().join("missing"), &target).unwrap(),
            }
            let before = fs::symlink_metadata(&target).unwrap();
            let mut cmd = command(dir.path());
            if !mode.is_empty() {
                cmd.arg(mode);
            }
            let output = cmd
                .args([
                    "--no-overwrite",
                    "--json",
                    "*** Begin Patch\n*** Add File: target\n+replace\n*** End Patch",
                ])
                .output()
                .unwrap();
            let result = json(&output, 1);
            assert_eq!(result["partial"], false);
            assert_eq!(result["exact"], true);
            assert!(
                result["message"]
                    .as_str()
                    .unwrap()
                    .contains("--no-overwrite")
            );
            assert_eq!(fs::symlink_metadata(&target).unwrap().ino(), before.ino());
            assert_eq!(fs::read_to_string(&other).unwrap(), "keep\n");
            assert!(!dir.path().join("missing").exists());
            if kind == "file" {
                assert_eq!(fs::read(&target).unwrap(), [0xff, 0xfe]);
            }
            if mode == "--diff" {
                assert_eq!(result["diff"], Value::Null);
            }
        }
    }
}

#[test]
fn no_overwrite_refuses_move_destination_and_preserves_source() {
    for mode in ["", "--check", "--diff"] {
        let dir = tempdir().unwrap();
        fs::write(dir.path().join("source"), "old\n").unwrap();
        fs::write(dir.path().join("destination"), "keep\n").unwrap();
        let mut cmd = command(dir.path());
        if !mode.is_empty() {
            cmd.arg(mode);
        }
        let output = cmd.args(["--no-overwrite",
            "*** Begin Patch\n*** Update File: source\n*** Move to: destination\n@@\n-old\n+new\n*** End Patch"]).output().unwrap();
        assert_eq!(output.status.code(), Some(1));
        assert!(output.stdout.is_empty());
        assert_eq!(
            fs::read_to_string(dir.path().join("source")).unwrap(),
            "old\n"
        );
        assert_eq!(
            fs::read_to_string(dir.path().join("destination")).unwrap(),
            "keep\n"
        );
    }
}

#[test]
fn no_overwrite_allows_updates_and_explicit_delete_then_add() {
    for mode in ["", "--check", "--diff"] {
        let dir = tempdir().unwrap();
        fs::write(dir.path().join("file"), "old\n").unwrap();
        let mut cmd = command(dir.path());
        if !mode.is_empty() {
            cmd.arg(mode);
        }
        cmd.args(["--no-overwrite",
            "*** Begin Patch\n*** Update File: file\n@@\n-old\n+updated\n*** Delete File: file\n*** Add File: file\n+recreated\n*** End Patch"]).assert().success();
        assert_eq!(
            fs::read_to_string(dir.path().join("file")).unwrap(),
            if mode.is_empty() {
                "recreated\n"
            } else {
                "old\n"
            }
        );
    }
}

#[test]
fn no_overwrite_observes_sequential_creations_and_partial_apply() {
    let patch =
        "*** Begin Patch\n*** Add File: first\n+one\n*** Add File: first\n+two\n*** End Patch";
    for mode in ["", "--check", "--diff"] {
        let dir = tempdir().unwrap();
        let mut cmd = command(dir.path());
        if !mode.is_empty() {
            cmd.arg(mode);
        }
        let value = json(
            &cmd.args(["--json", "--no-overwrite", patch])
                .output()
                .unwrap(),
            1,
        );
        assert_eq!(value["changes"].as_array().unwrap().len(), 1);
        assert_eq!(value["partial"], mode.is_empty());
        if mode.is_empty() {
            assert_eq!(
                fs::read_to_string(dir.path().join("first")).unwrap(),
                "one\n"
            );
        } else {
            assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 0);
        }
    }
}

#[test]
fn concurrent_no_overwrite_creations_have_one_winner() {
    let dir = tempdir().unwrap();
    let mut children = Vec::new();
    for index in 0..8 {
        let child = std::process::Command::new(env!("CARGO_BIN_EXE_apply_patch"))
            .current_dir(dir.path())
            .args(["--no-overwrite", "--quiet"])
            .arg(format!(
                "*** Begin Patch\n*** Add File: shared\n+writer-{index}\n*** End Patch"
            ))
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()
            .unwrap();
        children.push((index, child));
    }
    let mut winners = Vec::new();
    for (index, child) in children {
        let output = child.wait_with_output().unwrap();
        assert!(output.stdout.is_empty());
        if output.status.success() {
            winners.push(index);
        } else {
            assert_eq!(output.status.code(), Some(1));
        }
    }
    assert_eq!(winners.len(), 1);
    assert_eq!(
        fs::read_to_string(dir.path().join("shared")).unwrap(),
        format!("writer-{}\n", winners[0])
    );
}

#[test]
fn diff_previews_all_operations_without_writes() {
    let dir = tempdir().unwrap();
    fs::write(dir.path().join("old"), "old\n").unwrap();
    fs::write(dir.path().join("obsolete"), "obsolete\n").unwrap();
    let inode = fs::metadata(dir.path().join("old")).unwrap().ino();
    let patch = "*** Begin Patch\n*** Add File: nested/new\n+created\n*** Update File: old\n*** Move to: moved\n@@\n-old\n+changed\n*** Delete File: obsolete\n*** End Patch";
    for flag in ["--diff", "-D"] {
        let output = command(dir.path()).args([flag, patch]).output().unwrap();
        assert!(
            output.status.success(),
            "{}",
            String::from_utf8_lossy(&output.stderr)
        );
        let text = String::from_utf8(output.stdout).unwrap();
        for expected in [
            "--- /dev/null",
            "+++ b/nested/new",
            "+created",
            "+++ b/moved",
            "+changed",
            "--- a/old",
            "-old",
            "-obsolete",
        ] {
            assert!(text.contains(expected), "{expected}: {text}");
        }
        assert!(!text.contains("Success. Updated"));
        assert!(!dir.path().join("nested").exists());
        assert!(!dir.path().join("moved").exists());
        assert_eq!(fs::metadata(dir.path().join("old")).unwrap().ino(), inode);
        assert_eq!(fs::read_to_string(dir.path().join("old")).unwrap(), "old\n");
        assert_eq!(
            fs::read_to_string(dir.path().join("obsolete")).unwrap(),
            "obsolete\n"
        );
    }
}

#[test]
fn diff_preserves_execution_order_for_new_then_updated_files() {
    let dir = tempdir().unwrap();
    let patch = "*** Begin Patch\n*** Add File: file\n+first\n*** Update File: file\n@@\n-first\n+second\n*** End Patch";
    let output = command(dir.path())
        .args(["--diff", patch])
        .output()
        .unwrap();
    assert!(output.status.success());
    let text = String::from_utf8(output.stdout).unwrap();
    assert!(text.find("+first").unwrap() < text.find("-first").unwrap());
    assert!(text.contains("+second"));
    assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 0);
}

#[test]
fn diff_json_combines_check_policy_and_quiet_without_ansi() {
    let dir = tempdir().unwrap();
    let output = command(dir.path())
        .args([
            "--diff",
            "--check",
            "--no-overwrite",
            "--quiet",
            "--json",
            ADD,
        ])
        .output()
        .unwrap();
    let value = json(&output, 0);
    assert_eq!(value["mode"], "diff");
    assert_eq!(value["partial"], false);
    let diff = value["diff"].as_str().unwrap();
    assert!(diff.contains("+hello"));
    assert!(!diff.contains('\u{1b}'));
    assert!(
        value["message"]
            .as_str()
            .unwrap()
            .starts_with("Success. Previewed")
    );
    assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 0);
}

#[test]
fn failed_diff_has_no_partial_stdout_and_keeps_original_files() {
    let dir = tempdir().unwrap();
    fs::write(dir.path().join("old"), "old\r\n").unwrap();
    let patch = "*** Begin Patch\n*** Update File: old\n@@\n-old\n+changed\n*** Delete File: missing\n*** End Patch";
    let output = command(dir.path())
        .args(["--diff", patch])
        .output()
        .unwrap();
    assert_eq!(output.status.code(), Some(1));
    assert!(output.stdout.is_empty());
    assert!(!output.stderr.is_empty());
    let value = json(
        &command(dir.path())
            .args(["--diff", "--json", patch])
            .output()
            .unwrap(),
        1,
    );
    assert_eq!(value["diff"], Value::Null);
    assert_eq!(value["partial"], false);
    assert_eq!(fs::read(dir.path().join("old")).unwrap(), b"old\r\n");
    let value = json(
        &command(dir.path())
            .args(["--diff", "--json", "-f", "missing.patch"])
            .output()
            .unwrap(),
        1,
    );
    assert_eq!(value["diff"], Value::Null);
}

#[test]
fn diff_reports_binary_empty_files_and_symbolic_link_deletion() {
    let dir = tempdir().unwrap();
    fs::write(dir.path().join("binary"), [0xff, 0xfe]).unwrap();
    fs::write(dir.path().join("empty"), "").unwrap();
    fs::write(dir.path().join("target"), "target\n").unwrap();
    symlink("target", dir.path().join("link")).unwrap();
    let patch = "*** Begin Patch\n*** Delete File: binary\n*** Delete File: empty\n*** Add File: new-empty\n*** Delete File: link\n*** End Patch";
    let output = command(dir.path())
        .args(["--diff", patch])
        .output()
        .unwrap();
    assert!(
        output.status.success(),
        "{}",
        String::from_utf8_lossy(&output.stderr)
    );
    let text = String::from_utf8(output.stdout).unwrap();
    for expected in [
        "Binary files a/binary and /dev/null differ",
        "--- a/empty",
        "+++ b/new-empty",
        "deleted symbolic link a/link -> target",
    ] {
        assert!(text.contains(expected), "{text}");
    }
    assert!(!text.contains("-target"));
    assert_eq!(fs::read(dir.path().join("binary")).unwrap(), [0xff, 0xfe]);
    assert!(dir.path().join("empty").exists());
    assert!(
        fs::symlink_metadata(dir.path().join("link"))
            .unwrap()
            .is_symlink()
    );
    assert!(!dir.path().join("new-empty").exists());
}

#[test]
fn diff_quotes_paths_and_disables_color_when_redirected() {
    let dir = tempdir().unwrap();
    let normalized = dir.path().join("含 空格file");
    let literal = dir.path().join("含 空格\tfile");
    // 去掉制表符后是另一份文件，预览和应用都不能误操作它。
    fs::write(&normalized, "keep\n").unwrap();
    let patch = "*** Begin Patch\n*** Add File: 含 空格\tfile\n+text\n*** End Patch";
    let output = command(dir.path())
        .env("TERM", "xterm-256color")
        .args(["--diff", patch])
        .output()
        .unwrap();
    assert!(output.status.success());
    let text = String::from_utf8(output.stdout).unwrap();
    assert!(text.contains("+++ \"b/含 空格\\tfile\""), "{text}");
    assert!(!text.contains('\u{1b}'));
    assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 1);
    assert!(!literal.exists());
    command(dir.path())
        .args(["--check", "--no-overwrite", patch])
        .assert()
        .success();
    assert!(!literal.exists());
    command(dir.path())
        .args(["--no-overwrite", patch])
        .assert()
        .success();
    assert_eq!(fs::read_to_string(&literal).unwrap(), "text\n");
    assert_eq!(fs::read_to_string(&normalized).unwrap(), "keep\n");
}

#[test]
fn quiet_hides_summaries_but_keeps_errors_and_requested_output() {
    for flag in ["--quiet", "-q"] {
        let dir = tempdir().unwrap();
        for mode in ["--check", ""] {
            let mut cmd = command(dir.path());
            if !mode.is_empty() {
                cmd.arg(mode);
            }
            let output = cmd.args([flag, ADD]).output().unwrap();
            assert!(output.status.success());
            assert!(output.stdout.is_empty());
            assert!(output.stderr.is_empty());
        }
        let output = command(dir.path())
            .args([flag, "--no-overwrite", ADD])
            .output()
            .unwrap();
        assert_eq!(output.status.code(), Some(1));
        assert!(output.stdout.is_empty());
        assert!(!output.stderr.is_empty());
        for requested in ["--help", "--version", "--diff"] {
            let mut cmd = command(dir.path());
            cmd.args([flag, requested]);
            if requested == "--diff" {
                cmd.arg(ADD.replace("nested/new.txt", "preview.txt"));
            }
            let output = cmd.output().unwrap();
            assert!(output.status.success());
            assert!(!output.stdout.is_empty());
        }
        json(
            &command(dir.path())
                .args([flag, "--json", ADD])
                .output()
                .unwrap(),
            0,
        );
        command(dir.path())
            .args([flag, "--unknown"])
            .assert()
            .code(2);
    }
}

#[test]
fn help_documents_custom_rules_and_strict_is_not_an_alias() {
    let dir = tempdir().unwrap();
    let output = command(dir.path()).arg("--help").output().unwrap();
    assert!(output.status.success());
    let text = String::from_utf8(output.stdout).unwrap();
    for expected in [
        "--no-overwrite",
        "--diff",
        "--quiet",
        "Operit rules",
        "dangling links",
        "Update File may still",
        "Delete then Add",
        "exclusive",
        "not a partial diff",
        "NO_COLOR",
        "JSON, help/version",
    ] {
        assert!(text.contains(expected), "{expected}");
    }
    command(dir.path()).arg("--strict").assert().code(2);
}

#[test]
fn new_option_names_are_valid_file_arguments() {
    let dir = tempdir().unwrap();
    for name in ["--no-overwrite", "--diff", "--quiet"] {
        fs::write(dir.path().join(name), ADD).unwrap();
        command(dir.path())
            .args(["--file", name])
            .assert()
            .success();
    }
}

#[test]
fn diff_unchanged_update_outputs_nothing() {
    let dir = tempdir().unwrap();
    fs::write(dir.path().join("same"), "same\n").unwrap();
    let output = command(dir.path())
        .args([
            "--diff",
            "*** Begin Patch\n*** Update File: same\n@@\n-same\n+same\n*** End Patch",
        ])
        .output()
        .unwrap();
    assert!(output.status.success());
    assert!(output.stdout.is_empty());
}
