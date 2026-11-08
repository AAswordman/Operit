---
fork: https://github.com/AAswordman/Operit
---

# 动态思考选项

当前版本尚未发布，因此移除全局固定五档思考强度契约。思考滑块改由当前
Provider、协议和模型返回的真实选项决定，Android、WebChat 和请求序列化
共同使用选项 id。

作用域：思考选项映射、设置持久化、Android/WebChat 滑块、Provider 请求参数。

Provider 映射与模型目录能力边界记录在 [03-provider-capability-mapping.md](03-provider-capability-mapping.md)。

