// 派生自 OpenAI Codex 路径工具；来源见 UPSTREAM.json。
use crate::PathConvention;
use crate::PathUri;
use crate::PathUriParseError;
use crate::is_windows_separator_byte;
use codex_utils_absolute_path::AbsolutePathBuf;
use schemars::JsonSchema;
use serde::Deserialize;
use serde::Serialize;
use serde::Serializer;
use std::fmt;
use std::path::Path;
use thiserror::Error;
use ts_rs::TS;

#[derive(Clone, Debug, PartialEq, Eq, Hash, Deserialize, TS)]
#[serde(transparent)]
#[ts(type = "string")]
pub struct LegacyAppPathString(String);

impl LegacyAppPathString {
    pub fn from_string(path: impl Into<String>) -> Self {
        Self(path.into())
    }

    pub fn from_path(path: &Path) -> Self {
        Self(path.to_string_lossy().into_owned())
    }

    pub fn from_abs_path(path: &AbsolutePathBuf) -> Self {
        Self::from_path(path.as_path())
    }

    pub fn from_path_uri(
        path: &PathUri,
        convention: PathConvention,
    ) -> Result<Self, LegacyAppPathStringError> {
        if let Some(path_bytes) = path.opaque_fallback_bytes() {
            return render_opaque_fallback(path, &path_bytes, convention).map(Self);
        }
        match convention {
            PathConvention::Posix => render_posix_path(path),
            PathConvention::Windows => render_windows_path(path),
        }
        .map(Self)
    }

    pub fn to_path_uri(
        &self,
        convention: PathConvention,
    ) -> Result<PathUri, LegacyAppPathStringError> {
        PathUri::from_absolute_native_path(&self.0, convention).ok_or_else(|| {
            LegacyAppPathStringError::InvalidNativePath {
                path: self.0.clone(),
                convention: Some(convention),
            }
        })
    }

    pub fn resolve_against(
        &self,
        cwd: &PathUri,
        user_home_dir: Option<&PathUri>,
    ) -> Result<PathUri, LegacyAppPathStringError> {
        let convention = cwd.infer_path_convention().ok_or_else(|| {
            LegacyAppPathStringError::MissingBaseConvention {
                cwd: cwd.to_string(),
            }
        })?;
        let is_windows = convention == PathConvention::Windows;
        let path = self.as_str();
        let home_relative = convention.home_relative_suffix(path);
        if let Some(suffix) = home_relative {
            let home =
                user_home_dir.ok_or_else(|| LegacyAppPathStringError::MissingHomeDirectory {
                    path: path.to_string(),
                })?;
            return Ok(home.join(suffix.trim_start_matches(|separator| {
                separator == '/' || is_windows && separator == '\\'
            }))?);
        }

        if is_windows && (path.starts_with("//") || path.starts_with(r"\\")) {
            return self.to_path_uri(PathConvention::Windows);
        }

        match self.infer_absolute_path_convention() {
            Some(path_convention) if path_convention == convention => self.to_path_uri(convention),
            Some(PathConvention::Posix) if is_windows => Ok(cwd.join(path)?),
            Some(path_convention) => Err(LegacyAppPathStringError::MismatchedConvention {
                path: path.to_string(),
                path_convention,
                cwd: cwd.to_string(),
                convention,
            }),
            None => Ok(cwd.join(path)?),
        }
    }

    pub fn to_inferred_path_uri(&self) -> Option<PathUri> {
        PathUri::try_from(self.clone()).ok()
    }

    pub fn render_for_ui(&self) -> String {
        self.to_inferred_path_uri()
            .map(|path| path.inferred_native_path_string())
            .unwrap_or_else(|| self.0.clone())
    }

    pub fn to_inferred_abs_path(&self) -> Option<AbsolutePathBuf> {
        AbsolutePathBuf::try_from(self.clone()).ok()
    }

