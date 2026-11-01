use assert_cmd::Command;
use serde_json::Value;
use std::collections::BTreeMap;
use std::fs;
use std::os::unix::fs::{MetadataExt, symlink};
use std::path::{Path, PathBuf};
use std::process::Output;
use tempfile::tempdir;

const ADD: &str = "*** Begin Patch\n*** Add File: nested/new.txt\n+hello\n*** End Patch";

fn command(dir: &Path) -> Command {
    let mut command = Command::new(env!("CARGO_BIN_EXE_apply_patch"));
    command.current_dir(dir);
    command
}

fn json_result(output: &Output, code: i32) -> Value {
    assert_eq!(
        output.status.code(),
        Some(code),
        "{}",
        String::from_utf8_lossy(&output.stderr)
    );
    assert!(
        output.stderr.is_empty(),
        "{}",
        String::from_utf8_lossy(&output.stderr)
    );
    let value: Value = serde_json::from_slice(&output.stdout).expect("必须是单个完整 JSON 对象");
    assert_eq!(value["schema_version"], 1);
    assert_eq!(value["exit_code"], code);
    assert_eq!(value["success"], code == 0);
    value
}

#[test]
fn version_and_help_do_not_read_input_or_modify_files() {
    let dir = tempdir().unwrap();
    for flag in ["-v", "-V", "--version"] {
        command(dir.path())
            .arg(flag)
            .assert()
            .success()
            .stdout(format!("apply_patch {}\n", env!("CARGO_PKG_VERSION")));
    }
    for flag in ["-h", "--help"] {
        let output = command(dir.path()).arg(flag).output().unwrap();
        assert!(output.status.success());
        let text = String::from_utf8(output.stdout).unwrap();
        for expected in [
            "--file",
            "--check",
            "--json",
            "--no-overwrite",
            "--diff",
            "--quiet",
            "Operit rules",
            "*** Begin Patch",
            "Exit codes",
        ] {
            assert!(text.contains(expected));
        }
    }
    assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 0);
}

#[test]
fn help_and_version_support_json_output() {
    let dir = tempdir().unwrap();
    for (flag, mode) in [("--help", "help"), ("--version", "version")] {
        let value = json_result(
            &command(dir.path()).args(["--json", flag]).output().unwrap(),
            0,
        );
        assert_eq!(value["mode"], mode);
        assert_eq!(value["version"], env!("CARGO_PKG_VERSION"));
        assert_eq!(value["error"], Value::Null);
    }
}

#[test]
fn file_input_supports_short_long_equal_and_non_utf8_paths() {
    let dir = tempdir().unwrap();
    let patch = dir.path().join("含 空格'$的补丁.txt");
    fs::write(&patch, ADD).unwrap();
    for flag in ["-f", "--file"] {
        command(dir.path()).arg(flag).arg(&patch).assert().success();
        assert_eq!(
            fs::read_to_string(dir.path().join("nested/new.txt")).unwrap(),
            "hello\n"
        );
    }
    command(dir.path())
        .arg(format!("--file={}", patch.display()))
        .assert()
        .success();
    use std::os::unix::ffi::OsStringExt;
    let raw_path = dir
        .path()
        .join(std::ffi::OsString::from_vec(b"patch-\xff.txt".to_vec()));
    fs::write(&raw_path, ADD).unwrap();
    command(dir.path())
        .arg("-f")
        .arg(raw_path)
        .assert()
        .success();
}

#[test]
fn stdin_and_end_of_options_preserve_original_patch_input() {
    let dir = tempdir().unwrap();
    command(dir.path())
        .args(["-f", "-"])
        .write_stdin(ADD)
        .assert()
        .success();
    command(dir.path()).args(["--", ADD]).assert().success();
    assert_eq!(
        fs::read_to_string(dir.path().join("nested/new.txt")).unwrap(),
        "hello\n"
    );
}

#[test]
fn usage_and_input_failures_are_single_json_objects() {
    let dir = tempdir().unwrap();
    for args in [
        vec!["--unknown", "--json"],
        vec!["--json", "--file"],
        vec!["--json", "one", "two"],
        vec!["--json", "-f", "file.patch", ADD],
        vec!["--json", "--file="],
    ] {
        let value = json_result(&command(dir.path()).args(args).output().unwrap(), 2);
        assert_eq!(value["error"]["kind"], "usage");
    }
    let value = json_result(
        &command(dir.path())
            .args(["--json", "-f", "missing.patch"])
            .output()
            .unwrap(),
        1,
    );
    assert_eq!(value["error"]["kind"], "input");
    let value = json_result(
        &command(dir.path())
            .arg("--json")
            .write_stdin("")
            .output()
            .unwrap(),
        2,
    );
    assert_eq!(value["error"]["kind"], "usage");
    fs::write(dir.path().join("invalid.patch"), [0xff, 0xfe]).unwrap();
    let value = json_result(
        &command(dir.path())
            .args(["--json", "-f", "invalid.patch"])
            .output()
            .unwrap(),
        1,
    );
    assert_eq!(value["error"]["kind"], "input");
}

