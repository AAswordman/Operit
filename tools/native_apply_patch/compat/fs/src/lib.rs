use codex_utils_path_uri::PathUri;
use std::fs;
use std::future::Future;
use std::io;
use std::pin::Pin;
use std::sync::{Arc, LazyLock};
use tokio::io::AsyncWriteExt;

// 独立命令只使用本地文件系统，不接受执行服务的沙箱上下文。
pub enum FileSystemSandboxContext {}
pub type FileSystemResult<T> = io::Result<T>;
pub type ExecutorFileSystemFuture<'a, T> =
    Pin<Box<dyn Future<Output = FileSystemResult<T>> + Send + 'a>>;

#[derive(Clone, Copy)]
pub struct ReadFileOptions {
    pub follow_symlinks: bool,
}
impl Default for ReadFileOptions {
    fn default() -> Self {
        Self {
            follow_symlinks: true,
        }
    }
}
#[derive(Clone, Copy)]
pub struct WriteFileOptions {
    pub follow_symlinks: bool,
    // 新增及移动目标可要求原子独占创建，避免检查后被其他进程抢先写入。
    pub create_new: bool,
}
#[derive(Clone, Copy)]
pub struct GetMetadataOptions {
    pub follow_symlinks: bool,
}
#[derive(Clone, Copy)]
pub struct CreateDirectoryOptions {
    pub recursive: bool,
    pub follow_symlinks: bool,
}
#[derive(Clone, Copy)]
pub struct RemoveOptions {
    pub recursive: bool,
    pub force: bool,
    pub follow_symlinks: bool,
}
pub struct FileMetadata {
    pub is_directory: bool,
    pub is_file: bool,
    pub is_symlink: bool,
    pub size: u64,
    pub created_at_ms: i64,
    pub modified_at_ms: i64,
}
const FILE_READ_CHUNK_SIZE: usize = 1024 * 1024;
mod local_file_system_read;
mod no_follow;
mod regular_file;
mod protocol {
    pub(crate) enum FsOpenMode {
        Read,
        Replace,
    }
}

pub trait ExecutorFileSystem: Send + Sync {
    fn read_file_text<'a>(
        &'a self,
        path: &'a PathUri,
        options: ReadFileOptions,
        sandbox: Option<&'a FileSystemSandboxContext>,
    ) -> ExecutorFileSystemFuture<'a, String>;
    fn write_file<'a>(
        &'a self,
        path: &'a PathUri,
        contents: Vec<u8>,
        options: WriteFileOptions,
        sandbox: Option<&'a FileSystemSandboxContext>,
    ) -> ExecutorFileSystemFuture<'a, ()>;
    fn get_metadata<'a>(
        &'a self,
        path: &'a PathUri,
        options: GetMetadataOptions,
        sandbox: Option<&'a FileSystemSandboxContext>,
    ) -> ExecutorFileSystemFuture<'a, FileMetadata>;
    fn create_directory<'a>(
        &'a self,
        path: &'a PathUri,
        options: CreateDirectoryOptions,
        sandbox: Option<&'a FileSystemSandboxContext>,
    ) -> ExecutorFileSystemFuture<'a, ()>;
    fn remove<'a>(
        &'a self,
        path: &'a PathUri,
        options: RemoveOptions,
        sandbox: Option<&'a FileSystemSandboxContext>,
    ) -> ExecutorFileSystemFuture<'a, ()>;
}

pub struct LocalFileSystem;
pub static LOCAL_FS: LazyLock<Arc<dyn ExecutorFileSystem>> =
    LazyLock::new(|| Arc::new(LocalFileSystem));

impl ExecutorFileSystem for LocalFileSystem {
    fn read_file_text<'a>(
        &'a self,
        path: &'a PathUri,
        options: ReadFileOptions,
        _: Option<&'a FileSystemSandboxContext>,
    ) -> ExecutorFileSystemFuture<'a, String> {
        Box::pin(async move {
            let bytes = local_file_system_read::read_file(path.to_abs_path()?, options).await?;
            String::from_utf8(bytes)
                .map_err(|error| io::Error::new(io::ErrorKind::InvalidData, error))
        })
    }
    fn write_file<'a>(
        &'a self,
        path: &'a PathUri,
        contents: Vec<u8>,
        options: WriteFileOptions,
        _: Option<&'a FileSystemSandboxContext>,
    ) -> ExecutorFileSystemFuture<'a, ()> {
        Box::pin(async move {
            let path = path.to_abs_path()?;
            if options.follow_symlinks {
                if options.create_new {
                    let mut file = tokio::fs::OpenOptions::new()
                        .write(true)
                        .create_new(true)
                        .open(path.as_path())
                        .await?;
                    file.write_all(&contents).await
                } else {
                    tokio::fs::write(path.as_path(), contents).await
                }
            } else {
                no_follow::write_file(path.as_path(), contents, options.create_new).await
            }
        })
    }
    fn get_metadata<'a>(
        &'a self,
        path: &'a PathUri,
        options: GetMetadataOptions,
        _: Option<&'a FileSystemSandboxContext>,
    ) -> ExecutorFileSystemFuture<'a, FileMetadata> {
        Box::pin(async move {
            let path = path.to_abs_path()?;
            if !options.follow_symlinks {
                return no_follow::metadata(path.as_path()).await;
            }
            let link = fs::symlink_metadata(path.as_path())?;
            let metadata = if link.file_type().is_symlink() {
                fs::metadata(path.as_path())?
            } else {
                link.clone()
            };
            Ok(FileMetadata {
                is_directory: metadata.is_dir(),
                is_file: metadata.is_file(),
                is_symlink: link.file_type().is_symlink(),
                size: metadata.len(),
                created_at_ms: 0,
                modified_at_ms: 0,
            })
        })
    }
    fn create_directory<'a>(
        &'a self,
        path: &'a PathUri,
        options: CreateDirectoryOptions,
        _: Option<&'a FileSystemSandboxContext>,
    ) -> ExecutorFileSystemFuture<'a, ()> {
        Box::pin(async move {
            let path = path.to_abs_path()?;
            if !options.follow_symlinks {
                return no_follow::create_directory(path.as_path(), options.recursive).await;
            }
            if options.recursive {
                tokio::fs::create_dir_all(path.as_path()).await
            } else {
                tokio::fs::create_dir(path.as_path()).await
            }
        })
    }
    fn remove<'a>(
        &'a self,
        path: &'a PathUri,
        options: RemoveOptions,
        _: Option<&'a FileSystemSandboxContext>,
    ) -> ExecutorFileSystemFuture<'a, ()> {
        Box::pin(async move {
            let path = path.to_abs_path()?;
            if !options.follow_symlinks {
                return no_follow::remove(path.as_path(), options.recursive, options.force).await;
            }
            match tokio::fs::symlink_metadata(path.as_path()).await {
                Ok(metadata) => {
                    if metadata.is_dir() {
                        if options.recursive {
                            tokio::fs::remove_dir_all(path.as_path()).await?;
                        } else {
                            tokio::fs::remove_dir(path.as_path()).await?;
                        }
                    } else {
                        tokio::fs::remove_file(path.as_path()).await?;
                    }
                    Ok(())
                }
                Err(error) if options.force && error.kind() == io::ErrorKind::NotFound => Ok(()),
                Err(error) => Err(error),
            }
        })
    }
}
