// 派生自 OpenAI Codex 路径工具；来源见 UPSTREAM.json。

use base64::Engine;
use codex_utils_absolute_path::AbsolutePathBuf;
use codex_utils_absolute_path::normalize_windows_device_path;
use schemars::JsonSchema;
use serde::Deserialize;
use serde::Deserializer;
use serde::Serialize;
use serde::Serializer;
use std::borrow::Cow;
use std::fmt;
use std::hash::Hash;
use std::hash::Hasher;
use std::io;
use std::path::Path;
use std::path::PathBuf;
use std::str::FromStr;
use thiserror::Error;
use ts_rs::TS;
use url::Url;

mod absolute_path_normalization;
mod api_path_string;
mod config_path;
mod native_path_bytes;
mod platform;

use absolute_path_normalization::path_uri_from_segments;

pub use api_path_string::LegacyAppPathString;
pub use api_path_string::LegacyAppPathStringError;
pub use platform::Platform;

pub const FILE_SCHEME: &str = "file";
const BAD_PATH_URI_PREFIX: &str = "file:///%00/bad/path/";

#[derive(Clone, Debug, TS)]
#[ts(type = "string")]
pub struct PathUri(Url);

impl PartialEq for PathUri {
    fn eq(&self, other: &Self) -> bool {
        if self.0 == other.0 {
            return true;
        }
        let (Some(path), Some(other_path)) = (
            self.windows_identity_path_bytes(),
            other.windows_identity_path_bytes(),
        ) else {
            return false;
        };
        self.0.host_str() == other.0.host_str() && path.eq_ignore_ascii_case(&other_path)
    }
}

impl Eq for PathUri {}

impl Hash for PathUri {
    fn hash<H: Hasher>(&self, state: &mut H) {
        let Some(path) = self.windows_identity_path_bytes() else {
            self.0.hash(state);
            return;
        };
        self.0.host_str().hash(state);
        path.len().hash(state);
        for byte in path.as_ref() {
            byte.to_ascii_lowercase().hash(state);
        }
    }
}

impl PathUri {
    pub fn parse(uri: &str) -> Result<Self, PathUriParseError> {
        Url::parse(uri)?.try_into()
    }

    pub fn from_abs_path(path: &AbsolutePathBuf) -> Self {
        if let Ok(url) = Url::from_file_path(path.as_path())
            && let Ok(uri) = Self::try_from(url)
            && uri.0.host_str() != Some("")
            && uri.infer_path_convention() == Some(PathConvention::native())
        {
            return uri;
        }

        #[cfg(unix)]
        let path_bytes = {
            use std::os::unix::ffi::OsStrExt;
            path.as_path().as_os_str().as_bytes().to_vec()
        };
        #[cfg(windows)]
        let path_bytes = {
            use std::os::windows::ffi::OsStrExt;
            path.as_path()
                .as_os_str()
                .encode_wide()
                .flat_map(u16::to_le_bytes)
                .collect::<Vec<_>>()
        };
        Self::from_opaque_path_bytes(&path_bytes)
    }

    pub(crate) fn from_absolute_native_path(
        path: &str,
        convention: PathConvention,
    ) -> Option<Self> {
        let uri = match convention {
            PathConvention::Posix => parse_posix_path(path),
            PathConvention::Windows => parse_windows_path(path),
        }?;
        if uri.0.host_str() != Some("") && uri.infer_path_convention() == Some(convention) {
            return Some(uri);
        }
        Some(match convention {
            PathConvention::Posix => Self::from_opaque_path_bytes(path.as_bytes()),
            PathConvention::Windows => windows_opaque_path_uri(path),
        })
    }

    fn from_opaque_path_bytes(path_bytes: &[u8]) -> Self {
        let encoded_path = base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(path_bytes);
        let Ok(uri) = Self::parse(&format!("{BAD_PATH_URI_PREFIX}{encoded_path}")) else {
            unreachable!("URL-safe base64 always produces a valid fallback path URI");
        };
        uri
    }

