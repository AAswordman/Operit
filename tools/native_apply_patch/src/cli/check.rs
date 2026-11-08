use super::preview::{self, Change, Content};
use codex_exec_server::{
    CreateDirectoryOptions, ExecutorFileSystem, ExecutorFileSystemFuture, FileMetadata,
    FileSystemSandboxContext, GetMetadataOptions, LOCAL_FS, ReadFileOptions, RemoveOptions,
    WriteFileOptions,
};
use codex_utils_path_uri::PathUri;
use std::collections::{HashMap, VecDeque};
use std::ffi::OsString;
use std::io;
use std::os::unix::fs::MetadataExt;
use std::path::{Component, Path, PathBuf};
use std::sync::{Arc, Mutex};

#[derive(Clone)]
enum Node {
    File(Arc<Mutex<Vec<u8>>>),
    Directory,
    Link(PathBuf),
    Other,
    Missing,
}

struct Location {
    path: PathBuf,
    leaf_link: bool,
}

/// 检查模式只把写入、建目录和删除保存到内存，磁盘仅用于读取原始内容。
#[derive(Default)]
pub(super) struct CheckFileSystem {
    entries: Mutex<HashMap<PathBuf, Node>>,
    preview: Option<Mutex<Vec<Change>>>,
    // 原有硬链接共享内容；内存里模拟写入时也保持同一个 inode 的别名一致。
    inodes: Mutex<HashMap<(u64, u64), Arc<Mutex<Vec<u8>>>>>,
}

impl CheckFileSystem {
    pub fn with_preview() -> Self {
        Self {
            preview: Some(Mutex::new(Vec::new())),
            ..Self::default()
        }
    }

    pub fn diff(&self, cwd: &PathUri) -> io::Result<String> {
        let changes = self
            .preview
            .as_ref()
            .ok_or_else(|| io::Error::other("preview recording is not enabled"))?
            .lock()
            .map_err(|_| io::Error::other("preview lock poisoned"))?;
        Ok(preview::render(&changes, cwd.to_abs_path()?.as_path()))
    }

    async fn preview_content(&self, path: &Path, node: &Node) -> io::Result<Content> {
        match node {
            Node::Missing => Ok(Content::Missing),
            Node::Link(target) => Ok(Content::Link(target.clone())),
            Node::File(bytes) => Ok(Content::from_bytes(
                bytes
                    .lock()
                    .map_err(|_| io::Error::other("check content lock poisoned"))?
                    .clone(),
            )),
            Node::Other => {
                let uri = PathUri::from_host_native_path(path).map_err(io::Error::other)?;
                match LOCAL_FS
                    .read_file_text(&uri, ReadFileOptions::default(), None)
                    .await
                {
                    Ok(text) => Ok(Content::from_bytes(text.into_bytes())),
                    Err(error) if error.kind() == io::ErrorKind::InvalidData => {
                        Ok(Content::Binary(None))
                    }
                    Err(error) => Err(error),
                }
            }
            Node::Directory => Err(io::Error::from(io::ErrorKind::IsADirectory)),
        }
    }

    fn record_preview(&self, change: Option<Change>) -> io::Result<()> {
        if let (Some(changes), Some(change)) = (&self.preview, change) {
            changes
                .lock()
                .map_err(|_| io::Error::other("preview lock poisoned"))?
                .push(change);
        }
        Ok(())
    }

    // 独占创建只解析父目录，叶子即使是断开的符号链接也视为已存在。
    fn locate_new(&self, path: &Path, follow: bool) -> io::Result<Location> {
        let parent = path
            .parent()
            .ok_or_else(|| io::Error::from(io::ErrorKind::AlreadyExists))?;
        let leaf = path
            .file_name()
            .ok_or_else(|| io::Error::from(io::ErrorKind::AlreadyExists))?;
        let directory = self.locate(parent, follow, true)?;
        match self.node(&directory.path)? {
            Node::Directory => {}
            Node::Missing => return Err(io::Error::from(io::ErrorKind::NotFound)),
            _ => return Err(io::Error::from(io::ErrorKind::NotADirectory)),
        }
        let path = directory.path.join(leaf);
        if !matches!(self.node(&path)?, Node::Missing) {
            return Err(io::Error::from(io::ErrorKind::AlreadyExists));
        }
        Ok(Location {
            path,
            leaf_link: false,
        })
    }

    fn saved(&self, path: &Path) -> io::Result<Option<Node>> {
        self.entries
            .lock()
            .map(|entries| entries.get(path).cloned())
            .map_err(|_| io::Error::other("check filesystem lock poisoned"))
    }

