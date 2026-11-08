---
repo: https://github.com/3316891527/Operit
status: IMPLEMENTED_PENDING_VALIDATION
---

# 过期媒体标签与用户消息对齐

Debug 实测发现，气泡样式的过期图片和音频标签越过正文右边界，占用了头像列；过期音频还跳过了整条消息行，头像随之消失。

图片组右边界需要预留头像及间距；过期音频只替换气泡内容，继续使用原有消息行的头像和姓名。检查普通与宽屏气泡、头像开关、正常媒体及游标样式。

范围：BubbleUserMessageComposable、SentImageGroup，保持过期提示与数字角标。实现及回归结果见 [实施记录](1-implementation.md)。