    pub fn from_host_native_path(path: impl AsRef<Path>) -> io::Result<Self> {
        let path = AbsolutePathBuf::from_absolute_path_checked(path)
            .map_err(|err| io::Error::new(io::ErrorKind::InvalidInput, err))?;
        Ok(Self::from_abs_path(&path))
    }

    pub fn encoded_path(&self) -> &str {
        self.0.path()
    }

    pub fn decoded_path_bytes(&self) -> Cow<'_, [u8]> {
        urlencoding::decode_binary(self.encoded_path().as_bytes())
    }

    fn windows_identity_path_bytes(&self) -> Option<Cow<'_, [u8]>> {
        if self.infer_path_convention() != Some(PathConvention::Windows)
            || self.opaque_fallback_bytes().is_some()
            || self.0.path_segments()?.any(|segment| {
                urlencoding::decode_binary(segment.as_bytes())
                    .iter()
                    .any(|byte| matches!(byte, b'/' | b'\\'))
            })
        {
            return None;
        }

        Some(urlencoding::decode_binary(self.0.path().as_bytes()))
    }

    fn opaque_fallback_bytes(&self) -> Option<Vec<u8>> {
        decode_bad_path_uri(&self.0)
    }

    pub fn infer_path_convention(&self) -> Option<PathConvention> {
        if let Some(path_bytes) = self.opaque_fallback_bytes() {
            return infer_opaque_path_convention(&path_bytes);
        }
        if self.0.host_str().is_some() {
            return Some(PathConvention::Windows);
        }

        let has_windows_drive = self
            .0
            .path_segments()
            .and_then(|mut segments| segments.find(|segment| !segment.is_empty()))
            .is_some_and(is_windows_drive_uri_segment);
        if has_windows_drive {
            Some(PathConvention::Windows)
        } else {
            Some(PathConvention::Posix)
        }
    }

    pub fn inferred_native_path_string(&self) -> String {
        self.infer_path_convention()
            .and_then(|convention| LegacyAppPathString::from_path_uri(self, convention).ok())
            .map(LegacyAppPathString::into_string)
            .unwrap_or_else(|| self.to_string())
    }

    pub fn basename(&self) -> Option<String> {
        if decode_bad_path_uri(&self.0).is_some() {
            return None;
        }

        self.0
            .path_segments()?
            .rfind(|segment| !segment.is_empty())
            .map(decode_uri_path)
    }

    pub fn to_path_buf(&self) -> PathBuf {
        PathBuf::from(self.inferred_native_path_string())
    }

    pub fn parent(&self) -> Option<Self> {
        if decode_bad_path_uri(&self.0).is_some() {
            return None;
        }

        let convention = self.infer_path_convention()?;

        let anchor_depth = usize::from(convention == PathConvention::Windows);
        let depth = self
            .0
            .path_segments()?
            .filter(|segment| !segment.is_empty())
            .count();
        if depth <= anchor_depth {
            return None;
        }
        let mut url = self.0.clone();
        {
            let mut segments = match url.path_segments_mut() {
                Ok(segments) => segments,
                Err(()) => unreachable!("validated file URLs support hierarchical path segments"),
            };
            segments.pop_if_empty().pop();
        }
        Some(Self(url))
    }

    pub fn ancestors(&self) -> impl Iterator<Item = Self> {
        std::iter::successors(Some(self.clone()), Self::parent)
    }

    pub fn starts_with(&self, base: &Self) -> bool {
        if self == base {
            return true;
        }
        if decode_bad_path_uri(&self.0).is_some() || decode_bad_path_uri(&base.0).is_some() {
            return false;
        }
        if self.0.host_str() != base.0.host_str() {
            return false;
        }

        let convention = self.infer_path_convention();
        if convention != base.infer_path_convention() {
            return false;
        }
        let convention = convention.unwrap_or(PathConvention::Posix);
        let Some(path_segments) = containment_path_segments(&self.0, convention) else {
            return false;
        };
        let Some(base_segments) = containment_path_segments(&base.0, convention) else {
            return false;
        };
        native_path_segments_start_with(&path_segments, &base_segments, convention)
    }

    pub fn overlaps(&self, other: &Self) -> Option<bool> {
        if self == other {
            return Some(true);
        }
        self.lexical_depth()?;
        other.lexical_depth()?;
        Some(self.starts_with(other) || other.starts_with(self))
    }

    pub fn is_opaque(&self) -> bool {
        self.opaque_fallback_bytes().is_some()
    }

    pub fn lexical_depth(&self) -> Option<usize> {
        if decode_bad_path_uri(&self.0).is_some() {
            return None;
        }
        let convention = self.infer_path_convention()?;
        containment_path_segments(&self.0, convention).map(|segments| segments.len())
    }

    pub fn relative_path_from(&self, base: &Self) -> Option<String> {
        if self == base {
            return Some(String::new());
        }
        if decode_bad_path_uri(&self.0).is_some()
            || decode_bad_path_uri(&base.0).is_some()
            || self.0.host_str() != base.0.host_str()
            || self.infer_path_convention() != base.infer_path_convention()
        {
            return None;
        }

        let convention = self.infer_path_convention()?;
        let path_segments = containment_path_segments(&self.0, convention)?;
        let base_segments = containment_path_segments(&base.0, convention)?;
        if !native_path_segments_start_with(&path_segments, &base_segments, convention) {
            return None;
        }
        let relative_segments = &path_segments[base_segments.len()..];
        let separator = match convention {
            PathConvention::Posix => "/",
            PathConvention::Windows => "\\",
        };
        Some(
            relative_segments
                .iter()
                .map(|segment| decode_uri_path(segment))
                .collect::<Vec<_>>()
                .join(separator),
        )
    }

    pub fn join(&self, path: &str) -> Result<Self, PathUriParseError> {
        if path.contains('\0') {
            return Err(PathUriParseError::InvalidFileUriPath {
                path: path.to_string(),
            });
        }
        if path.is_empty() {
            return Ok(self.clone());
        }
        let convention =
            self.infer_path_convention()
                .ok_or_else(|| PathUriParseError::InvalidFileUriPath {
                    path: self.to_string(),
                })?;

        // URL 路径分段解析会忽略制表符和换行，原生文件名必须先按字节编码。
        if convention == PathConvention::Posix && native_path_bytes::has_url_ignored_bytes(path) {
            return self.join_native_bytes(path.as_bytes());
        }
        if let Some(absolute) = Self::from_absolute_native_path(path, convention) {
            return Ok(absolute);
        }
        let path_bytes = path.as_bytes();
        let path = if convention == PathConvention::Windows
            && matches!(path_bytes, [drive, b':', ..] if drive.is_ascii_alphabetic())
        {
            let same_drive = self
                .0
                .path_segments()
                .and_then(|mut segments| segments.find(|segment| !segment.is_empty()))
                .is_some_and(|segment| {
                    is_windows_drive_uri_segment(segment)
                        && segment.as_bytes()[0].eq_ignore_ascii_case(&path_bytes[0])
                });
            if !same_drive {
                return Err(PathUriParseError::InvalidFileUriPath {
                    path: path.to_string(),
                });
            }
            &path[2..]
        } else {
            path
        };
        let path_bytes = path.as_bytes();
        if decode_bad_path_uri(&self.0).is_some() {
            return Err(PathUriParseError::InvalidFileUriPath {
                path: self.to_string(),
            });
        }

        let mut url = self.0.clone();
        let anchor_depth = usize::from(convention == PathConvention::Windows);
        let mut depth = url
            .path_segments()
            .map(|segments| segments.filter(|segment| !segment.is_empty()).count())
            .unwrap_or_default();
        let windows_root_relative = convention == PathConvention::Windows
            && matches!(path_bytes, [b'\\' | b'/', rest @ ..] if !matches!(rest, [b'\\' | b'/', ..]));
        {
            let Ok(mut segments) = url.path_segments_mut() else {
                unreachable!("validated file URLs support hierarchical path segments");
            };
            segments.pop_if_empty();
            if windows_root_relative {
                while depth > anchor_depth {
                    segments.pop();
                    depth -= 1;
                }
            }
            let path = match convention {
                PathConvention::Posix => path.to_string(),
                PathConvention::Windows => path.replace('\\', "/"),
            };
            for component in path.split('/') {
                match component {
                    "" | "." => {}
                    ".." => {
                        if depth > anchor_depth {
                            segments.pop();
                            depth -= 1;
                        }
                    }
                    component => {
                        segments.push(component);
                        depth += 1;
                    }
                }
            }
        }
        Self::try_from(url)
    }

    pub fn join_descendant(&self, path: &str) -> Result<Self, PathUriParseError> {
        let descendant = self.join(path)?;
        let windows = self.infer_path_convention() == Some(PathConvention::Windows);
        if path.starts_with('/')
            || windows
                && (path.starts_with('\\')
                    || PathConvention::Windows
                        .path_segments(path)
                        .any(|segment| segment.contains(':')))
            || !descendant.starts_with(self)
        {
            return Err(PathUriParseError::JoinPathMustBeDescendant(
                path.to_string(),
            ));
        }
        Ok(descendant)
    }

    pub fn to_abs_path(&self) -> io::Result<AbsolutePathBuf> {
        if self.infer_path_convention() != Some(PathConvention::native())
            || (PathConvention::native() == PathConvention::Windows
                && containment_path_segments(&self.0, PathConvention::Windows).is_none())
        {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                PathUriParseError::InvalidFileUriPath {
                    path: self.to_string(),
                },
            ));
        }
        if let Some(path_bytes) = decode_bad_path_uri(&self.0) {
            #[cfg(unix)]
            let decoded_path = {
                use std::os::unix::ffi::OsStringExt;
                Some(std::path::PathBuf::from(std::ffi::OsString::from_vec(
                    path_bytes,
                )))
            };
            #[cfg(windows)]
            let decoded_path = {
                use std::os::windows::ffi::OsStringExt;
                path_bytes.len().is_multiple_of(2).then(|| {
                    let path_wide = path_bytes
                        .chunks_exact(2)
                        .map(|bytes| u16::from_le_bytes([bytes[0], bytes[1]]))
                        .collect::<Vec<_>>();
                    std::path::PathBuf::from(std::ffi::OsString::from_wide(&path_wide))
                })
            };
            if let Some(decoded_path) = decoded_path
                && let Ok(path) = AbsolutePathBuf::from_absolute_path_checked(decoded_path)
                && Self::from_abs_path(&path).eq(self)
            {
                return Ok(path);
            }

            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                PathUriParseError::InvalidFileUriPath {
                    path: self.to_string(),
                },
            ));
        }

        let path = self.0.to_file_path().map_err(|()| {
            io::Error::new(
                io::ErrorKind::InvalidInput,
                PathUriParseError::InvalidFileUriPath {
                    path: self.to_string(),
                },
            )
        })?;
        AbsolutePathBuf::from_absolute_path_checked(path).map_err(|_| {
            io::Error::new(
                io::ErrorKind::InvalidInput,
                PathUriParseError::InvalidFileUriPath {
                    path: self.to_string(),
                },
            )
        })
    }

    pub fn to_url(&self) -> Url {
        self.0.clone()
    }
}

