// 派生自 OpenAI Codex 路径工具；来源见 UPSTREAM.json。

use crate::PathConvention;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Platform {
    Linux,
    Macos,
    Windows,

    Unknown,
}

impl Platform {
    pub fn from_platform_os(platform_os: Option<&str>) -> Self {
        match platform_os {
            Some("linux") => Self::Linux,
            Some("macos") => Self::Macos,
            Some("windows") => Self::Windows,
            Some(_) | None => Self::Unknown,
        }
    }

    pub const fn native() -> Self {
        if cfg!(target_os = "linux") {
            Self::Linux
        } else if cfg!(target_os = "macos") {
            Self::Macos
        } else if cfg!(target_os = "windows") {
            Self::Windows
        } else {
            Self::Unknown
        }
    }

    pub const fn path_convention(self) -> Option<PathConvention> {
        match self {
            Self::Linux | Self::Macos => Some(PathConvention::Posix),
            Self::Windows => Some(PathConvention::Windows),
            Self::Unknown => None,
        }
    }
}

#[cfg(test)]
#[path = "platform_tests.rs"]
mod tests;
