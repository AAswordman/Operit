# 默认主题仓库

## 仓库

- 名称：`luojiaping/operit-theme-default`
- 包 ID：`operit.default`
- 接入版本：`1.0.2`
- Release 标签：`v1.0.2`

## 内容

```text
operit-theme-default/
	.github/workflows/release.yml
	scripts/package.sh
	operit-theme.json
	README.md
	LICENSE
```

默认主题公开 `primary_color` 和 `background_image` 参数，并用 `chat.main@1.0` 的标准槽位表达当前 Compose 聊天布局。它没有主题素材依赖。

`scripts/package.sh` 校验 manifest、打包根 `operit-theme.json`、写入标准 ZIP comment，并生成 `dist/operit-default-<version>.otheme` 和 SHA-256 文件。Release 工作流只在 `v*` 标签上把这两项作为 GitHub Release assets 上传。

## 宿主锁定

主应用 assets 中的 `operit-default.otheme` 必须与该仓库 Release artifact 字节一致。`ThemePackageDefaultV1` 固定其版本和 SHA-256。主题源仓库发生变更时，先创建新 Release，再将经过校验的 artifact 和 lock 信息更新到主应用。

[DONE] 仓库和 [`v1.0.2 Release`](https://github.com/luojiaping/operit-theme-default/releases/tag/v1.0.2) 已创建；Release SHA-256 为 `e4b6aad585f1a79854f9f3fdc18e06445002a6629d2140df25ab87e7972667ed`。
