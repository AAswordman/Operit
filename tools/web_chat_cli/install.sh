#!/bin/bash
set -euo pipefail

if [ "$#" -ne 4 ]; then
    echo 'web-chat 安装参数不完整' >&2
    exit 2
fi
payload=$1
expected_sha=$2
command_dir=$3
socket_name=$4
case "$command_dir" in
    /*) ;;
    *) echo 'web-chat 安装目录必须是绝对路径' >&2; exit 2 ;;
esac
[[ "$expected_sha" =~ ^[0-9a-f]{64}$ ]] || { echo 'web-chat 校验值无效' >&2; exit 2; }
[[ "$socket_name" =~ ^operit\.web-chat\.[0-9]+$ ]] || { echo 'web-chat 通道名称无效' >&2; exit 2; }
printf '%s  %s\n' "$expected_sha" "$payload" | sha256sum --check --status
mkdir -p -- "$command_dir"
lock_file="$command_dir/.operit-web-chat.install.lock"
exec 9>"$lock_file"
flock --exclusive 9
set_host_owner() {
    if [[ -n "${OPERIT_UID:-}" && -n "${OPERIT_GID:-}" ]]; then
        chown -- "$OPERIT_UID:$OPERIT_GID" "$@"
    fi
}
set_host_owner "$lock_file"
destination="$command_dir/.web-chat-bin-$expected_sha"
staging=$(mktemp "$command_dir/.web-chat-install.XXXXXXXX")
trap 'rm -f -- "$staging"' EXIT
if [ ! -f "$destination" ] || [ -L "$destination" ] ||
    ! printf '%s  %s\n' "$expected_sha" "$destination" | sha256sum --check --status; then
    cp -- "$payload" "$staging"
    printf '%s  %s\n' "$expected_sha" "$staging" | sha256sum --check --status
    set_host_owner "$staging"
    chmod 0755 -- "$staging"
    mv --force --no-target-directory -- "$staging" "$destination"
fi
set_host_owner "$destination"
chmod 0755 -- "$destination"
# 包装命令记录宿主 UID 的通道名，避免 proot 的虚拟 root 身份影响寻址。
printf '#!/bin/bash\nOPERIT_WEB_CHAT_SOCKET=%q exec %q "$@"\n' "$socket_name" "$destination" > "$staging"
set_host_owner "$staging"
chmod 0755 -- "$staging"
if [ -f "$command_dir/web-chat" ] && [ ! -L "$command_dir/web-chat" ] && cmp --silent -- "$staging" "$command_dir/web-chat"; then
    rm -- "$staging"
else
    mv --force --no-target-directory -- "$staging" "$command_dir/web-chat"
fi
# 新包装命令已就绪后清理旧二进制，已运行进程不受文件删除影响。
for old in "$command_dir"/.web-chat-bin-*; do
    if [ "$old" != "$destination" ] && [ -f "$old" ]; then rm -- "$old"; fi
done
trap - EXIT
