mod arguments;
mod client;

use arguments::{Arguments, HELP, Request, parse};
use std::io::{self, Write};
use std::process::ExitCode;

fn run(request: &Request) -> Result<bool, String> {
    let socket = std::env::var("OPERIT_WEB_CHAT_SOCKET")
        .map_err(|_| "请从 Operit 自带终端使用 web-chat；当前缺少本地控制通道配置")?;
    let response = client::exchange(&socket, request)?;
    if response.success {
        writeln!(io::stdout().lock(), "{}", response.output).map_err(|error| error.to_string())?;
    } else {
        writeln!(io::stderr().lock(), "{}", response.output).map_err(|error| error.to_string())?;
    }
    Ok(response.success)
}

fn main() -> ExitCode {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let request = match parse(&args) {
        Ok(Arguments::Help) => {
            println!("{HELP}");
            return ExitCode::SUCCESS;
        }
        Ok(Arguments::Version) => {
            println!("web-chat {}", env!("CARGO_PKG_VERSION"));
            return ExitCode::SUCCESS;
        }
        Err(error) => {
            eprintln!("{error}");
            return ExitCode::from(2);
        }
        Ok(Arguments::Command(request)) => request,
    };
    match run(&request) {
        Ok(true) => ExitCode::SUCCESS,
        Ok(false) => ExitCode::FAILURE,
        Err(error) => {
            eprintln!("{error}");
            ExitCode::FAILURE
        }
    }
}