impl TryFrom<Url> for PathUri {
    type Error = PathUriParseError;

    fn try_from(url: Url) -> Result<Self, Self::Error> {
        if url.scheme() != FILE_SCHEME {
            return Err(PathUriParseError::UnsupportedScheme(
                url.scheme().to_string(),
            ));
        }
        validate_file_url(&url)?;
        let url = without_localhost_authority(url);
        let url = with_normalized_windows_drive_letter(url);
        Ok(Self(url))
    }
}

impl TryFrom<String> for PathUri {
    type Error = PathUriParseError;

    fn try_from(uri: String) -> Result<Self, Self::Error> {
        Self::parse(&uri)
    }
}

impl From<AbsolutePathBuf> for PathUri {
    fn from(p: AbsolutePathBuf) -> Self {
        Self::from_abs_path(&p)
    }
}

impl<'de> Deserialize<'de> for PathUri {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        let value = String::deserialize(deserializer)?;
        Self::parse(&value).map_err(serde::de::Error::custom)
    }
}

impl FromStr for PathUri {
    type Err = PathUriParseError;

    fn from_str(uri: &str) -> Result<Self, Self::Err> {
        Self::parse(uri)
    }
}

impl fmt::Display for PathUri {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        self.0.fmt(f)
    }
}

impl Serialize for PathUri {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        serializer.serialize_str(self.0.as_str())
    }
}

