// 独立命令的发布目标是 Ubuntu ARM64；沿用原版基于目录描述符的符号链接处理。
use crate::FileMetadata;
use std::{io, path::Path};
mod unix;

pub(crate) fn open_file(path: &Path) -> io::Result<std::fs::File> {
    unix::open_file_sync(path)
}
pub(crate) async fn write_file(path: &Path, contents: Vec<u8>, create_new: bool) -> io::Result<()> {
    unix::write_file(path.to_path_buf(), contents, create_new).await
}
pub(crate) async fn metadata(path: &Path) -> io::Result<FileMetadata> {
    unix::metadata(path.to_path_buf()).await
}
pub(crate) async fn create_directory(path: &Path, recursive: bool) -> io::Result<()> {
    unix::create_directory(path.to_path_buf(), recursive).await
}
pub(crate) async fn remove(path: &Path, recursive: bool, force: bool) -> io::Result<()> {
    unix::remove(path.to_path_buf(), recursive, force).await
}
