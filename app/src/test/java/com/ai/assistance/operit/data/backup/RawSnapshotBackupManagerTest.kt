package com.ai.assistance.operit.data.backup

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSnapshotBackupManagerTest {

    @Test
    fun snapshotPackageName_acceptsOperitPackagePrefix() {
        assertTrue(isSupportedSnapshotPackageName("com.ai.assistance.operit"))
        assertTrue(isSupportedSnapshotPackageName("com.ai.assistance.operit.debug"))
        assertTrue(isSupportedSnapshotPackageName("com.ai.assistance.operit.clone"))
    }

    @Test
    fun snapshotPackageName_rejectsDifferentPackagePrefix() {
        assertFalse(isSupportedSnapshotPackageName("com.ai.assistance.other"))
        assertFalse(isSupportedSnapshotPackageName("com.example.operit"))
        assertFalse(isSupportedSnapshotPackageName("com.ai.assistance.operitmalicious"))
    }

    @Test
    fun releasedFormat1Includes_requiresExactCategoryOrder() {
        assertTrue(
            hasExactReleasedFormat1Includes(RAW_SNAPSHOT_REQUIRED_PAYLOAD_DIRECTORIES)
        )
        assertFalse(
            hasExactReleasedFormat1Includes(
                RAW_SNAPSHOT_REQUIRED_PAYLOAD_DIRECTORIES.reversed()
            )
        )
        assertFalse(
            hasExactReleasedFormat1Includes(
                RAW_SNAPSHOT_REQUIRED_PAYLOAD_DIRECTORIES.dropLast(1)
            )
        )
    }

    @Test
    fun boundedByteAccounting_rejectsBeforeExceedingLimit() {
        assertEquals(
            RAW_SNAPSHOT_MAX_MANIFEST_BYTES,
            addRawSnapshotBytesWithinLimit(
                currentBytes = RAW_SNAPSHOT_MAX_MANIFEST_BYTES - 1L,
                additionalBytes = 1L,
                limitBytes = RAW_SNAPSHOT_MAX_MANIFEST_BYTES,
                description = "manifest"
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            addRawSnapshotBytesWithinLimit(
                currentBytes = RAW_SNAPSHOT_MAX_MANIFEST_BYTES - 1L,
                additionalBytes = 2L,
                limitBytes = RAW_SNAPSHOT_MAX_MANIFEST_BYTES,
                description = "manifest"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            addRawSnapshotBytesWithinLimit(
                currentBytes = Long.MAX_VALUE,
                additionalBytes = 1L,
                limitBytes = Long.MAX_VALUE,
                description = "archive"
            )
        }
    }

    @Test
    fun payloadInventory_acceptsExplicitEmptyRequiredCategories() {
        validateRawSnapshotPayloadInventory(
            manifestDirectories = RAW_SNAPSHOT_REQUIRED_PAYLOAD_DIRECTORIES,
            manifestFiles = emptyList(),
            observedEntries = requiredDirectoryEntries()
        )
    }

    @Test
    fun payloadInventory_acceptsExactFileMetadata() {
        val file = manifestFile("payload/files/preferences.pb", byteLength = 12L)

        validateRawSnapshotPayloadInventory(
            manifestDirectories = RAW_SNAPSHOT_REQUIRED_PAYLOAD_DIRECTORIES,
            manifestFiles = listOf(file),
            observedEntries = requiredDirectoryEntries() + observedFile(file)
        )
    }

    @Test
    fun payloadInventory_rejectsMissingRequiredCategoryEntry() {
        assertInventoryRejected(
            manifestFiles = emptyList(),
            observedEntries = requiredDirectoryEntries().dropLast(1)
        )
    }

    @Test
    fun payloadInventory_rejectsIncompleteManifestDirectoryList() {
        assertThrows(IllegalArgumentException::class.java) {
            validateRawSnapshotPayloadInventory(
                manifestDirectories = RAW_SNAPSHOT_REQUIRED_PAYLOAD_DIRECTORIES.dropLast(1),
                manifestFiles = emptyList(),
                observedEntries = requiredDirectoryEntries()
            )
        }
    }

    @Test
    fun payloadInventory_rejectsMissingListedFile() {
        assertInventoryRejected(
            manifestFiles = listOf(manifestFile("payload/datastore/settings.preferences_pb")),
            observedEntries = requiredDirectoryEntries()
        )
    }

    @Test
    fun payloadInventory_rejectsUnlistedPayloadFile() {
        val unlisted = manifestFile("payload/external_files/unlisted.txt")

        assertInventoryRejected(
            manifestFiles = emptyList(),
            observedEntries = requiredDirectoryEntries() + observedFile(unlisted)
        )
    }

    @Test
    fun payloadInventory_rejectsDuplicateManifestFile() {
        val duplicate = manifestFile("payload/files/duplicate.bin")

        assertInventoryRejected(
            manifestFiles = listOf(duplicate, duplicate),
            observedEntries = requiredDirectoryEntries() + observedFile(duplicate)
        )
    }

    @Test
    fun payloadInventory_rejectsDuplicateArchivePath() {
        val file = manifestFile("payload/shared_prefs/settings.xml")
        val observed = observedFile(file)

        assertInventoryRejected(
            manifestFiles = listOf(file),
            observedEntries = requiredDirectoryEntries() + listOf(observed, observed)
        )
    }

    @Test
    fun payloadInventory_rejectsNonRegularPayloadPath() {
        assertInventoryRejected(
            manifestFiles = emptyList(),
            observedEntries =
                requiredDirectoryEntries() +
                    RawSnapshotObservedPayloadEntry(
                        zipPath = "payload/files/device",
                        kind = RawSnapshotPayloadEntryKind.NON_REGULAR,
                        byteLength = 0L,
                        sha256 = ""
                    )
        )
    }

    @Test
    fun payloadInventory_rejectsSizeMismatch() {
        val file = manifestFile("payload/databases/operit_database", byteLength = 20L)

        assertInventoryRejected(
            manifestFiles = listOf(file),
            observedEntries =
                requiredDirectoryEntries() + observedFile(file.copy(byteLength = 19L))
        )
    }

    @Test
    fun payloadInventory_rejectsSha256Mismatch() {
        val file = manifestFile("payload/files/objectbox/data.mdb")

        assertInventoryRejected(
            manifestFiles = listOf(file),
            observedEntries =
                requiredDirectoryEntries() + observedFile(file.copy(sha256 = HASH_B))
        )
    }

    @Test
    fun payloadInventory_rejectsNonDeterministicManifestOrder() {
        val first = manifestFile("payload/files/a")
        val second = manifestFile("payload/files/b")

        assertInventoryRejected(
            manifestFiles = listOf(second, first),
            observedEntries =
                requiredDirectoryEntries() + listOf(observedFile(first), observedFile(second))
        )
    }

    @Test
    fun payloadInventory_rejectsTraversalPath() {
        assertInventoryRejected(
            manifestFiles = listOf(manifestFile("payload/files/../databases/operit_database")),
            observedEntries = requiredDirectoryEntries()
        )
    }

    @Test
    fun replacementSource_requiresExistingDirectory() {
        val root = Files.createTempDirectory("raw_snapshot_source_test").toFile()
        try {
            val missing = File(root, "missing")
            assertThrows(IllegalArgumentException::class.java) {
                requireRawSnapshotReplacementSourceDirectory(missing)
            }

            val regularFile = File(root, "file").apply { writeText("data") }
            assertThrows(IllegalArgumentException::class.java) {
                requireRawSnapshotReplacementSourceDirectory(regularFile)
            }

            val directory = File(root, "directory")
            assertTrue(directory.mkdir())
            requireRawSnapshotReplacementSourceDirectory(directory)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun quarantineManifest_distinguishesAbsentAndExistingEmptyCategories() {
        val manifest =
            quarantineManifest(
                mapOf(
                    RawSnapshotRestoreCategory.FILES to
                        quarantineCategory(
                            RawSnapshotRestoreCategory.FILES,
                            originallyExisted = true
                        ),
                    RawSnapshotRestoreCategory.EXTERNAL_FILES to
                        quarantineCategory(
                            RawSnapshotRestoreCategory.EXTERNAL_FILES,
                            originallyExisted = false
                        )
                )
            )

        validateRawSnapshotQuarantineManifest(manifest)
        assertTrue(manifest.categories.first().originallyExisted)
        assertFalse(manifest.categories[1].originallyExisted)
    }

    @Test
    fun quarantineManifest_acceptsCompleteNestedDirectoryInventory() {
        val filesState =
            quarantineCategory(
                category = RawSnapshotRestoreCategory.FILES,
                originallyExisted = true,
                directories = listOf("nested", "nested/empty"),
                files =
                    listOf(
                        RawSnapshotQuarantineFileState(
                            relativePath = "nested/value.bin",
                            byteLength = 4L,
                            sha256 = HASH_A
                        )
                    )
            )

        validateRawSnapshotQuarantineManifest(
            quarantineManifest(mapOf(RawSnapshotRestoreCategory.FILES to filesState))
        )
    }

    @Test
    fun quarantineManifest_rejectsEntriesForAbsentCategory() {
        val invalid =
            quarantineCategory(
                category = RawSnapshotRestoreCategory.DATASTORE,
                originallyExisted = false,
                directories = listOf("unexpected")
            )

        assertThrows(IllegalArgumentException::class.java) {
            validateRawSnapshotQuarantineManifest(
                quarantineManifest(mapOf(RawSnapshotRestoreCategory.DATASTORE to invalid))
            )
        }
    }

    @Test
    fun quarantineManifest_rejectsMissingParentDirectory() {
        val invalid =
            quarantineCategory(
                category = RawSnapshotRestoreCategory.DATABASES,
                originallyExisted = true,
                files =
                    listOf(
                        RawSnapshotQuarantineFileState(
                            relativePath = "nested/database.db",
                            byteLength = 8L,
                            sha256 = HASH_A
                        )
                    )
            )

        assertThrows(IllegalArgumentException::class.java) {
            validateRawSnapshotQuarantineManifest(
                quarantineManifest(mapOf(RawSnapshotRestoreCategory.DATABASES to invalid))
            )
        }
    }

    @Test
    fun transactionMarker_acceptsDerivedBasenames() {
        RawSnapshotTransactionMarkerState.entries.forEach { state ->
            validateRawSnapshotTransactionMarker(transactionMarker(state))
        }
    }

    @Test
    fun transactionMarker_rejectsInvalidNamesAndTransactionId() {
        val prepared = transactionMarker(RawSnapshotTransactionMarkerState.PREPARED)
        assertThrows(IllegalArgumentException::class.java) {
            validateRawSnapshotTransactionMarker(
                prepared.copy(transactionId = "../transaction")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateRawSnapshotTransactionMarker(
                prepared.copy(quarantineDirectoryBasename = "../quarantine")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateRawSnapshotTransactionMarker(
                prepared.copy(recoveryEpochArchiveBasename = "recovery_epoch_other")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateRawSnapshotTransactionMarker(prepared.copy(formatVersion = 2))
        }
    }

    @Test
    fun transactionMarker_statesChooseRecoveryOrRemoval() {
        assertEquals(
            RawSnapshotTransactionRecoveryDecision.RESTORE_PREVIOUS_EPOCH,
            rawSnapshotTransactionRecoveryDecision(RawSnapshotTransactionMarkerState.PREPARED)
        )
        assertEquals(
            RawSnapshotTransactionRecoveryDecision.RESTORE_PREVIOUS_EPOCH,
            rawSnapshotTransactionRecoveryDecision(
                RawSnapshotTransactionMarkerState.MUTATION_STARTED
            )
        )
        assertEquals(
            RawSnapshotTransactionRecoveryDecision.REMOVE_COMMITTED_MARKER,
            rawSnapshotTransactionRecoveryDecision(RawSnapshotTransactionMarkerState.COMMITTED)
        )
    }

    @Test
    fun transactionMarker_transitionsAreStrictlyForward() {
        assertTrue(
            isValidRawSnapshotMarkerTransition(
                RawSnapshotTransactionMarkerState.PREPARED,
                RawSnapshotTransactionMarkerState.MUTATION_STARTED
            )
        )
        assertTrue(
            isValidRawSnapshotMarkerTransition(
                RawSnapshotTransactionMarkerState.MUTATION_STARTED,
                RawSnapshotTransactionMarkerState.COMMITTED
            )
        )
        assertFalse(
            isValidRawSnapshotMarkerTransition(
                RawSnapshotTransactionMarkerState.PREPARED,
                RawSnapshotTransactionMarkerState.COMMITTED
            )
        )
        assertFalse(
            isValidRawSnapshotMarkerTransition(
                RawSnapshotTransactionMarkerState.COMMITTED,
                RawSnapshotTransactionMarkerState.MUTATION_STARTED
            )
        )
        assertEquals(
            RAW_SNAPSHOT_REPLACEMENT_CATEGORY_ORDER.asReversed(),
            rawSnapshotRollbackCategoryOrder()
        )
    }

    @Test
    fun rollbackException_preservesOriginalAndRollbackEvidence() {
        val original = IllegalStateException("replacement failed")
        val rollbackEvidence = IllegalStateException("databases rollback failed")

        val failure =
            createRawSnapshotRestoreRollbackException(
                originalFailure = original,
                rollbackFailures = listOf(rollbackEvidence)
            )

        assertSame(original, failure.cause)
        assertEquals(1, failure.suppressed.size)
        assertSame(rollbackEvidence, failure.suppressed.single())
    }

    private fun assertInventoryRejected(
        manifestFiles: List<RawSnapshotPayloadFile>,
        observedEntries: List<RawSnapshotObservedPayloadEntry>
    ) {
        assertThrows(IllegalArgumentException::class.java) {
            validateRawSnapshotPayloadInventory(
                manifestDirectories = RAW_SNAPSHOT_REQUIRED_PAYLOAD_DIRECTORIES,
                manifestFiles = manifestFiles,
                observedEntries = observedEntries
            )
        }
    }

    private fun requiredDirectoryEntries(): List<RawSnapshotObservedPayloadEntry> =
        RAW_SNAPSHOT_REQUIRED_PAYLOAD_DIRECTORIES.map { directory ->
            RawSnapshotObservedPayloadEntry(
                zipPath = directory,
                kind = RawSnapshotPayloadEntryKind.DIRECTORY,
                byteLength = 0L,
                sha256 = ""
            )
        }

    private fun manifestFile(
        zipPath: String,
        byteLength: Long = 1L,
        sha256: String = HASH_A
    ): RawSnapshotPayloadFile =
        RawSnapshotPayloadFile(
            zipPath = zipPath,
            byteLength = byteLength,
            sha256 = sha256
        )

    private fun observedFile(file: RawSnapshotPayloadFile): RawSnapshotObservedPayloadEntry =
        RawSnapshotObservedPayloadEntry(
            zipPath = file.zipPath,
            kind = RawSnapshotPayloadEntryKind.REGULAR_FILE,
            byteLength = file.byteLength,
            sha256 = file.sha256
        )

    private fun quarantineManifest(
        overrides: Map<RawSnapshotRestoreCategory, RawSnapshotQuarantineCategoryState> = emptyMap()
    ): RawSnapshotQuarantineManifest =
        RawSnapshotQuarantineManifest(
            formatVersion = 1,
            createdAt = 1L,
            categories =
                RAW_SNAPSHOT_REPLACEMENT_CATEGORY_ORDER.map { category ->
                    overrides[category]
                        ?: quarantineCategory(category, originallyExisted = false)
                }
        )

    private fun quarantineCategory(
        category: RawSnapshotRestoreCategory,
        originallyExisted: Boolean,
        directories: List<String> = emptyList(),
        files: List<RawSnapshotQuarantineFileState> = emptyList()
    ): RawSnapshotQuarantineCategoryState =
        RawSnapshotQuarantineCategoryState(
            category = category,
            originallyExisted = originallyExisted,
            directories = directories,
            files = files
        )

    private fun transactionMarker(
        state: RawSnapshotTransactionMarkerState
    ): RawSnapshotTransactionMarker =
        RawSnapshotTransactionMarker(
            formatVersion = RAW_SNAPSHOT_TRANSACTION_MARKER_FORMAT_VERSION,
            transactionId = TRANSACTION_ID,
            quarantineDirectoryBasename = rawSnapshotQuarantineBasename(TRANSACTION_ID),
            recoveryEpochArchiveBasename =
                rawSnapshotRecoveryEpochArchiveBasename(TRANSACTION_ID),
            state = state
        )

    private companion object {
        const val TRANSACTION_ID = "0123456789abcdef0123456789abcdef"
        const val HASH_A =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val HASH_B =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