impl JsonSchema for PathUri {
    fn schema_name() -> String {
        "PathUri".to_string()
    }

    fn json_schema(generator: &mut schemars::r#gen::SchemaGenerator) -> schemars::schema::Schema {
        String::json_schema(generator)
    }
}

fn without_localhost_authority(mut url: Url) -> Url {
    if url.host_str() == Some("localhost") {
        let Ok(()) = url.set_host(None) else {
            unreachable!("validated file URLs can remove a localhost authority");
        };
    }
    url
}

fn with_normalized_windows_drive_letter(mut url: Url) -> Url {
    if url.host_str().is_some() {
        return url;
    }

    let path = url.path();
    let Some(drive_start) = path.bytes().position(|byte| byte != b'/') else {
        return url;
    };
    let Some(drive) = path[drive_start..].split('/').next() else {
        return url;
    };
    if !is_windows_drive_uri_segment(drive) || drive.as_bytes()[0].is_ascii_uppercase() {
        return url;
    }

    let drive_letter = char::from(drive.as_bytes()[0]).to_ascii_uppercase();
    let normalized_path = format!(
        "{}{drive_letter}{}",
        &path[..drive_start],
        &path[drive_start + 1..]
    );
    url.set_path(&normalized_path);
    url
}

fn decode_uri_path(path: &str) -> String {
    urlencoding::decode(path)
        .map(std::borrow::Cow::into_owned)
        .unwrap_or_else(|_| path.to_string())
}

