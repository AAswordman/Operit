package com.ai.assistance.operit.api.chat.transport

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/**
 * 为模型请求统一关闭 HTTPS 证书链与主机名校验。
 * 当前版本未发布，按新方案直接切换，不保留旧逻辑。
 */
internal object UnsafeModelSsl {
    fun apply(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        val trustManager =
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?
                ) {}

                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?
                ) {}

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())

        return builder
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
    }
}
