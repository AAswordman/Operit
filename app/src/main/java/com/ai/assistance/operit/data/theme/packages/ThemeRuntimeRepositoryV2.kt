package com.ai.assistance.operit.data.theme.packages

import android.content.Context
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-local immutable runtime index. Installation and application startup refresh this index
 * off the UI composition path; Compose only looks up already linked package data here.
 */
internal object ThemeRuntimeRepositoryV2 {
    private val snapshot = AtomicReference<Map<ThemePackageCoordinateV2, LinkedThemeRuntimeV2>>(emptyMap())

    fun refresh(context: Context) {
        val catalog = ThemePackageInstallerV2.getInstance(context).catalog()
        val linked =
            catalog.installations.associate { installation ->
                installation.coordinate to ThemePackageRuntimeLinkerV2.link(installation, catalog)
            }
        snapshot.set(linked)
    }

    fun require(coordinate: ThemePackageCoordinateV2): LinkedThemeRuntimeV2 =
        snapshot.get()[coordinate]
            ?: error("Active V2 theme package has not been linked: ${coordinate.packageId.value}")
}
