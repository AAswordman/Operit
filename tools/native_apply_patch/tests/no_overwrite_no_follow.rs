use codex_apply_patch::{ApplyPatchOptions, apply_patch_with_options};
use codex_exec_server::LOCAL_FS;
use codex_utils_path_uri::PathUri;
use std::fs;
use std::os::unix::fs::symlink;

#[tokio::test]
async fn no_overwrite_and_no_follow_keep_exclusive_creation_and_link_rules() -> anyhow::Result<()> {
    let dir = tempfile::tempdir()?;
    let root = dir.path().canonicalize()?;
    let cwd = PathUri::from_host_native_path(&root)?;
    let options = ApplyPatchOptions {
        follow_symlinks: false,
        no_overwrite: true,
    };
    let patch = "*** Begin Patch\n*** Add File: new/file\n+first\n*** End Patch";
    apply_patch_with_options(
        patch,
        options,
        &cwd,
        &mut Vec::new(),
        &mut Vec::new(),
        LOCAL_FS.as_ref(),
        None,
    )
    .await?;
    let failure = apply_patch_with_options(
        patch,
        options,
        &cwd,
        &mut Vec::new(),
        &mut Vec::new(),
        LOCAL_FS.as_ref(),
        None,
    )
    .await
    .unwrap_err();
    assert!(failure.delta().is_empty());
    assert!(failure.delta().is_exact());
    assert_eq!(fs::read_to_string(root.join("new/file"))?, "first\n");

    symlink("missing", root.join("link"))?;
    symlink("new", root.join("linked"))?;
    for path in ["link", "linked/another"] {
        let patch = format!("*** Begin Patch\n*** Add File: {path}\n+changed\n*** End Patch");
        assert!(
            apply_patch_with_options(
                &patch,
                options,
                &cwd,
                &mut Vec::new(),
                &mut Vec::new(),
                LOCAL_FS.as_ref(),
                None,
            )
            .await
            .is_err()
        );
    }
    assert!(fs::symlink_metadata(root.join("link"))?.is_symlink());
    assert!(!root.join("missing").exists());
    assert!(!root.join("new/another").exists());
    Ok(())
}