    fn save(&self, path: PathBuf, node: Node) -> io::Result<()> {
        self.entries
            .lock()
            .map_err(|_| io::Error::other("check filesystem lock poisoned"))?
            .insert(path, node);
        Ok(())
    }

    fn node(&self, path: &Path) -> io::Result<Node> {
        if let Some(node) = self.saved(path)? {
            return Ok(node);
        }
        match std::fs::symlink_metadata(path) {
            Ok(metadata) if metadata.is_symlink() => Ok(Node::Link(std::fs::read_link(path)?)),
            Ok(metadata) if metadata.is_dir() => Ok(Node::Directory),
            Ok(metadata) if metadata.is_file() => {
                let inodes = self
                    .inodes
                    .lock()
                    .map_err(|_| io::Error::other("check inode lock poisoned"))?;
                Ok(inodes
                    .get(&(metadata.dev(), metadata.ino()))
                    .cloned()
                    .map(Node::File)
                    .unwrap_or(Node::Other))
            }
            Ok(_) => Ok(Node::Other),
            Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(Node::Missing),
            Err(error) => Err(error),
        }
    }

    fn parts(path: &Path) -> VecDeque<OsString> {
        path.components()
            .filter_map(|part| match part {
                Component::RootDir | Component::CurDir => None,
                other => Some(other.as_os_str().to_owned()),
            })
            .collect()
    }

    // 逐段解析真实链接，同时识别内存中的新增/删除；删除链接时只移除链接本身。
    fn locate(&self, path: &Path, follow: bool, follow_leaf: bool) -> io::Result<Location> {
        if !path.is_absolute() {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "path must be absolute",
            ));
        }
        let mut pending = Self::parts(path);
        let mut current = PathBuf::from("/");
        let mut links = 0;
        let mut leaf_link = false;
        while let Some(part) = pending.pop_front() {
            if part == ".." {
                current.pop();
                continue;
            }
            let next = current.join(&part);
            let leaf = pending.is_empty();
            match self.node(&next)? {
                Node::Link(target) => {
                    if !follow {
                        return Err(io::Error::new(
                            io::ErrorKind::InvalidInput,
                            "path contains a symbolic link",
                        ));
                    }
                    if leaf {
                        leaf_link = true;
                    }
                    if leaf && !follow_leaf {
                        current = next;
                        continue;
                    }
                    links += 1;
                    if links > 40 {
                        return Err(io::Error::new(
                            io::ErrorKind::InvalidInput,
                            "too many symbolic links",
                        ));
                    }
                    if target.is_absolute() {
                        current = PathBuf::from("/");
                    }
                    let mut target_parts = Self::parts(&target);
                    target_parts.append(&mut pending);
                    pending = target_parts;
                }
                Node::Directory => current = next,
                Node::Missing if !leaf => return Err(io::Error::from(io::ErrorKind::NotFound)),
                Node::File(_) | Node::Other if !leaf => {
                    return Err(io::Error::from(io::ErrorKind::NotADirectory));
                }
                _ => current = next,
            }
        }
        Ok(Location {
            path: current,
            leaf_link,
        })
    }

    fn create_dir(&self, path: &Path, recursive: bool, follow: bool) -> io::Result<()> {
        if recursive {
            if let Some(parent) = path.parent() {
                self.create_dir(parent, true, follow)?;
            }
        }
        let location = self.locate(path, follow, true)?;
        match self.node(&location.path)? {
            Node::Directory if recursive => Ok(()),
            Node::Missing => self.save(location.path, Node::Directory),
            _ => Err(io::Error::from(io::ErrorKind::AlreadyExists)),
        }
    }
}

