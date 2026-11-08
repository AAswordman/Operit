# Lucide 图标数据

- 版本：`lucide-static@0.456.0`，1535 个图标。
- 官方包：<https://registry.npmjs.org/lucide-static/-/lucide-static-0.456.0.tgz>
- 完整 SHA-512 固定于 `generate_lucide.py`，生成前校验；不运行 npm 脚本。
- 许可：ISC，部分图标源自 Feather（MIT）。Lucide 包内许可证及
  [Feather v4.29.2 LICENSE](https://github.com/feathericons/feather/blob/v4.29.2/LICENSE)
  保存在 `app/src/main/assets/licenses/`，随 APK 分发。

下载上述归档到仓库外后执行：

```sh
python tools/icons/generate_lucide.py path/to/lucide-static-0.456.0.tgz
```

生成器只读取归档内 SVG 与关键词，转换为 Compose 路径数据；每个 SVG 元素
保留独立路径，避免相对起点串到上一元素。只接受已实现的基础元素和描边属性。
生成 Kotlin 数据分块，防止超过 JVM 字符串常量长度；不增加 SVG 运行库。

修改生成器或升级归档后，运行 `CardIconsTest`（全库路径解析、契约、搜索与兼容性）
及行内指令测试，并检查生成差异和许可证。
