package com.ai.assistance.operit.api.chat.error

import java.io.IOException

/** 应用主动发现的问题在发生位置确定类型，错误文案不参与分类。 */
internal open class RequestFailureException(
    val failureCode: RequestFailureCode,
    val detail: String? = null,
    cause: Throwable? = null,
) : IOException(failureCode.explanation, cause)

internal class FirstTokenTimeoutException(
    message: String = "Waiting for the first token timed out",
    cause: Throwable? = null,
) : RequestFailureException(RequestFailureCode.FIRST_TOKEN_TIMEOUT, message, cause)

/** 服务端业务编号保持原样，不能伪装成本地网络编号。 */
internal class ProviderResponseException(
    val provider: String,
    val providerCode: String?,
    val providerType: String?,
    message: String,
) : IOException(message)

internal class RequestFailureContextException(
    cause: Exception,
    val requestContext: RequestFailureContext,
) : IOException(cause.message, cause)
