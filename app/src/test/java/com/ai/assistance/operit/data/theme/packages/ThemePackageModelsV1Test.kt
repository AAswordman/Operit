package com.ai.assistance.operit.data.theme.packages

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ThemePackageModelsV1Test {
    private val json =
        kotlinx.serialization.json.Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
            explicitNulls = false
        }

    @Test
    fun defaultBundledRoundTripsThroughJson() {
        val instance = ThemeInstanceV1.defaultBundled()
        val encoded = json.encodeToString(instance)
        assertEquals(instance, json.decodeFromString<ThemeInstanceV1>(encoded))
    }

    @Test
    fun installedReferenceRoundTripsThroughJson() {
        val instance =
            ThemeInstanceV1(
                reference =
                    ThemePackageReferenceV1(
                        coordinate =
                            ThemePackageCoordinateV1(
                                packageId = ThemePackageIdV1("author.cyber_night"),
                                version = ThemePackageVersionV1("1.2.0"),
                                archiveSha256 =
                                    ThemeArchiveSha256V1(
                                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                                    ),
                            ),
                    ),
                variantId = ThemeVariantIdV1("neon"),
                parameterValues =
                    mapOf(
                        "accent" to ThemeParameterValueV1.StringValue("#00e5ff"),
                        "reduce_glow" to ThemeParameterValueV1.BooleanValue(false),
                    ),
            )
        val encoded = json.encodeToString(instance)
        assertEquals(instance, json.decodeFromString<ThemeInstanceV1>(encoded))
    }

    @Test
    fun invalidPackageIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ThemePackageIdV1("Operit") }
    }

    @Test
    fun invalidVersionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ThemePackageVersionV1("1.2") }
    }

    @Test
    fun invalidDigestIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ThemeArchiveSha256V1("0123456789ABCDEF")
        }
    }

    @Test
    fun invalidParameterKeyIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ThemeInstanceV1(
                reference = ThemeInstanceV1.defaultBundled().reference,
                parameterValues =
                    mapOf("Bad Key" to ThemeParameterValueV1.BooleanValue(true)),
            )
        }
    }
}
