#!/bin/bash
set -euo pipefail

if [ "$#" -ne 3 ]; then
    echo 'apply_patch 安装参数不完整' >&2
    exit 2
fi

payload=$1
expected_sha=$2
command_dir=$3

case "$command_dir" in
    /*) ;;
    *) echo 'apply_patch 安装目录必须是绝对路径' >&2; exit 2 ;;
esac
if [[ ! "$expected_sha" =~ ^[0-9a-f]{64}$ ]]; then
    echo 'apply_patch 校验值格式错误' >&2
    exit 2
fi

# 在修改已安装命令前验证来源，校验失败立即终止本次登录。
printf '%s  %s\n' "$expected_sha" "$payload" | sha256sum --check --status
mkdir -p -- "$command_dir"
lock_file="$command_dir/.operit-apply-patch.install.lock"
exec 9>"$lock_file"
flock --exclusive 9

# chroot 会以真实 root 写入；恢复宿主归属后，普通 proot 会话才能继续升级。
set_host_owner() {
    if [[ -n "${OPERIT_UID:-}" && -n "${OPERIT_GID:-}" ]]; then
        chown -- "$OPERIT_UID:$OPERIT_GID" "$@"
    fi
}
set_host_owner "$lock_file"

destination="$command_dir/apply_patch"
if [ -f "$destination" ] && [ ! -L "$destination" ] &&
    printf '%s  %s\n' "$expected_sha" "$destination" | sha256sum --check --status; then
    set_host_owner "$destination"
    chmod 0755 -- "$destination"
    exit 0
fi

# 临时文件与目标位于同一目录，完整校验后一次替换，避免并发会话读取半个文件。
staging=$(mktemp "$command_dir/.apply_patch.XXXXXXXX")
trap 'rm -f -- "$staging"' EXIT
cp -- "$payload" "$staging"
printf '%s  %s\n' "$expected_sha" "$staging" | sha256sum --check --status
set_host_owner "$staging"
chmod 0755 -- "$staging"
mv --force --no-target-directory -- "$staging" "$destination"
trap - EXIT
