package com.ai.assistance.operit.core.tools.packTool

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class ToolPkgMarketOrigin(
    val market: String,
    val toolpkgId: String,
    val version: String,
    val author: List<String>
)

internal object ToolPkgMarketOriginCodec {
    private const val MARKET = "Operit"
    private const val XOR_KEY = 0x5a
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(origin: ToolPkgMarketOrigin): String {
        val encoded = encodeBytes(origin).joinToString(prefix = "[", postfix = "]")
        return "ToolPkg._m(${encoded},$XOR_KEY);"
    }

    fun encodeForMetadata(origin: ToolPkgMarketOrigin): String {
        return buildString {
            append("xor-v1:")
            append(encodeBytes(origin).joinToString(","))
        }
    }

    fun parse(specJson: String): ToolPkgMarketOrigin? {
        return runCatching {
            json.decodeFromString<ToolPkgMarketOrigin>(specJson)
        }
            .getOrNull()
    }

    fun decodeMetadata(value: String): ToolPkgMarketOrigin? {
        val encoded = value.trim().removePrefix("xor-v1:")
        if (!value.trim().startsWith("xor-v1:") || encoded.isBlank()) return null
        val bytes =
            encoded.split(',').map { item ->
                item.trim().toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            }
        val decoded = bytes.map { value -> (value xor XOR_KEY).toByte() }.toByteArray()
        return parse(decoded.toString(Charsets.US_ASCII))
    }

    fun validateForPackage(origin: ToolPkgMarketOrigin?, packageId: String): ToolPkgMarketOrigin? {
        val normalized = origin ?: return null
        if (normalized.market != MARKET) return null
        if (normalized.toolpkgId != packageId.trim()) return null
        if (normalized.version.isBlank()) return null
        return normalized.copy(
            toolpkgId = normalized.toolpkgId.trim(),
            version = normalized.version.trim(),
            author = normalized.author.map(String::trim).filter(String::isNotBlank)
        )
    }

    private fun escapeNonAscii(value: String): String {
        return buildString(value.length) {
            value.forEach { character ->
                if (character.code in 0x20..0x7e) {
                    append(character)
                } else {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                }
            }
        }
    }

    private fun encodeBytes(origin: ToolPkgMarketOrigin): List<Int> {
        val payload = json.encodeToString(origin)
        return escapeNonAscii(payload)
            .toByteArray(Charsets.US_ASCII)
            .map { byte -> (byte.toInt() and 0xff) xor XOR_KEY }
    }
}