fn decode_bad_path_uri(url: &Url) -> Option<Vec<u8>> {
    let encoded_path = url.as_str().strip_prefix(BAD_PATH_URI_PREFIX)?;
    if encoded_path.is_empty() || encoded_path.contains('/') {
        return None;
    }

    let path_bytes = base64::engine::general_purpose::URL_SAFE_NO_PAD
        .decode(encoded_path)
        .ok()?;
    (base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(&path_bytes) == encoded_path)
        .then_some(path_bytes)
}

fn is_windows_drive_uri_segment(segment: &str) -> bool {
    matches!(
        segment.as_bytes(),
        [drive, b':'] | [drive, b'%', b'3', b'A' | b'a'] if drive.is_ascii_alphabetic()
    )
}

fn containment_path_segments(url: &Url, convention: PathConvention) -> Option<Vec<&str>> {
    let segments = url
        .path_segments()?
        .filter(|segment| !segment.is_empty())
        .collect::<Vec<_>>();
    (!segments.iter().any(|segment| {
        urlencoding::decode_binary(segment.as_bytes())
            .iter()
            .any(|byte| *byte == b'/' || (convention == PathConvention::Windows && *byte == b'\\'))
    }))
    .then_some(segments)
}

fn native_path_segments_start_with(
    path_segments: &[&str],
    base_segments: &[&str],
    convention: PathConvention,
) -> bool {
    match convention {
        PathConvention::Posix => {
            path_segments.len() >= base_segments.len()
                && path_segments.iter().zip(base_segments).all(|(path, base)| {
                    urlencoding::decode_binary(path.as_bytes())
                        == urlencoding::decode_binary(base.as_bytes())
                })
        }
        PathConvention::Windows => {
            path_segments.len() >= base_segments.len()
                && path_segments.iter().zip(base_segments).all(|(path, base)| {
                    path.eq_ignore_ascii_case(base)
                        || decode_uri_path(path).eq_ignore_ascii_case(&decode_uri_path(base))
                })
        }
    }
}

