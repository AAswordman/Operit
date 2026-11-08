use std::ffi::OsString;
use std::path::PathBuf;

#[derive(Default)]
pub(super) struct Options {
    pub check: bool,
    pub diff: bool,
    pub no_overwrite: bool,
    pub quiet: bool,
    pub json: bool,
    pub help: bool,
    pub version: bool,
    pub file: Option<PathBuf>,
    pub patch: Option<OsString>,
}

impl Options {
    pub fn parse(args: Vec<OsString>) -> Result<Self, (bool, String)> {
        // 先识别输出协议，未知参数或缺少文件路径也保持单个 JSON 错误对象。
        let mut scan = args.iter();
        let mut json = false;
        while let Some(argument) = scan.next() {
            if argument == "--" {
                break;
            }
            if argument == "--file" || argument == "-f" {
                scan.next();
                continue;
            }
            if argument == "--json" {
                json = true;
            }
        }
        let mut options = Self {
            json,
            ..Self::default()
        };
        let mut arguments = args.into_iter();
        let mut positional_only = false;
        while let Some(argument) = arguments.next() {
            if !positional_only {
                match argument.to_str() {
                    Some("--") => {
                        positional_only = true;
                        continue;
                    }
                    Some("--json") => {
                        options.json = true;
                        continue;
                    }
                    Some("--check" | "-c" | "--dry-run") => {
                        options.check = true;
                        continue;
                    }
                    Some("--diff" | "-D") => {
                        options.diff = true;
                        continue;
                    }
                    Some("--no-overwrite") => {
                        options.no_overwrite = true;
                        continue;
                    }
                    Some("--quiet" | "-q") => {
                        options.quiet = true;
                        continue;
                    }
                    Some("--help" | "-h") => {
                        options.help = true;
                        continue;
                    }
                    Some("--version" | "-v" | "-V") => {
                        options.version = true;
                        continue;
                    }
                    Some("--file" | "-f") => {
                        let path = arguments.next().ok_or_else(|| {
                            (json, "--file requires a patch file path.".to_owned())
                        })?;
                        if options.file.replace(PathBuf::from(path)).is_some() {
                            return Err((json, "--file may only be specified once.".to_owned()));
                        }
                        continue;
                    }
                    Some(value) if value.starts_with("--file=") => {
                        let path = &value[7..];
                        if path.is_empty() || options.file.replace(PathBuf::from(path)).is_some() {
                            return Err((
                                json,
                                "--file requires exactly one patch file path.".to_owned(),
                            ));
                        }
                        continue;
                    }
                    Some(value) if value.starts_with('-') => {
                        return Err((json, format!("Unknown option: {value}")));
                    }
                    _ => {}
                }
            }
            if options.patch.replace(argument).is_some() {
                return Err((json, "apply_patch accepts exactly one argument.".to_owned()));
            }
        }
        if options.file.is_some() && options.patch.is_some() {
            return Err((json, "PATCH and --file cannot be combined.".to_owned()));
        }
        Ok(options)
    }
}
