# 赛博主题仓库与发布

## 仓库

- 名称：`luojiaping/operit-theme-cyber-grid`
- 包 ID：`operit.cyber_grid`
- 接入版本：`1.0.2`
- Release 标签：`v1.0.2`

## 内容

```text
operit-theme-cyber-grid/
	.github/workflows/release.yml
	scripts/package.sh
	assets/
	operit-theme.json
	ATTRIBUTION.md
	README.md
	LICENSE
```

仓库保留 Hubble Ring Nebula 图片来源和归因，以及由项目生成的三张九宫格框体。manifest 声明源像素 cap insets 与目标 dp cap insets，确保不同密度下框体保持正确。

Release workflow 生成 `operit-cyber-grid-<version>.otheme` 和对应 SHA-256 文件并发布。主应用不下载、不预装、不引用该仓库内容；用户从 Release 下载归档后在主题页显式导入。

## 完成条件

- Release asset 可由主应用的 `ThemePackageArchiveValidatorV1` 原样验证
- ZIP comment 为 `Operit Theme Package`
- manifest 中每个素材的 SHA-256 和字节数与归档内容一致
- 新增主题版本只需在该仓库变更、打 tag、发布 Release，无须修改主应用

[DONE] 仓库和 [`v1.0.2 Release`](https://github.com/luojiaping/operit-theme-cyber-grid/releases/tag/v1.0.2) 已创建；Release SHA-256 为 `cfbdf77a5a1ffab604f7cfd0ff2274f0492f41011ff6cc3783b2aeff9a3717a8`。