    pub fn infer_absolute_path_convention(&self) -> Option<PathConvention> {
        let bytes = self.0.as_bytes();
        let has_windows_drive_root = matches!(
            bytes,
            [drive, b':', separator, ..]
                if drive.is_ascii_alphabetic() && is_windows_separator_byte(*separator)
        );
        let has_windows_unc_root = matches!(
            bytes,
            [first, second, ..]
                if is_windows_separator_byte(*first) && is_windows_separator_byte(*second)
        );
        if has_windows_drive_root || has_windows_unc_root {
            Some(PathConvention::Windows)
        } else if self.0.starts_with('/') {
            Some(PathConvention::Posix)
        } else {
            None
        }
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }

    pub fn into_string(self) -> String {
        self.0
    }
}

impl From<AbsolutePathBuf> for LegacyAppPathString {
    fn from(path: AbsolutePathBuf) -> Self {
        Self::from_abs_path(&path)
    }
}

impl From<PathUri> for LegacyAppPathString {
    fn from(path: PathUri) -> Self {
        Self(path.inferred_native_path_string())
    }
}

impl TryFrom<LegacyAppPathString> for PathUri {
    type Error = LegacyAppPathStringError;

    fn try_from(path: LegacyAppPathString) -> Result<Self, Self::Error> {
        let Some(convention) = path.infer_absolute_path_convention() else {
            return Err(LegacyAppPathStringError::InvalidNativePath {
                path: path.0,
                convention: None,
            });
        };
        PathUri::from_absolute_native_path(path.as_str(), convention).ok_or({
            LegacyAppPathStringError::InvalidNativePath {
                path: path.0,
                convention: Some(convention),
            }
        })
    }
}

impl TryFrom<LegacyAppPathString> for AbsolutePathBuf {
    type Error = LegacyAppPathStringError;

    fn try_from(path: LegacyAppPathString) -> Result<Self, Self::Error> {
        AbsolutePathBuf::from_absolute_path_checked(path.as_str()).map_err(|_| {
            LegacyAppPathStringError::InvalidNativePath {
                path: path.0,
                convention: None,
            }
        })
    }
}

fn render_opaque_fallback(
    path: &PathUri,
    path_bytes: &[u8],
    convention: PathConvention,
) -> Result<String, LegacyAppPathStringError> {
    let rendered = match convention {
        PathConvention::Posix if path_bytes.starts_with(b"/") => {
            Some(String::from_utf8_lossy(path_bytes).into_owned())
        }
        PathConvention::Windows => render_windows_opaque_fallback(path_bytes),
        PathConvention::Posix => None,
    };
    rendered.ok_or_else(|| LegacyAppPathStringError::OpaqueFallback {
        path: path.to_string(),
    })
}

fn render_windows_opaque_fallback(path_bytes: &[u8]) -> Option<String> {
    if !path_bytes.len().is_multiple_of(2) {
        return None;
    }
    let path_wide = path_bytes
        .chunks_exact(2)
        .map(|bytes| u16::from_le_bytes([bytes[0], bytes[1]]))
        .collect::<Vec<_>>();

    let has_drive_root = matches!(
        path_wide.as_slice(),
        [drive, colon, separator, ..]
            if ((u16::from(b'A')..=u16::from(b'Z')).contains(drive)
                || (u16::from(b'a')..=u16::from(b'z')).contains(drive))
                && *colon == u16::from(b':')
                && is_windows_separator(*separator)
    );
    let has_namespace_or_unc_root = matches!(
        path_wide.as_slice(),
        [first, second, ..]
            if is_windows_separator(*first) && is_windows_separator(*second)
    );
    (has_drive_root || has_namespace_or_unc_root).then(|| String::from_utf16_lossy(&path_wide))
}

fn is_windows_separator(character: u16) -> bool {
    character == u16::from(b'\\') || character == u16::from(b'/')
}

impl fmt::Display for LegacyAppPathString {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl Serialize for LegacyAppPathString {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        serializer.serialize_str(&self.0)
    }
}

impl JsonSchema for LegacyAppPathString {
    fn schema_name() -> String {
        "LegacyAppPathString".to_string()
    }