fn infer_opaque_path_convention(path_bytes: &[u8]) -> Option<PathConvention> {
    if path_bytes.starts_with(b"/") && !path_bytes.starts_with(b"/\0") {
        return Some(PathConvention::Posix);
    }
    if !path_bytes.len().is_multiple_of(2) {
        return None;
    }

    let mut path_wide = path_bytes
        .chunks_exact(2)
        .map(|bytes| u16::from_le_bytes([bytes[0], bytes[1]]));
    let first = path_wide.next()?;
    let second = path_wide.next()?;
    let has_drive = u8::try_from(first).is_ok_and(|drive| drive.is_ascii_alphabetic())
        && second == u16::from(b':');
    let is_separator = |character| character == u16::from(b'\\') || character == u16::from(b'/');
    let has_unc_prefix = is_separator(first) && is_separator(second);
    (has_drive || has_unc_prefix).then_some(PathConvention::Windows)
}

fn parse_posix_path(path: &str) -> Option<PathUri> {
    let path = path.strip_prefix('/')?;
    if path.contains('\0') {
        return Some(PathUri::from_opaque_path_bytes(
            format!("/{path}").as_bytes(),
        ));
    }
    path_uri_from_segments(PathConvention::Posix, None, path.split('/'))
}

fn parse_windows_path(path: &str) -> Option<PathUri> {
    if let Some(normalized_path) = normalize_windows_device_path(path) {
        if let Some(unc_path) = normalized_path.strip_prefix(r"\\") {
            let mut components = unc_path.split(is_windows_separator_char);
            if matches!(components.next(), None | Some("" | "." | ".."))
                || matches!(components.next(), None | Some("" | "." | ".."))
            {
                return Some(windows_opaque_path_uri(path));
            }
        }
        return Some(
            parse_unnormalized_windows_path(&normalized_path)
                .filter(|uri| {
                    uri.infer_path_convention() == Some(PathConvention::Windows)
                        && uri.opaque_fallback_bytes().is_none()
                        && (!normalized_path.starts_with(r"\\")
                            || uri.0.host_str().is_some_and(|host| !host.is_empty()))
                })
                .unwrap_or_else(|| windows_opaque_path_uri(path)),
        );
    }
    parse_unnormalized_windows_path(path)
}

fn parse_unnormalized_windows_path(path: &str) -> Option<PathUri> {
    let bytes = path.as_bytes();
    let uses_namespace = matches!(
        bytes,
        [first, second, namespace @ (b'.' | b'?'), separator, ..]
            if is_windows_separator_byte(*first)
                && is_windows_separator_byte(*second)
                && is_windows_separator_byte(*separator)
                && matches!(*namespace, b'.' | b'?')
    );
    if uses_namespace || path.contains('\0') {
        return Some(windows_opaque_path_uri(path));
    }

    if matches!(
        bytes,
        [drive, b':', separator, ..]
            if drive.is_ascii_alphabetic() && is_windows_separator_byte(*separator)
    ) {
        return path_uri_from_segments(
            PathConvention::Windows,
            None,
            std::iter::once(&path[..2]).chain(path[3..].split(is_windows_separator_char)),
        );
    }

    if matches!(bytes, [first, second, ..]
        if is_windows_separator_byte(*first) && is_windows_separator_byte(*second))
    {
        let mut components = path[2..].split(is_windows_separator_char);
        let host = components.next().filter(|host| !host.is_empty())?;
        let share = components.next().filter(|share| !share.is_empty())?;
        if matches!(host, "." | "..") || matches!(share, "." | "..") {
            return Some(windows_opaque_path_uri(path));
        }
        return path_uri_from_segments(
            PathConvention::Windows,
            Some(host),
            std::iter::once(share).chain(components),
        )
        .filter(|uri| {
            uri.0
                .host_str()
                .is_some_and(|parsed| parsed.eq_ignore_ascii_case(host))
        })
        .or_else(|| Some(windows_opaque_path_uri(path)));
    }

    None
}

