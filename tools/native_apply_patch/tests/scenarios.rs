// 沿用 Codex 的命令行及场景回归，执行入口适配为当前 Cargo 产物。
use anyhow::Context;
use pretty_assertions::assert_eq;
use std::collections::BTreeMap;
use std::fs;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use tempfile::tempdir;

#[test]
fn test_apply_patch_scenarios() -> anyhow::Result<()> {
    let scenarios_marker =
        PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures/scenarios/.gitattributes");
    let scenarios_dir = scenarios_marker
        .parent()
        .context("scenario marker should have a parent directory")?;
    for scenario in fs::read_dir(scenarios_dir)
        .with_context(|| format!("failed to read {}", scenarios_dir.display()))?
    {
        let scenario = scenario?;
        let path = scenario.path();
        if path.is_dir() {
            run_apply_patch_scenario(&path)
                .with_context(|| format!("failed to run scenario {}", path.display()))?;
        }
    }
    Ok(())
}

fn run_apply_patch_scenario(dir: &Path) -> anyhow::Result<()> {
    let tmp = tempdir()?;

    let input_dir = dir.join("input");
    if input_dir.is_dir() {
        copy_dir_recursive(&input_dir, tmp.path())?;
    }

    let patch_path = dir.join("patch.txt");
    let patch = fs::read_to_string(&patch_path)
        .with_context(|| format!("failed to read {}", patch_path.display()))?;

    Command::new(env!("CARGO_BIN_EXE_apply_patch"))
        .arg(patch)
        .current_dir(tmp.path())
        .output()
        .with_context(|| format!("failed to run scenario {}", dir.display()))?;

    let expected_dir = dir.join("expected");
    let expected_snapshot = snapshot_dir(&expected_dir)?;
    let actual_snapshot = snapshot_dir(tmp.path())?;

    assert_eq!(
        actual_snapshot,
        expected_snapshot,
        "Scenario {} did not match expected final state",
        dir.display()
    );

    Ok(())
}

#[derive(Debug, Clone, PartialEq, Eq)]
enum Entry {
    File(Vec<u8>),
    Dir,
}

fn snapshot_dir(root: &Path) -> anyhow::Result<BTreeMap<PathBuf, Entry>> {
    let mut entries = BTreeMap::new();
    if root.is_dir() {
        snapshot_dir_recursive(root, root, &mut entries)?;
    }
    Ok(entries)
}

fn snapshot_dir_recursive(
    base: &Path,
    dir: &Path,
    entries: &mut BTreeMap<PathBuf, Entry>,
) -> anyhow::Result<()> {
    for entry in fs::read_dir(dir)? {
        let entry = entry?;
        let path = entry.path();
        let Some(stripped) = path.strip_prefix(base).ok() else {
            continue;
        };
        let rel = stripped.to_path_buf();

        let metadata = fs::metadata(&path)?;
        if metadata.is_dir() {
            entries.insert(rel.clone(), Entry::Dir);
            snapshot_dir_recursive(base, &path, entries)?;
        } else if metadata.is_file() {
            let contents = fs::read(&path)?;
            entries.insert(rel, Entry::File(contents));
        }
    }
    Ok(())
}

fn copy_dir_recursive(src: &Path, dst: &Path) -> anyhow::Result<()> {
    for entry in fs::read_dir(src)? {
        let entry = entry?;
        let path = entry.path();
        let dest_path = dst.join(entry.file_name());

        let metadata = fs::metadata(&path)?;
        if metadata.is_dir() {
            fs::create_dir_all(&dest_path)?;
            copy_dir_recursive(&path, &dest_path)?;
        } else if metadata.is_file() {
            if let Some(parent) = dest_path.parent() {
                fs::create_dir_all(parent)?;
            }
            fs::copy(&path, &dest_path)?;
        }
    }
    Ok(())
}