#[test]
fn file_name_equal_to_json_option_is_not_an_output_flag() {
    let dir = tempdir().unwrap();
    fs::write(dir.path().join("--json"), ADD).unwrap();
    command(dir.path())
        .args(["-f", "--json"])
        .assert()
        .success()
        .stdout("Success. Updated the following files:\nA nested/new.txt\n");
}

#[test]
fn check_aliases_never_create_directories_or_files() {
    let dir = tempdir().unwrap();
    for flag in ["-c", "--check", "--dry-run"] {
        let output = command(dir.path()).args([flag, ADD]).output().unwrap();
        assert!(
            output.status.success(),
            "{}",
            String::from_utf8_lossy(&output.stderr)
        );
        assert!(String::from_utf8_lossy(&output.stdout).contains("no files written"));
        assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 0);
    }
}

#[test]
fn check_simulates_sequential_add_update_move_and_delete() {
    let dir = tempdir().unwrap();
    let patch = "*** Begin Patch\n*** Add File: a/x.txt\n+first\n*** Update File: a/x.txt\n*** Move to: b/y.txt\n@@\n-first\n+second\n*** Update File: b/y.txt\n@@\n-second\n+third\n*** Delete File: b/y.txt\n*** End Patch";
    let value = json_result(
        &command(dir.path())
            .args(["--check", "--json", patch])
            .output()
            .unwrap(),
        0,
    );
    assert_eq!(value["mode"], "check");
    let operations: Vec<_> = value["changes"]
        .as_array()
        .unwrap()
        .iter()
        .map(|change| change["operation"].as_str().unwrap())
        .collect();
    assert_eq!(operations, ["add", "move", "update", "delete"]);
    assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 0);
}

#[test]
fn failed_check_leaves_all_files_unchanged_even_after_valid_prefix() {
    let dir = tempdir().unwrap();
    let target = dir.path().join("old.txt");
    fs::write(&target, "original\r\n").unwrap();
    let inode = fs::metadata(&target).unwrap().ino();
    let patch = "*** Begin Patch\n*** Update File: old.txt\n@@\n-original\n+changed\n*** Add File: created.txt\n+created\n*** Delete File: missing.txt\n*** End Patch";
    let value = json_result(
        &command(dir.path())
            .args(["--json", "--check", patch])
            .output()
            .unwrap(),
        1,
    );
    assert_eq!(value["partial"], false);
    assert_eq!(value["changes"].as_array().unwrap().len(), 2);
    assert_eq!(fs::read(&target).unwrap(), b"original\r\n");
    assert_eq!(fs::metadata(&target).unwrap().ino(), inode);
    assert!(!dir.path().join("created.txt").exists());
}

#[test]
fn json_apply_preserves_partial_failure_and_reports_moves() {
    let dir = tempdir().unwrap();
    let patch = "*** Begin Patch\n*** Add File: a.txt\n+original\n*** Update File: a.txt\n*** Move to: b.txt\n@@\n-original\n+changed\n*** Delete File: missing.txt\n*** End Patch";
    let value = json_result(
        &command(dir.path())
            .args(["--json", patch])
            .output()
            .unwrap(),
        1,
    );
    assert_eq!(value["mode"], "apply");
    assert_eq!(value["partial"], true);
    assert_eq!(value["changes"][1]["operation"], "move");
    assert!(
        value["changes"][1]["destination"]
            .as_str()
            .unwrap()
            .ends_with("b.txt")
    );
    assert_eq!(
        fs::read_to_string(dir.path().join("b.txt")).unwrap(),
        "changed\n"
    );
    assert!(!dir.path().join("a.txt").exists());
}

#[test]
fn check_follows_leaf_and_directory_links_without_modifying_targets() {
    let dir = tempdir().unwrap();
    let outside = tempdir().unwrap();
    fs::write(outside.path().join("target.txt"), "original\n").unwrap();
    symlink(
        outside.path().join("target.txt"),
        dir.path().join("leaf.txt"),
    )
    .unwrap();
    symlink(outside.path(), dir.path().join("linked")).unwrap();
    let patch = "*** Begin Patch\n*** Update File: leaf.txt\n@@\n-original\n+changed\n*** Update File: linked/target.txt\n@@\n-changed\n+final\n*** Delete File: leaf.txt\n*** Add File: leaf.txt\n+independent\n*** Update File: linked/target.txt\n@@\n-final\n+done\n*** End Patch";
    command(dir.path())
        .args(["--check", patch])
        .assert()
        .success();
    assert!(
        fs::symlink_metadata(dir.path().join("leaf.txt"))
            .unwrap()
            .is_symlink()
    );
    assert_eq!(
        fs::read_to_string(outside.path().join("target.txt")).unwrap(),
        "original\n"
    );
}