fn windows_opaque_path_uri(path: &str) -> PathUri {
    let path_bytes = path
        .encode_utf16()
        .flat_map(u16::to_le_bytes)
        .collect::<Vec<_>>();
    PathUri::from_opaque_path_bytes(&path_bytes)
}

fn is_windows_separator_char(character: char) -> bool {
    matches!(character, '\\' | '/')
}

pub(crate) fn is_windows_separator_byte(character: u8) -> bool {
    matches!(character, b'\\' | b'/')
}

fn validate_common_known_uri(url: &Url) -> Result<(), PathUriParseError> {
    if !url.username().is_empty() || url.password().is_some() {
        return Err(PathUriParseError::CredentialsNotAllowed);
    }
    if url.port().is_some() {
        return Err(PathUriParseError::PortNotAllowed);
    }
    if url.query().is_some() {
        return Err(PathUriParseError::QueryNotAllowed);
    }
    if url.fragment().is_some() {
        return Err(PathUriParseError::FragmentNotAllowed);
    }
    Ok(())
}

fn validate_file_url(url: &Url) -> Result<(), PathUriParseError> {
    validate_common_known_uri(url)?;

    if urlencoding::decode_binary(url.path().as_bytes()).contains(&0)
        && decode_bad_path_uri(url).is_none()
    {
        return Err(PathUriParseError::InvalidFileUriPath {
            path: url.to_string(),
        });
    }
    Ok(())
}

#[derive(Debug, Error, PartialEq, Eq)]
pub enum PathUriParseError {
    #[error("invalid URI: {0}")]
    InvalidUri(#[from] url::ParseError),
    #[error("unsupported path URI scheme `{0}`")]
    UnsupportedScheme(String),
    #[error("'{path}' is invalid on '{os}'", os = std::env::consts::OS)]
    InvalidFileUriPath { path: String },
    #[error("credentials are not allowed in path URIs")]
    CredentialsNotAllowed,
    #[error("ports are not allowed in path URIs")]
    PortNotAllowed,
    #[error("query parameters are not allowed in path URIs")]
    QueryNotAllowed,
    #[error("fragments are not allowed in path URIs")]
    FragmentNotAllowed,
    #[error("path `{0}` must resolve to a relative descendant when joining a path URI")]
    JoinPathMustBeDescendant(String),
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Serialize, Deserialize, JsonSchema, TS)]
#[serde(rename_all = "snake_case")]
#[ts(rename_all = "snake_case")]
pub enum PathConvention {
    Posix,
    Windows,
}

impl PathConvention {
    #[cfg(windows)]
    pub const fn native() -> Self {
        Self::Windows
    }

    #[cfg(unix)]
    pub const fn native() -> Self {
        Self::Posix
    }

    pub fn home_relative_suffix(self, path: &str) -> Option<&str> {
        let suffix = path.strip_prefix('~')?;
        (suffix.is_empty()
            || suffix.starts_with('/')
            || self == Self::Windows && suffix.starts_with('\\'))
        .then_some(suffix)
    }

    pub fn path_segments(self, path: &str) -> impl DoubleEndedIterator<Item = &str> {
        path.split(move |character| match self {
            Self::Posix => character == '/',
            Self::Windows => matches!(character, '/' | '\\'),
        })
    }
}

impl fmt::Display for PathConvention {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Posix => f.write_str("POSIX"),
            Self::Windows => f.write_str("Windows"),
        }
    }
}

#[cfg(test)]
#[path = "tests.rs"]
mod tests;
