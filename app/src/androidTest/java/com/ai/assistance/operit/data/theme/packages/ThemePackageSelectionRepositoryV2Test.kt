package com.ai.assistance.operit.data.theme.packages

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePackageSelectionRepositoryV2Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = ThemePackageSelectionRepositoryV2.getInstance(context)

    @Before
    fun resetSelection() = runBlocking {
        repository.replace(ThemeInstanceV2.defaultBundled())
    }

    @After
    fun restoreSelection() = runBlocking {
        repository.replace(ThemeInstanceV2.defaultBundled())
    }

    @Test
    fun missingExternalSelectionIsAtomicallyReplacedWithBundledDefault() = runBlocking {
        val missing = coordinate("operit.cyber_grid", "a")
        repository.replace(
            ThemeInstanceV2(
                reference = ThemePackageReferenceV2(missing),
                parameterValues =
                    mapOf(
                        ThemePackageParameterIdsV2.BACKGROUND_IMAGE to
                            ThemeParameterValueV2.StringValue("content://theme/background"),
                    ),
            ),
        )

        val repairedFrom = repository.repairUnavailableSelection(setOf(ThemePackageDefaultV2.coordinate))

        assertEquals(missing, repairedFrom)
        assertEquals(ThemeInstanceV2.defaultBundled(), repository.selectionFlow.first())
    }

    @Test
    fun installedExternalSelectionIsPreserved() = runBlocking {
        val installed = coordinate("operit.cyber_grid", "b")
        val selection =
            ThemeInstanceV2(
                reference = ThemePackageReferenceV2(installed),
                variantId = ThemeVariantIdV2("night"),
            )
        repository.replace(selection)

        val repairedFrom = repository.repairUnavailableSelection(setOf(ThemePackageDefaultV2.coordinate, installed))

        assertEquals(null, repairedFrom)
        assertEquals(selection, repository.selectionFlow.first())
    }

    private fun coordinate(packageId: String, digestSeed: String): ThemePackageCoordinateV2 =
        ThemePackageCoordinateV2(
            packageId = ThemePackageIdV2(packageId),
            version = ThemePackageVersionV2("2.1.0"),
            archiveSha256 = ThemeArchiveSha256V2(digestSeed.repeat(64)),
        )
}