#[test]
fn check_hard_link_aliases_share_changes_but_recreated_names_do_not() {
    let dir = tempdir().unwrap();
    fs::write(dir.path().join("a.txt"), "original\n").unwrap();
    fs::hard_link(dir.path().join("a.txt"), dir.path().join("b.txt")).unwrap();
    let patch = "*** Begin Patch\n*** Update File: a.txt\n@@\n-original\n+first\n*** Update File: b.txt\n@@\n-first\n+second\n*** Update File: a.txt\n@@\n-second\n+third\n*** Delete File: a.txt\n*** Add File: a.txt\n+independent\n*** Update File: b.txt\n@@\n-third\n+fourth\n*** End Patch";
    command(dir.path()).args(["-c", patch]).assert().success();
    assert_eq!(
        fs::read_to_string(dir.path().join("a.txt")).unwrap(),
        "original\n"
    );
    assert_eq!(
        fs::read_to_string(dir.path().join("b.txt")).unwrap(),
        "original\n"
    );
}

#[test]
fn check_rejects_a_file_used_as_a_parent_without_touching_it() {
    let dir = tempdir().unwrap();
    fs::write(dir.path().join("parent"), "keep").unwrap();
    command(dir.path())
        .args([
            "--check",
            "*** Begin Patch\n*** Add File: parent/child\n+text\n*** End Patch",
        ])
        .assert()
        .code(1);
    assert_eq!(
        fs::read_to_string(dir.path().join("parent")).unwrap(),
        "keep"
    );
}

#[test]
fn successful_json_reports_binary_deletions_even_without_text_snapshots() {
    let dir = tempdir().unwrap();
    fs::write(dir.path().join("binary.dat"), [0xff, 0xfe]).unwrap();
    let patch = "*** Begin Patch
*** Delete File: binary.dat
*** End Patch";
    let checked = json_result(
        &command(dir.path())
            .args(["--json", "--check", patch])
            .output()
            .unwrap(),
        0,
    );
    assert_eq!(checked["changes"][0]["operation"], "delete");
    assert!(dir.path().join("binary.dat").exists());
    let applied = json_result(
        &command(dir.path())
            .args(["--json", patch])
            .output()
            .unwrap(),
        0,
    );
    assert_eq!(applied["changes"][0]["operation"], "delete");
    assert!(!dir.path().join("binary.dat").exists());
}

fn copy_tree(source: &Path, target: &Path) {
    if !source.is_dir() {
        return;
    }
    fs::create_dir_all(target).unwrap();
    for entry in fs::read_dir(source).unwrap() {
        let entry = entry.unwrap();
        let to = target.join(entry.file_name());
        if entry.path().is_dir() {
            copy_tree(&entry.path(), &to);
        } else {
            fs::copy(entry.path(), to).unwrap();
        }
    }
}

fn snapshot(root: &Path) -> BTreeMap<PathBuf, Option<Vec<u8>>> {
    fn visit(root: &Path, dir: &Path, entries: &mut BTreeMap<PathBuf, Option<Vec<u8>>>) {
        for entry in fs::read_dir(dir).unwrap() {
            let path = entry.unwrap().path();
            let relative = path.strip_prefix(root).unwrap().to_owned();
            if path.is_dir() {
                entries.insert(relative, None);
                visit(root, &path, entries);
            } else {
                entries.insert(relative, Some(fs::read(&path).unwrap()));
            }
        }
    }
    let mut result = BTreeMap::new();
    visit(root, root, &mut result);
    result
}

#[test]
fn all_original_scenarios_have_matching_check_status_and_unchanged_inputs() {
    let scenarios = Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures/scenarios");
    for entry in fs::read_dir(scenarios).unwrap() {
        let scenario = entry.unwrap().path();
        if !scenario.is_dir() {
            continue;
        }
        let check_dir = tempdir().unwrap();
        let apply_dir = tempdir().unwrap();
        copy_tree(&scenario.join("input"), check_dir.path());
        copy_tree(&scenario.join("input"), apply_dir.path());
        let before = snapshot(check_dir.path());
        let patch = fs::read_to_string(scenario.join("patch.txt")).unwrap();
        let checked = command(check_dir.path())
            .args(["--check", &patch])
            .output()
            .unwrap();
        let previewed = command(check_dir.path())
            .args(["--diff", &patch])
            .output()
            .unwrap();
        assert_eq!(
            previewed.status.code(),
            checked.status.code(),
            "{}",
            scenario.display()
        );
        assert_eq!(snapshot(check_dir.path()), before, "{}", scenario.display());
        let applied = command(apply_dir.path()).arg(&patch).output().unwrap();
        assert_eq!(
            checked.status.code(),
            applied.status.code(),
            "{}: {}",
            scenario.display(),
            String::from_utf8_lossy(&checked.stderr)
        );
        assert_eq!(snapshot(check_dir.path()), before, "{}", scenario.display());
    }
}
