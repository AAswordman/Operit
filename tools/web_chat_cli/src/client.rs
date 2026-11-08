use crate::arguments::Request;
use serde::Deserialize;
use std::io::{BufRead, BufReader, Read, Write};
use std::net::Shutdown;
use std::os::linux::net::SocketAddrExt;
use std::os::unix::net::{SocketAddr, UnixStream};
use std::time::Duration;

const MAX_REPLY: u64 = 65_536;

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Response {
    pub version: u8,
    pub success: bool,
    pub output: String,
}

pub fn exchange(socket_name: &str, request: &Request) -> Result<Response, String> {
    let address = SocketAddr::from_abstract_name(socket_name.as_bytes())
        .map_err(|error| error.to_string())?;
    let mut stream = UnixStream::connect_addr(&address)
        .map_err(|error| format!("无法连接 Operit 控制服务，请在 App 中重新打开终端：{error}"))?;
    let timeout = Some(Duration::from_secs(30));
    stream
        .set_read_timeout(timeout)
        .map_err(|error| error.to_string())?;
    stream
        .set_write_timeout(timeout)
        .map_err(|error| error.to_string())?;
    let mut data = serde_json::to_vec(request).map_err(|error| error.to_string())?;
    data.push(b'\n');
    stream.write_all(&data).map_err(|error| error.to_string())?;
    stream
        .shutdown(Shutdown::Write)
        .map_err(|error| error.to_string())?;
    let mut reply = Vec::new();
    BufReader::new(stream)
        .take(MAX_REPLY + 1)
        .read_until(b'\n', &mut reply)
        .map_err(|error| format!("读取服务结果失败：{error}"))?;
    if reply.len() as u64 > MAX_REPLY || reply.last() != Some(&b'\n') {
        return Err("服务返回的结果过大或不完整".into());
    }
    let response: Response =
        serde_json::from_slice(&reply).map_err(|error| format!("服务结果格式错误：{error}"))?;
    if response.version != 1 {
        return Err("终端命令与 App 控制协议不匹配，请重新打开终端更新命令".into());
    }
    Ok(response)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::os::unix::net::UnixListener;
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::thread;

    static NEXT_SOCKET: AtomicUsize = AtomicUsize::new(0);

    fn reply_with(bytes: Vec<u8>) -> Result<Response, String> {
        let name = format!(
            "operit-web-chat-test-{}-{}",
            std::process::id(),
            NEXT_SOCKET.fetch_add(1, Ordering::Relaxed)
        );
        let address = SocketAddr::from_abstract_name(name.as_bytes()).unwrap();
        let listener = UnixListener::bind_addr(&address).unwrap();
        let server = thread::spawn(move || {
            let (mut socket, _) = listener.accept().unwrap();
            let mut line = String::new();
            BufReader::new(socket.try_clone().unwrap())
                .read_line(&mut line)
                .unwrap();
            assert_eq!(
                serde_json::from_str::<serde_json::Value>(&line).unwrap()["command"],
                "status"
            );
            let _ = socket.write_all(&bytes);
        });
        let result = exchange(
            &name,
            &Request {
                version: 1,
                command: "status".into(),
                port: None,
                login: false,
                example: None,
            },
        );
        server.join().unwrap();
        result
    }

    #[test]
    fn success_and_service_failures_are_distinct() {
        let success =
            reply_with(b"{\"version\":1,\"success\":true,\"output\":\"running\"}\n".to_vec())
                .unwrap();
        assert!(success.success);
        assert_eq!(success.output, "running");
        let failure = reply_with(
            b"{\"version\":1,\"success\":false,\"output\":\"port occupied\"}\n".to_vec(),
        )
        .unwrap();
        assert!(!failure.success);
        assert_eq!(failure.output, "port occupied");
    }

    #[test]
    fn truncated_oversized_and_incompatible_replies_are_rejected() {
        assert!(reply_with(b"{\"version\":1}".to_vec()).is_err());
        assert!(reply_with(vec![b'x'; MAX_REPLY as usize + 1]).is_err());
        assert!(
            reply_with(b"{\"version\":2,\"success\":true,\"output\":\"\"}\n".to_vec()).is_err()
        );
    }
}
