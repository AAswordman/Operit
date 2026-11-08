use serde::Serialize;

pub const HELP: &str = "Operit HTTP / Web Chat / A2A 服务管理

用法：
  web-chat start-server [--port PORT] [--login]
  web-chat stop-server
  web-chat status
  web-chat token show
  web-chat token reset
  web-chat examples [sync|async|health|intent]
  web-chat --help
  web-chat --version

说明：
  在 Operit 自带终端中使用；端口默认为已保存配置，首次使用为 8094。
  start-server 输出带说明的 URL 和 Token；--login 打开默认浏览器并自动登录。
  token reset 更新 Token；普通启动继续使用已有 Token。
  examples 不带类型时依次输出同步、异步、健康检查和 Intent 示例，不发送请求。
  命令不会修改剪贴板。";

#[derive(Debug, PartialEq, Serialize)]
pub struct Request {
    pub version: u8,
    pub command: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub port: Option<u16>,
    pub login: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub example: Option<String>,
}

pub enum Arguments {
    Help,
    Version,
    Command(Request),
}

pub fn parse(args: &[String]) -> Result<Arguments, String> {
    if args.is_empty() || args == ["--help"] || args == ["-h"] {
        return Ok(Arguments::Help);
    }
    if args == ["--version"] {
        return Ok(Arguments::Version);
    }
    let mut request = Request {
        version: 1,
        command: args[0].clone(),
        port: None,
        login: false,
        example: None,
    };
    match args[0].as_str() {
        "start-server" => {
            let mut index = 1;
            while index < args.len() {
                match args[index].as_str() {
                    "--port" if request.port.is_none() => {
                        index += 1;
                        let raw = args.get(index).ok_or("--port 缺少端口值")?;
                        if raw.is_empty() || !raw.bytes().all(|byte| byte.is_ascii_digit()) {
                            return Err("端口必须为 1-65535 的整数".into());
                        }
                        let port: u16 = raw.parse().map_err(|_| "端口必须为 1-65535 的整数")?;
                        if port == 0 {
                            return Err("端口必须为 1-65535 的整数".into());
                        }
                        request.port = Some(port);
                    }
                    "--login" if !request.login => request.login = true,
                    option => return Err(format!("不支持或重复的参数：{option}")),
                }
                index += 1;
            }
        }
        "stop-server" | "status" if args.len() == 1 => {}
        "token" if args.len() == 2 => {
            request.command = match args[1].as_str() {
                "show" => "token-show",
                "reset" => "token-reset",
                _ => return Err("用法：web-chat token show|reset".into()),
            }
            .into();
        }
        "examples" if args.len() <= 2 => {
            if let Some(kind) = args.get(1) {
                if !matches!(kind.as_str(), "sync" | "async" | "health" | "intent") {
                    return Err("示例类型必须为 sync、async、health 或 intent".into());
                }
                request.example = Some(kind.clone());
            }
        }
        _ => return Err("命令或参数无效；使用 web-chat --help 查看帮助".into()),
    }
    Ok(Arguments::Command(request))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn command(args: &[&str]) -> Request {
        match parse(&args.iter().map(|value| (*value).into()).collect::<Vec<_>>()).unwrap() {
            Arguments::Command(request) => request,
            _ => panic!("应解析为服务命令"),
        }
    }

    #[test]
    fn port_boundaries_and_login_can_be_combined_in_either_order() {
        for args in [
            vec!["start-server", "--port", "1", "--login"],
            vec!["start-server", "--login", "--port", "65535"],
        ] {
            let request = command(&args);
            assert!(request.login);
            assert!(matches!(request.port, Some(1 | 65535)));
        }
        for raw in ["0", "65536", "-1", "+80", "8.0", "", "abc"] {
            assert!(parse(&["start-server".into(), "--port".into(), raw.into()]).is_err());
        }
    }

    #[test]
    fn unknown_duplicate_and_misplaced_flags_are_rejected() {
        for args in [
            vec!["start-server", "--port"],
            vec!["start-server", "--copy"],
            vec!["start-server", "--login", "--login"],
            vec!["start-server", "--port", "80", "--port", "81"],
            vec!["status", "--login"],
            vec!["stop-server", "--port", "80"],
            vec!["token", "reset", "extra"],
            vec!["examples", "other"],
            vec!["examples", "sync", "async"],
        ] {
            assert!(parse(&args.iter().map(|value| (*value).into()).collect::<Vec<_>>()).is_err());
        }
    }

    #[test]
    fn default_examples_and_token_actions_keep_the_wire_contract() {
        assert_eq!(command(&["examples"]).example, None);
        for kind in ["sync", "async", "health", "intent"] {
            assert_eq!(command(&["examples", kind]).example.as_deref(), Some(kind));
        }
        assert_eq!(command(&["token", "show"]).command, "token-show");
        assert_eq!(command(&["token", "reset"]).command, "token-reset");
        let wire = serde_json::to_value(command(&["start-server"])).unwrap();
        assert_eq!(
            wire,
            serde_json::json!({"version": 1, "command": "start-server", "login": false})
        );
    }
}
