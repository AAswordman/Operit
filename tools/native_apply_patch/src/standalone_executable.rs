// 命令行扩展位于独立模块，保留原版对外入口名称。
pub fn main() -> ! {
    crate::cli::main()
}
