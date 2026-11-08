# HTTP 重试错误与顶部提示

继续使用 `feat/chat-batch-and-settings` 和 PR #18。错误提示与实际动作一致：结束请求时只报告失败，确实继续请求时保留重试次数和等待信息。

## 错误分类与呈现

- 余额不足与额度耗尽保留原有重试策略，用户可在等待期间补充额度；429 限流、网络中断、超时及服务端临时故障继续按原预算和退避时间重试
- API 密钥无效或过期不在同一把密钥上反复请求；识别 `API key is invalid`、`API key not valid`、`API_KEY_INVALID`、`API key has expired`、`invalid x-api-key` 等鉴权错误。没有其他候选密钥时直接结束，只显示最终错误；密钥池仍可切换到另一把候选密钥
- 非法、不支持、缺失的参数、无效参数值、请求正文 JSON 错误、未知字段及上下文长度超限直接结束。明确的参数错误即使包含 `model` 或 `not allowed`，也不会误判成可换钥的模型或鉴权故障；普通响应 JSON 解析错误沿用原重试流程
- 通用 `invalid_request_error` 与 `INVALID_ARGUMENT` 不是所有错误的终止依据，不能覆盖无效密钥的候选切换，也不能覆盖额度不足、限流、超时和服务端临时故障的重试策略

`ApiKeyRequestSupport.kt` 保留既有重试提示构造与请求回滚：只有重试决策接受后才发布重试次数，终止分类和耗尽分支在此前抛出最终错误。OpenAI、Gemini 与 Claude 沿用各自的真实重试文案；网络和首字超时保留原结构化元数据及 300 秒首字期限。

错误继续通过 `MessageProcessingDelegate.nonFatalErrorEvent` 送到 `MessageCoordinationDelegate`，使用窗口上方的 `OKToast.requestError`。本次调整没有修改重试次数预算、等待时长、余额不足策略、密钥选择算法或数据库格式。

## 验证记录

`ApiRequestRetryPolicyTest.kt` 扩充参数和密钥错误样本，保留余额不足、限流、网络、超时、临时服务故障与正常换钥的全部回归。`ApiKeyRetryNoticeTest.kt` 保留原有三个回归，新增三个场景：明确参数错误不构造重试提示、无其他密钥时鉴权失败直接结束、余额不足仍发布第 1 次重试并保留退避时间。

[完整验收清单](02-完整验收清单.md) 保留此前 348 项，新增两个终止错误场景及对应结果行，共 350 项。本地仅完成差异空白、调用点和清单完整性的静态检查，未执行编译、构建或测试。远端 Android Build 使用 `assembleDebug`，Android Tests 执行 JVM 回归；派发和结果记录在 PR，新错误分类与提示尚待构建产物复验。

[DONE]