impl ExecutorFileSystem for CheckFileSystem {
    fn read_file_text<'a>(
        &'a self,
        path: &'a PathUri,
        options: ReadFileOptions,
        _: Option<&'a FileSystemSandboxContext>,
    ) -> ExecutorFileSystemFuture<'a, String> {
        Box::pin(async move {
            let location =
                self.locate(path.to_abs_path()?.as_path(), options.follow_symlinks, true)?;
            match self.node(&location.path)? {
                Node::File(contents) => {
                    let bytes = contents
                        .lock()
                        .map_err(|_| io::Error::other("check content lock poisoned"))?
                        .clone();
                    if bytes.len() as u64 > 512 * 1024 * 1024 {
                        return Err(io::Error::new(
                            io::ErrorKind::InvalidInput,
                            "file is too large to read: limit is 536870912 bytes",
                        ));
                    }
                    return String::from_utf8(bytes)
                        .map_err(|error| io::Error::new(io::ErrorKind::InvalidData, error));
                }
                Node::Missing => return Err(io::Error::from(io::ErrorKind::NotFound)),
                Node::Directory => {
                    return Err(io::Error::new(
                        io::ErrorKind::InvalidInput,
                        "path is not a regular file",
                    ));
                }
                _ => {}
            }
            let uri = PathUri::from_host_native_path(&location.path).map_err(io::Error::other)?;
            LOCAL_FS.read_file_text(&uri, options, None).await
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
            let absolute = path.to_abs_path()?;
            let location = if options.create_new {
                self.locate_new(absolute.as_path(), options.follow_symlinks)?
            } else {
                self.locate(absolute.as_path(), options.follow_symlinks, true)?
            };
            let node = self.node(&location.path)?;
            let preview = if self.preview.is_some() {
                Some(Change {
                    path: location.path.clone(),
                    before: self.preview_content(&location.path, &node).await?,
                    after: Content::from_bytes(contents.clone()),
                })
            } else {
                None
            };
            match node {
                Node::Directory => return Err(io::Error::from(io::ErrorKind::IsADirectory)),
                Node::File(shared) => {
                    *shared
                        .lock()
                        .map_err(|_| io::Error::other("check content lock poisoned"))? = contents;
                    self.save(location.path, Node::File(shared))?;
                }
                Node::Other => {
                    let metadata = std::fs::metadata(&location.path)?;
                    if !metadata.is_file() {
                        return Err(io::Error::new(
                            io::ErrorKind::InvalidInput,
                            "path is not a regular file",
                        ));
                    }
                    let shared = Arc::new(Mutex::new(contents));
                    self.inodes
                        .lock()
                        .map_err(|_| io::Error::other("check inode lock poisoned"))?
                        .insert((metadata.dev(), metadata.ino()), shared.clone());
                    self.save(location.path, Node::File(shared))?;
                }
                _ => self.save(location.path, Node::File(Arc::new(Mutex::new(contents))))?,
            }
            self.record_preview(preview)
        })
    }

    fn get_metadata<'a>(
        &'a self,
        path: &'a PathUri,
        options: GetMetadataOptions,
        _: Option<&'a FileSystemSandboxContext>,
    ) -> ExecutorFileSystemFuture<'a, FileMetadata> {
        Box::pin(async move {
            let location =
                self.locate(path.to_abs_path()?.as_path(), options.follow_symlinks, true)?;
            let node = self.node(&location.path)?;
            if matches!(node, Node::Missing) {
                return Err(io::Error::from(io::ErrorKind::NotFound));
            }
            if let Node::Other = node {
                let uri =
                    PathUri::from_host_native_path(&location.path).map_err(io::Error::other)?;
                let mut metadata = LOCAL_FS.get_metadata(&uri, options, None).await?;
                metadata.is_symlink = location.leaf_link;
                return Ok(metadata);
            }
            let size = match &node {
                Node::File(bytes) => bytes
                    .lock()
                    .map_err(|_| io::Error::other("check content lock poisoned"))?
                    .len() as u64,
                _ => 0,
            };
            Ok(FileMetadata {
                is_directory: matches!(node, Node::Directory),
                is_file: matches!(node, Node::File(_)),
                is_symlink: location.leaf_link,
                size,
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
            self.create_dir(
                path.to_abs_path()?.as_path(),
                options.recursive,
                options.follow_symlinks,
            )
        })
    }

    fn remove<'a>(
        &'a self,
        path: &'a PathUri,
        options: RemoveOptions,
        _: Option<&'a FileSystemSandboxContext>,
    ) -> ExecutorFileSystemFuture<'a, ()> {
        Box::pin(async move {
            if options.recursive {
                return Err(io::Error::new(
                    io::ErrorKind::Unsupported,
                    "patch check does not support recursive removal",
                ));
            }
            let location = self.locate(
                path.to_abs_path()?.as_path(),
                options.follow_symlinks,
                false,
            )?;
            let node = self.node(&location.path)?;
            match node {
                Node::Missing if options.force => return Ok(()),
                Node::Missing => return Err(io::Error::from(io::ErrorKind::NotFound)),
                Node::Directory => {
                    return Err(io::Error::new(
                        io::ErrorKind::InvalidInput,
                        "path is a directory",
                    ));
                }
                _ => {}
            }
            let preview = if self.preview.is_some() {
                Some(Change {
                    path: location.path.clone(),
                    before: self.preview_content(&location.path, &node).await?,
                    after: Content::Missing,
                })
            } else {
                None
            };
            self.save(location.path, Node::Missing)?;
            self.record_preview(preview)
        })
    }
}
