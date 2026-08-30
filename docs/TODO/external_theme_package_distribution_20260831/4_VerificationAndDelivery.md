# 验证与交付

## 宿主验证

1. 归档校验测试读取 `operit-default.otheme`，确认其 package ID、版本、坐标和 ZIP comment
2. 主题选择模型只序列化 installed reference
3. 默认主题安装任务校验归档摘要并发布到内容寻址目录
4. 赛博源、归档、启动预装代码与不可卸载限制均不在主应用中

## 主题仓库验证

1. `scripts/package.sh` 校验所有声明素材存在、大小和摘要一致
2. 脚本只从仓库根打包允许的 manifest、attribution 和素材
3. tag 工作流生成 `.otheme` 和 `.sha256`，并把它们作为同名 Release assets

## 设备验证

1. 清数据启动后默认主题正常安装并成为当前主题
2. 默认主题的主色和背景图参数可编辑
3. 从赛博主题 Release 下载 `.otheme`，在主题页导入、启用、停用和卸载
4. 赛博聊天场景检查背景、九宫格、滚动、IME、流式消息和 TalkBack