    fn json_schema(generator: &mut schemars::r#gen::SchemaGenerator) -> schemars::schema::Schema {
        String::json_schema(generator)
    }
}

fn render_posix_path(path: &PathUri) -> Result<String, LegacyAppPathStringError> {
    let url = path.to_url();

    if url.host_str().is_some() {
        return Err(incompatible_convention(path, PathConvention::Posix));
    }

    let mut rendered = String::new();
    for segment in path_segments(&url) {
        rendered.push('/');
        rendered.push_str(&decode_native_segment(segment));
    }
    Ok(rendered)
}

fn render_windows_path(path: &PathUri) -> Result<String, LegacyAppPathStringError> {
    let url = path.to_url();
    let mut segments = path_segments(&url);
    let mut rendered = String::new();
    if let Some(host) = url.host_str() {
        let Some(share) = segments.next() else {
            return Err(incompatible_convention(path, PathConvention::Windows));
        };
        let share = decode_native_segment(share);
        if share.is_empty() {
            return Err(incompatible_convention(path, PathConvention::Windows));
        }
        rendered.push_str(r"\\");
        rendered.push_str(host);
        rendered.push('\\');
        rendered.push_str(&share);
    } else {
        let Some(drive) = segments.next() else {
            return Err(incompatible_convention(path, PathConvention::Windows));
        };
        let drive = decode_native_segment(drive);
        let bytes = drive.as_bytes();
        if bytes.len() != 2 || !bytes[0].is_ascii_alphabetic() || bytes[1] != b':' {
            return Err(incompatible_convention(path, PathConvention::Windows));
        }
        rendered.push_str(&drive);
    }

    for segment in segments {
        let segment = decode_native_segment(segment);
        rendered.push('\\');
        rendered.push_str(&segment);
    }

    if rendered.len() == 2 && rendered.as_bytes()[1] == b':' {
        rendered.push('\\');
    }
    Ok(rendered)
}

fn path_segments(url: &url::Url) -> std::str::Split<'_, char> {
    url.path_segments()
        .unwrap_or_else(|| unreachable!("validated file URLs have path segments"))
}

fn decode_native_segment(segment: &str) -> String {
    let bytes = urlencoding::decode_binary(segment.as_bytes());
    String::from_utf8_lossy(&bytes).into_owned()
}

fn incompatible_convention(path: &PathUri, convention: PathConvention) -> LegacyAppPathStringError {
    LegacyAppPathStringError::IncompatibleConvention {
        path: path.to_string(),
        convention,
    }
}

#[derive(Debug, Error, PartialEq, Eq)]
pub enum LegacyAppPathStringError {
    #[error("opaque fallback path URI `{path}` cannot be recovered as a native path")]
    OpaqueFallback { path: String },
    #[error("path URI `{path}` cannot be rendered using {convention} path syntax")]
    IncompatibleConvention {
        path: String,
        convention: PathConvention,
    },
    #[error(
        "path `{path}` is not absolute{convention}",
        convention = .convention.map(|convention| format!(" using {convention} path syntax")).unwrap_or_default()
    )]
    InvalidNativePath {
        path: String,
        convention: Option<PathConvention>,
    },
    #[error("unsupported configuration path {path:?} using {convention} path syntax")]
    UnsupportedConfigPath {
        path: String,
        convention: PathConvention,
    },
    #[error("path URI `{cwd}` has no path convention")]
    MissingBaseConvention { cwd: String },
    #[error("cannot resolve home-relative path `{path}` without an executor home")]
    MissingHomeDirectory { path: String },
    #[error(
        "path {path} uses {path_convention} paths, but executor cwd {cwd} uses {convention} paths"
    )]
    MismatchedConvention {
        path: String,
        path_convention: PathConvention,
        cwd: String,
        convention: PathConvention,
    },
    #[error(transparent)]
    PathUri(#[from] PathUriParseError),
}

#[cfg(test)]
#[path = "api_path_string_tests.rs"]
mod tests;
