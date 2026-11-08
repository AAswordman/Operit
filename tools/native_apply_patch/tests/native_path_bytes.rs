use codex_utils_path_uri::PathUri;
use std::ffi::OsString;
use std::os::unix::ffi::OsStringExt;

#[test]
fn native_path_joins_preserve_url_ignored_bytes() {
    let dir = tempfile::tempdir().unwrap();
    let cwd = PathUri::from_host_native_path(dir.path()).unwrap();
    for name in [
        "含 空格\tfile",
        "line\nfile",
        "line\rfile",
        "literal%09\tfile",
    ] {
        let expected = dir.path().join(name);
        for input in [name, expected.to_str().unwrap()] {
            for actual in [
                cwd.join(input).unwrap(),
                cwd.join_native_bytes(input.as_bytes()).unwrap(),
            ] {
                assert_eq!(actual.to_abs_path().unwrap().as_path(), expected.as_path());
            }
        }
    }

    // 含制表符的非 UTF-8 文件名也应保留原始字节，不把百分号当成已有转义。
    let bytes = b"raw-\xff\t%09";
    let expected = dir.path().join(OsString::from_vec(bytes.to_vec()));
    let actual = cwd.join_native_bytes(bytes).unwrap();
    assert_eq!(actual.to_abs_path().unwrap().as_path(), expected.as_path());
    assert!(cwd.join("invalid\t\0file").is_err());
    assert!(cwd.join_native_bytes(b"invalid\t\0file").is_err());
}
