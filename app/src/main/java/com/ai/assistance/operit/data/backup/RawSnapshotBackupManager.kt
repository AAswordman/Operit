package com.ai.assistance.operit.data.backup

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.stats.TokenUsageRepository
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.OperitPaths
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val SNAPSHOT_PACKAGE_NAME_PREFIX = "com.ai.assistance.operit"

internal fun isSupportedSnapshotPackageName(packageName: String): Boolean =
    packageName.startsWith(SNAPSHOT_PACKAGE_NAME_PREFIX)

object RawSnapshotBackupManager {

    private const val TAG = "RawSnapshotBackup"
    private const val FORMAT_VERSION = 1
    private const val ZIP_PREFIX = "operit_raw_snapshot_"

    private const val ENTRY_MANIFEST = "manifest.json"
    private const val ENTRY_PAYLOAD_PREFIX = "payload/"

    private const val ENTRY_FILES = "payload/files/"
    private const val ENTRY_EXTERNAL_FILES = "payload/external_files/"
    private const val ENTRY_SHARED_PREFS = "payload/shared_prefs/"
    private const val ENTRY_DATASTORE = "payload/datastore/"
    private const val ENTRY_DATABASES = "payload/databases/"

    private val terminalTopLevelDirNames = setOf("usr", "tmp", "bin")
    private val logFilesTopLevelDirNames = setOf("logs")
    private val logExternalTopLevelDirNames = setOf("anr_reports")

    private const val RESTORE_ROOT_APP_DATA = "app_data"
    private const val RESTORE_ROOT_EXTERNAL_FILES = "external_files"

    // Theme assets (avatars / background / bubble image / fonts) picked by the user are persisted
    // by FileUtils.copyFileToInternalStorage() as flat files in filesDir root, named
    // "<kind>_<UUID>.<ext>" (or "avatar_<id>_<UUID>", "group_avatar_<id>_<UUID>"). Only these
    // prefixes are candidates when pruning unreferenced historical media from a raw snapshot.
    private val themeMediaFlatNamePrefixes = listOf(
        "background",
        "bubble_ai",
        "bubble_user",
        "custom_font",
        "user_avatar",
        "ai_avatar",
        "global_user_avatar",
        "avatar_",
        "group_avatar_",
    )

    private val mainHandler = Handler(Looper.getMainLooper())

    @Serializable
    data class ResourceMapping(
        val ownerType: String,
        val kind: String,
        val ownerId: String? = null,
        val ownerName: String? = null,
        val originalUri: String,
        val snapshotPath: String,
        val restoreRoot: String? = null,
        val restoreRelativePath: String? = null,
    )

    @Serializable
    data class Manifest(
        val formatVersion: Int,
        val packageName: String,
        val createdAt: Long,
        val includes: List<String>,
        val includeTerminalData: Boolean = true,
        val includeLogs: Boolean = true,
        val resources: List<ResourceMapping> = emptyList(),
    )

    data class PreparedImageResource(
        val source: File,
        val mapping: ResourceMapping,
    )

    private data class ResourceRestorePlan(
        val mapping: ResourceMapping,
        val snapshotFile: File,
        val destination: File,
        val destinationUri: String,
    )

    private data class RestoreLocation(
        val root: String,
        val relativePath: String,
    )

    data class SnapshotOptions(
        val includeTerminalData: Boolean = false,
        val includeLogs: Boolean = true,
    )

    enum class ExportProgress {
        PREPARING,
        SCANNING_FILES,
        ZIPPING_FILES,
        ZIPPING_EXTERNAL_FILES,
        ZIPPING_SHARED_PREFS,
        ZIPPING_DATASTORE,
        ZIPPING_DATABASES,
        FINALIZING
    }

    data class ExportProgressInfo(
        val stage: ExportProgress,
        val percent: Int? = null,
        val scannedFiles: Int? = null
    )

    enum class RestoreProgress {
        PREPARING,
        READING_ZIP,
        EXTRACTING,
        REPLACING_FILES,
        REPLACING_EXTERNAL_FILES,
        REPLACING_SHARED_PREFS,
        REPLACING_DATASTORE,
        REPLACING_DATABASES,
        FINALIZING
    }

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun exportToBackupDir(
        context: Context,
        options: SnapshotOptions = SnapshotOptions(),
        onProgress: ((ExportProgressInfo) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        TokenUsageRepository.withDatabaseAccess {
            AppLogger.i(TAG, "export start (includeTerminalData=${options.includeTerminalData})")
            withContext(Dispatchers.Main) { onProgress?.invoke(ExportProgressInfo(ExportProgress.PREPARING)) }
            val exportDir = OperitBackupDirs.rawSnapshotDir()
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            val outFile = File(exportDir, "$ZIP_PREFIX$timestamp.zip")
            val tmpFile = File(exportDir, "${outFile.name}.tmp")

            if (tmpFile.exists()) {
                tmpFile.delete()
            }

            val dataDir = context.dataDir
            val externalFilesDir = requireNotNull(context.getExternalFilesDir(null)) {
                "External files dir is unavailable"
            }
            val sharedPrefsDir = File(dataDir, "shared_prefs")
            val datastoreDir = File(dataDir, "datastore")
            val databasesDir = File(dataDir, "databases")
            val resourceReferences = DefaultRawSnapshotResourceReferenceProvider(context).collectReferences()
            val preparedResources = prepareImageResources(
                context = context,
                references = resourceReferences,
            )
            val referencedImagePaths = preparedResources
                .mapNotNull { it.source.canonicalFile.path }
                .toSet()

            try {
                val sqliteDb = AppDatabase.getDatabase(context).openHelper.writableDatabase
                sqliteDb.query("PRAGMA wal_checkpoint(FULL)").close()
            } catch (e: Exception) {
                AppLogger.w(TAG, "wal_checkpoint failed", e)
            }

            val includes = listOf(
                ENTRY_FILES,
                ENTRY_EXTERNAL_FILES,
                ENTRY_SHARED_PREFS,
                ENTRY_DATASTORE,
                ENTRY_DATABASES,
                RawSnapshotResourceLayout.ROOT,
            )
            val manifest = Manifest(
                formatVersion = FORMAT_VERSION,
                packageName = context.packageName,
                createdAt = System.currentTimeMillis(),
                includes = includes,
                includeTerminalData = options.includeTerminalData,
                includeLogs = options.includeLogs,
                resources = preparedResources.map { it.mapping },
            )

            ZipOutputStream(BufferedOutputStream(FileOutputStream(tmpFile))).use { zos ->
                zos.putNextEntry(ZipEntry(ENTRY_MANIFEST))
                zos.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                if (preparedResources.isNotEmpty()) {
                    val writtenResources = HashSet<String>()
                    val resourcesMs = measureTimeMillis {
                        preparedResources.forEach { prepared ->
                            val source = prepared.source
                            if (!source.isFile) return@forEach
                            val snapshotPath = prepared.mapping.snapshotPath
                            if (!writtenResources.add(snapshotPath)) return@forEach
                            zos.putNextEntry(ZipEntry(snapshotPath))
                            BufferedInputStream(FileInputStream(source)).use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    zos.write(buffer, 0, read)
                                }
                            }
                            zos.closeEntry()
                        }
                    }
                    AppLogger.i(TAG, "export add resources done in ${resourcesMs}ms (count=${writtenResources.size})")
                }

                val alwaysExcluded = OperitPaths.rawSnapshotExcludedFilesTopLevelDirNames()
                val excludedNames = buildSet {
                    addAll(alwaysExcluded)
                    if (!options.includeTerminalData) addAll(terminalTopLevelDirNames)
                    if (!options.includeLogs) addAll(logFilesTopLevelDirNames)
                }
                withContext(Dispatchers.Main) {
                    onProgress?.invoke(ExportProgressInfo(stage = ExportProgress.SCANNING_FILES, scannedFiles = 0))
                }
                val filesTotalCount = totalFilesForZip(
                    dir = context.filesDir,
                    entryPrefix = ENTRY_FILES,
                    excludedTopLevelDirNames = excludedNames,
                    referencedResourcePaths = referencedImagePaths,
                    onScannedCountChanged = { scanned ->
                        if (onProgress != null) {
                            mainHandler.post {
                                onProgress.invoke(
                                    ExportProgressInfo(stage = ExportProgress.SCANNING_FILES, scannedFiles = scanned)
                                )
                            }
                        }
                    }
                )
                withContext(Dispatchers.Main) {
                    onProgress?.invoke(
                        ExportProgressInfo(stage = ExportProgress.SCANNING_FILES, scannedFiles = filesTotalCount)
                    )
                }
                withContext(Dispatchers.Main) { onProgress?.invoke(ExportProgressInfo(ExportProgress.ZIPPING_FILES, 0)) }
                val filesMs = measureTimeMillis {
                    addDirToZip(
                        zos = zos,
                        dir = context.filesDir,
                        entryPrefix = ENTRY_FILES,
                        excludedTopLevelDirNames = excludedNames,
                        referencedResourcePaths = referencedImagePaths,
                        totalFiles = filesTotalCount,
                        onPercentChanged = { percent ->
                            if (onProgress != null) {
                                mainHandler.post {
                                    onProgress.invoke(ExportProgressInfo(ExportProgress.ZIPPING_FILES, percent))
                                }
                            }
                        }
                    )
                }
                withContext(Dispatchers.Main) { onProgress?.invoke(ExportProgressInfo(ExportProgress.ZIPPING_FILES, 100)) }
                AppLogger.i(TAG, "export add files done in ${filesMs}ms (excludedTopLevel=${excludedNames.size})")

                val externalFilesTotalCount = totalFilesForZip(
                    dir = externalFilesDir,
                    entryPrefix = ENTRY_EXTERNAL_FILES,
                    excludedTopLevelDirNames = if (options.includeLogs) emptySet() else logExternalTopLevelDirNames,
                    referencedResourcePaths = referencedImagePaths
                )
                withContext(Dispatchers.Main) {
                    onProgress?.invoke(ExportProgressInfo(ExportProgress.ZIPPING_EXTERNAL_FILES, 0))
                }
                val externalFilesMs = measureTimeMillis {
                    addDirToZip(
                        zos = zos,
                        dir = externalFilesDir,
                        entryPrefix = ENTRY_EXTERNAL_FILES,
                        excludedTopLevelDirNames = if (options.includeLogs) emptySet() else logExternalTopLevelDirNames,
                        referencedResourcePaths = referencedImagePaths,
                        totalFiles = externalFilesTotalCount,
                        onPercentChanged = { percent ->
                            if (onProgress != null) {
                                mainHandler.post {
                                    onProgress.invoke(
                                        ExportProgressInfo(ExportProgress.ZIPPING_EXTERNAL_FILES, percent)
                                    )
                                }
                            }
                        }
                    )
                }
                withContext(Dispatchers.Main) {
                    onProgress?.invoke(ExportProgressInfo(ExportProgress.ZIPPING_EXTERNAL_FILES, 100))
                }
                AppLogger.i(TAG, "export add external_files done in ${externalFilesMs}ms")

                withContext(Dispatchers.Main) { onProgress?.invoke(ExportProgressInfo(ExportProgress.ZIPPING_SHARED_PREFS)) }
                val sharedPrefsMs = measureTimeMillis { addDirToZip(zos, sharedPrefsDir, ENTRY_SHARED_PREFS) }
                AppLogger.i(TAG, "export add shared_prefs done in ${sharedPrefsMs}ms")

                withContext(Dispatchers.Main) { onProgress?.invoke(ExportProgressInfo(ExportProgress.ZIPPING_DATASTORE)) }
                val datastoreMs = measureTimeMillis { addDirToZip(zos, datastoreDir, ENTRY_DATASTORE) }
                AppLogger.i(TAG, "export add datastore done in ${datastoreMs}ms")

                withContext(Dispatchers.Main) { onProgress?.invoke(ExportProgressInfo(ExportProgress.ZIPPING_DATABASES)) }
                val databasesMs = measureTimeMillis { addDirToZip(zos, databasesDir, ENTRY_DATABASES) }
                AppLogger.i(TAG, "export add databases done in ${databasesMs}ms")
            }

            withContext(Dispatchers.Main) { onProgress?.invoke(ExportProgressInfo(ExportProgress.FINALIZING)) }
            if (outFile.exists()) {
                outFile.delete()
            }

            if (!tmpFile.renameTo(outFile)) {
                tmpFile.copyTo(outFile, overwrite = true)
                tmpFile.delete()
            }

            AppLogger.i(TAG, "export done: ${outFile.absolutePath} (${outFile.length()} bytes)")
            outFile
        }
    }

    suspend fun restoreFromBackupUri(
        context: Context,
        uri: Uri,
        onProgress: ((RestoreProgress) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        TokenUsageRepository.withDatabaseRestore {
            val cacheZip = File.createTempFile("raw_snapshot_restore_", ".zip", context.cacheDir)
            val workDir = File(context.cacheDir, "raw_snapshot_restore_work").apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }

            try {
                AppLogger.i(TAG, "restore start uri=$uri")
                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.PREPARING) }
                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.READING_ZIP) }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(cacheZip).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Failed to open uri")

                AppLogger.i(TAG, "restore cached zip: ${cacheZip.absolutePath} (${cacheZip.length()} bytes)")

                AppDatabase.closeDatabase()
                ObjectBoxManager.closeAll()

                AppLogger.i(TAG, "restore closed databases (room + objectbox)")

                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.EXTRACTING) }
                val manifest = extractZipToWorkDir(cacheZip, workDir)

                val payloadDir = File(workDir, "payload")
                val externalFilesPayloadDir = File(payloadDir, "external_files")

                val resourceRestorePlans = buildResourceRestorePlans(
                    context = context,
                    manifest = manifest,
                    workDir = workDir,
                )
                rewriteResourceUrisInExtractedPreferences(
                    workDir = workDir,
                    resourceRestorePlans = resourceRestorePlans,
                )

                val alwaysExcluded = OperitPaths.rawSnapshotExcludedFilesTopLevelDirNames()
                val preserveTerminal = !manifest.includeTerminalData
                val preservedTerminalNames = if (preserveTerminal) terminalTopLevelDirNames else emptySet()
                val preservedAlwaysExcludedNames = alwaysExcluded.filterNot { dirName ->
                    File(payloadDir, "files/$dirName").exists()
                }.toSet()
                val preservedLogNames = if (!manifest.includeLogs) logFilesTopLevelDirNames else emptySet()
                val preservedNames = preservedTerminalNames + preservedAlwaysExcludedNames + preservedLogNames

                AppLogger.i(
                    TAG,
                    "restore manifest ok (formatVersion=${manifest.formatVersion}, " +
                        "includeTerminalData=${manifest.includeTerminalData}, " +
                        "includeLogs=${manifest.includeLogs}, resources=${manifest.resources.size})"
                )

                AppLogger.i(TAG, "restore replace dirs (preserveTopLevel=${preservedNames.joinToString()})")

                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.REPLACING_FILES) }
                replaceDirContents(File(payloadDir, "files"), context.filesDir, preservedTopLevelDirNames = preservedNames)
                if (externalFilesPayloadDir.exists()) {
                    val externalFilesDir = requireNotNull(context.getExternalFilesDir(null)) {
                        "External files dir is unavailable"
                    }
                    withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.REPLACING_EXTERNAL_FILES) }
                    val preservedExternalLogNames = if (!manifest.includeLogs) logExternalTopLevelDirNames else emptySet()
                    replaceDirContents(
                        externalFilesPayloadDir,
                        externalFilesDir,
                        preservedTopLevelDirNames = preservedExternalLogNames,
                    )
                }
                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.REPLACING_SHARED_PREFS) }
                replaceDirContents(File(payloadDir, "shared_prefs"), File(context.dataDir, "shared_prefs"))
                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.REPLACING_DATASTORE) }
                replaceDirContents(File(payloadDir, "datastore"), File(context.dataDir, "datastore"))
                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.REPLACING_DATABASES) }
                replaceDirContents(File(payloadDir, "databases"), File(context.dataDir, "databases"))

                restoreSnapshotResources(resourceRestorePlans)

                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.FINALIZING) }
                AppLogger.i(TAG, "restore done: ${manifest.packageName}")
            } catch (e: Exception) {
                AppLogger.e(TAG, "restore failed", e)
                throw e
            } finally {
                try {
                    cacheZip.delete()
                } catch (_: Exception) {
                }
                try {
                    workDir.deleteRecursively()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun extractZipToWorkDir(zipFile: File, workDir: File): Manifest {
        val payloadRoot = File(workDir, "payload")
        payloadRoot.mkdirs()

        var manifestText: String? = null
        var extractedPayloadFiles = 0

        val buffer = ByteArray(64 * 1024)
        val extractMs = measureTimeMillis {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val name = entry.name

                    if (entry.isDirectory) {
                        zis.closeEntry()
                        continue
                    }

                    if (name == ENTRY_MANIFEST) {
                        val bytes = zis.readBytesSafely(maxBytes = 512 * 1024)
                        manifestText = bytes.toString(Charsets.UTF_8)
                        zis.closeEntry()
                        continue
                    }

                    if (!name.startsWith(ENTRY_PAYLOAD_PREFIX)) {
                        zis.closeEntry()
                        continue
                    }

                    val target = File(workDir, name)
                    val workCanonical = workDir.canonicalFile
                    val targetCanonical = target.canonicalFile
                    if (!targetCanonical.path.startsWith(workCanonical.path + File.separator)) {
                        zis.closeEntry()
                        throw IllegalArgumentException("Invalid zip entry path: $name")
                    }

                    target.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(target)).use { output ->
                        while (true) {
                            val read = zis.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                        }
                    }

                    extractedPayloadFiles++

                    zis.closeEntry()
                }
            }
        }

        AppLogger.i(TAG, "restore extract done in ${extractMs}ms (payloadFiles=$extractedPayloadFiles)")

        val manifest = manifestText?.let { json.decodeFromString(Manifest.serializer(), it) }
            ?: throw IllegalArgumentException("Invalid backup zip: missing $ENTRY_MANIFEST")

        if (manifest.formatVersion != FORMAT_VERSION) {
            throw IllegalArgumentException("Unsupported backup version: ${manifest.formatVersion}")
        }

        if (!isSupportedSnapshotPackageName(manifest.packageName)) {
            throw IllegalArgumentException("Backup package mismatch: ${manifest.packageName}")
        }

        return manifest
    }

    private fun addDirToZip(
        zos: ZipOutputStream,
        dir: File,
        entryPrefix: String,
        excludedTopLevelDirNames: Set<String> = emptySet(),
        referencedResourcePaths: Set<String> = emptySet(),
        totalFiles: Int = 0,
        onPercentChanged: ((Int) -> Unit)? = null
    ) {
        if (!dir.exists() || !dir.isDirectory) return

        val baseCanonical = dir.canonicalFile
        val buffer = ByteArray(64 * 1024)
        val writtenEntryNames = HashSet<String>()

        var processedFiles = 0
        var lastPercent = -1

        dir.walkTopDown().onEnter { currentDir ->
            !shouldPruneDirForZip(currentDir, dir, entryPrefix, excludedTopLevelDirNames)
        }.forEach { f ->
            if (!f.isFile) return@forEach

            val canonical = f.canonicalFile
            val referencedSkip =
                referencedResourcePaths.isNotEmpty() && referencedResourcePaths.contains(canonical.path)
            if (referencedSkip) {
                AppLogger.i(TAG, "export skip referenced resource in raw copy: ${canonical.absolutePath}")
                return@forEach
            }
            if (entryPrefix == ENTRY_FILES && isUnreferencedThemeMediaFlatFile(canonical, baseCanonical)) {
                AppLogger.i(TAG, "export skip unreferenced theme media: ${canonical.name}")
                return@forEach
            }
            if (shouldSkipForZip(canonical, baseCanonical, entryPrefix, excludedTopLevelDirNames)) {
                if (canonical.name == "lock.mdb" && canonical.parentFile?.name?.startsWith("objectbox") == true) {
                    AppLogger.w(TAG, "export skip objectbox lock file: ${canonical.absolutePath}")
                }
                return@forEach
            }

            val rel = canonical.path.substring(baseCanonical.path.length + 1)
            val entryName = entryPrefix + rel.replace(File.separatorChar, '/')

            if (!writtenEntryNames.add(entryName)) {
                AppLogger.w(TAG, "export skip duplicate entry: $entryName")
                return@forEach
            }

            zos.putNextEntry(ZipEntry(entryName))
            BufferedInputStream(FileInputStream(canonical)).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    zos.write(buffer, 0, read)
                }
            }
            zos.closeEntry()

            if (totalFiles > 0 && onPercentChanged != null) {
                processedFiles++
                val percent = ((processedFiles * 100) / totalFiles).coerceIn(0, 100)
                if (percent != lastPercent) {
                    lastPercent = percent
                    onPercentChanged(percent)
                }
            }
        }
    }

    private fun prepareImageResources(
        context: Context,
        references: Set<RawSnapshotResourceReference>,
    ): List<PreparedImageResource> {
        val seenSourcePaths = HashSet<String>()
        val result = ArrayList<PreparedImageResource>()
        references.forEach { reference ->
            val localPath = reference.localPath ?: return@forEach
            val source = File(localPath)
            if (!source.isFile) return@forEach
            val canonical = source.canonicalFile
            if (!seenSourcePaths.add(canonical.path)) return@forEach
            val extension = canonical.extension
            val snapshotPath = RawSnapshotResourceLayout.directoryFor(reference) +
                RawSnapshotResourceLayout.fileName(reference, extension)
            val restoreLocation = restoreLocationFor(context, canonical)
            result += PreparedImageResource(
                source = canonical,
                mapping = ResourceMapping(
                    ownerType = reference.ownerType.name,
                    kind = reference.kind.name,
                    ownerId = reference.ownerId,
                    ownerName = reference.ownerName,
                    originalUri = reference.uri,
                    snapshotPath = snapshotPath,
                    restoreRoot = restoreLocation?.root,
                    restoreRelativePath = restoreLocation?.relativePath,
                ),
            )
        }
        AppLogger.i(TAG, "prepare resources done (references=${references.size} files=${result.size})")
        return result
    }

    private fun buildResourceRestorePlans(
        context: Context,
        manifest: Manifest,
        workDir: File,
    ): List<ResourceRestorePlan> {
        if (manifest.resources.isEmpty()) return emptyList()

        val seenOriginalUris = HashSet<String>()
        return manifest.resources.mapNotNull { mapping ->
            if (!seenOriginalUris.add(mapping.originalUri)) return@mapNotNull null

            val snapshotFile = snapshotFileFor(workDir, mapping)
            if (snapshotFile == null || !snapshotFile.isFile) {
                AppLogger.w(TAG, "restore resource skip (snapshot file missing): ${mapping.snapshotPath}")
                return@mapNotNull null
            }

            val destination = restoreDestinationFor(mapping, context)
            if (destination == null) {
                AppLogger.w(TAG, "restore resource skip (unsupported originalUri): ${mapping.originalUri}")
                return@mapNotNull null
            }

            ResourceRestorePlan(
                mapping = mapping,
                snapshotFile = snapshotFile,
                destination = destination,
                destinationUri = uriForDestination(mapping.originalUri, destination),
            )
        }
    }

    private fun snapshotFileFor(workDir: File, mapping: ResourceMapping): File? {
        val payloadRoot = File(workDir, "payload").canonicalFile
        val rawPath = mapping.snapshotPath.trimStart('/')
        val relativePath = when {
            rawPath.startsWith("payload/") -> rawPath.removePrefix("payload/")
            rawPath.startsWith("resources/") -> rawPath
            else -> return null
        }
        val candidate = File(payloadRoot, relativePath).canonicalFile
        return candidate.takeIf { isWithin(payloadRoot, it) }
    }

    private fun restoreLocationFor(context: Context, source: File): RestoreLocation? {
        val canonicalSource = source.canonicalFile
        val dataRoot = context.dataDir.canonicalFile
        if (isWithin(dataRoot, canonicalSource)) {
            return RestoreLocation(
                root = RESTORE_ROOT_APP_DATA,
                relativePath = relativePathFrom(dataRoot, canonicalSource),
            )
        }

        val externalRoot = context.getExternalFilesDir(null)?.canonicalFile
        if (externalRoot != null && isWithin(externalRoot, canonicalSource)) {
            return RestoreLocation(
                root = RESTORE_ROOT_EXTERNAL_FILES,
                relativePath = relativePathFrom(externalRoot, canonicalSource),
            )
        }
        return null
    }

    private fun restoreSnapshotResources(resourceRestorePlans: List<ResourceRestorePlan>) {
        if (resourceRestorePlans.isEmpty()) {
            AppLogger.i(TAG, "restore resources done (restored=0 skipped=0 total=0)")
            return
        }

        var restored = 0
        var skipped = 0
        resourceRestorePlans.forEach { plan ->
            try {
                plan.destination.parentFile?.mkdirs()
                plan.snapshotFile.copyTo(plan.destination, overwrite = true)
                restored++
            } catch (e: Exception) {
                AppLogger.w(TAG, "restore resource failed: ${plan.mapping.snapshotPath}", e)
                skipped++
            }
        }
        AppLogger.i(
            TAG,
            "restore resources done (restored=$restored skipped=$skipped total=${resourceRestorePlans.size})"
        )
    }

    private fun restoreDestinationFor(
        mapping: ResourceMapping,
        context: Context,
    ): File? {
        val configuredRoot = when (mapping.restoreRoot) {
            RESTORE_ROOT_APP_DATA -> context.dataDir
            RESTORE_ROOT_EXTERNAL_FILES -> context.getExternalFilesDir(null)
            else -> null
        }
        val configuredRelativePath = mapping.restoreRelativePath
        if (configuredRoot != null && !configuredRelativePath.isNullOrBlank()) {
            return resolveWithin(configuredRoot, configuredRelativePath)
        }

        // Compatibility for manifests created before restoreRoot/restoreRelativePath was added.
        return restoreLegacyDestination(mapping.originalUri, context)
    }

    private fun restoreLegacyDestination(originalUri: String, context: Context): File? {
        val uri = runCatching { Uri.parse(originalUri) }.getOrNull() ?: return null
        val path = when (uri.scheme?.lowercase()) {
            null, "" -> originalUri
            "file" -> uri.path
            else -> null
        } ?: return null
        val normalizedPath = path.replace('\\', '/')
        val filesMarker = "/files/"
        val markerIndex = normalizedPath.lastIndexOf(filesMarker)
        if (markerIndex < 0) return null

        val relativePath = normalizedPath.substring(markerIndex + filesMarker.length)
        val isExternalFilesPath = normalizedPath.contains("/Android/data/") ||
            normalizedPath.contains("/Android/media/")
        val root = if (isExternalFilesPath) {
            context.getExternalFilesDir(null)
        } else {
            context.filesDir
        } ?: return null
        return resolveWithin(root, relativePath)
    }

    private fun uriForDestination(originalUri: String, destination: File): String {
        val scheme = runCatching { Uri.parse(originalUri).scheme?.lowercase() }.getOrNull()
        return if (scheme.isNullOrBlank()) {
            destination.path
        } else {
            Uri.fromFile(destination).toString()
        }
    }

    private fun resolveWithin(root: File, relativePath: String): File? {
        if (relativePath.isBlank() || relativePath.startsWith("/") || relativePath.contains('\u0000')) {
            return null
        }
        val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val candidate = runCatching {
            File(canonicalRoot, relativePath.replace('/', File.separatorChar)).canonicalFile
        }.getOrNull() ?: return null
        return candidate.takeIf { isWithin(canonicalRoot, it) && it != canonicalRoot }
    }

    private fun relativePathFrom(root: File, file: File): String {
        return file.path.substring(root.path.length + 1).replace(File.separatorChar, '/')
    }

    private fun isWithin(root: File, file: File): Boolean {
        return file.path == root.path || file.path.startsWith(root.path + File.separator)
    }

    private fun rewriteResourceUrisInExtractedPreferences(
        workDir: File,
        resourceRestorePlans: List<ResourceRestorePlan>,
    ) {
        if (resourceRestorePlans.isEmpty()) return
        val replacements = linkedMapOf<String, String>()
        resourceRestorePlans.forEach { plan ->
            replacements.putIfAbsent(plan.mapping.originalUri, plan.destinationUri)
        }
        if (replacements.isEmpty()) return

        val payloadRoot = File(workDir, "payload")
        val preferenceDirs = listOf(
            File(payloadRoot, "files/datastore"),
            File(payloadRoot, "datastore"),
            File(payloadRoot, "shared_prefs"),
        )
        var changedFiles = 0
        preferenceDirs
            .filter { it.isDirectory }
            .flatMap { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && (it.name.endsWith(".preferences_pb") || it.name.endsWith(".xml")) }
                    .toList()
            }
            .distinctBy { it.canonicalPath }
            .forEach { file ->
                if (rewritePreferenceFile(file, replacements)) changedFiles++
            }

        AppLogger.i(
            TAG,
            "rewrite resource URI references done (mappings=${replacements.size}, files=$changedFiles)"
        )
    }

    private fun rewritePreferenceFile(
        file: File,
        replacements: Map<String, String>,
    ): Boolean {
        val original = runCatching { file.readBytes() }.getOrNull() ?: return false
        val rewritten = if (file.name.endsWith(".preferences_pb")) {
            rewritePreferenceProto(original, replacements, depth = 0) ?: original
        } else {
            val text = original.toString(Charsets.UTF_8)
            replacements.entries.fold(text) { current, (source, destination) ->
                current.replace(source, destination)
            }.toByteArray(Charsets.UTF_8)
        }
        if (original.contentEquals(rewritten)) return false
        return runCatching {
            file.writeBytes(rewritten)
            true
        }.getOrDefault(false)
    }

    private data class ProtoVarint(
        val value: Long,
        val nextOffset: Int,
    )

    private fun rewritePreferenceProto(
        bytes: ByteArray,
        replacements: Map<String, String>,
        depth: Int,
    ): ByteArray? {
        if (depth > 12) return null
        val output = ByteArrayOutputStream(bytes.size)
        var offset = 0
        while (offset < bytes.size) {
            val tag = readProtoVarint(bytes, offset) ?: return null
            if (tag.value == 0L) return null
            output.write(bytes, offset, tag.nextOffset - offset)
            offset = tag.nextOffset

            when ((tag.value and 7L).toInt()) {
                0 -> {
                    val value = readProtoVarint(bytes, offset) ?: return null
                    output.write(bytes, offset, value.nextOffset - offset)
                    offset = value.nextOffset
                }
                1 -> {
                    if (bytes.size - offset < 8) return null
                    output.write(bytes, offset, 8)
                    offset += 8
                }
                2 -> {
                    val length = readProtoVarint(bytes, offset) ?: return null
                    if (length.value < 0L || length.value > Int.MAX_VALUE) return null
                    val payloadStart = length.nextOffset
                    val payloadLength = length.value.toInt()
                    if (payloadLength > bytes.size - payloadStart) return null
                    val payload = bytes.copyOfRange(payloadStart, payloadStart + payloadLength)
                    val payloadText = payload.toString(Charsets.UTF_8)
                    val replacementText = replacements[payloadText]
                    val rewrittenPayload = if (replacementText != null) {
                        replacementText.toByteArray(Charsets.UTF_8)
                    } else {
                        rewritePreferenceProto(payload, replacements, depth + 1) ?: payload
                    }
                    writeProtoVarint(output, rewrittenPayload.size.toLong())
                    output.write(rewrittenPayload)
                    offset = payloadStart + payloadLength
                }
                5 -> {
                    if (bytes.size - offset < 4) return null
                    output.write(bytes, offset, 4)
                    offset += 4
                }
                else -> return null
            }
        }
        return output.toByteArray()
    }

    private fun readProtoVarint(bytes: ByteArray, startOffset: Int): ProtoVarint? {
        if (startOffset < 0 || startOffset >= bytes.size) return null
        var offset = startOffset
        var value = 0L
        var shift = 0
        while (offset < bytes.size && shift <= 63) {
            val byte = bytes[offset].toInt() and 0xff
            if (shift == 63 && byte > 1) return null
            value = value or ((byte and 0x7f).toLong() shl shift)
            offset++
            if ((byte and 0x80) == 0) return ProtoVarint(value, offset)
            shift += 7
        }
        return null
    }

    private fun writeProtoVarint(output: ByteArrayOutputStream, value: Long) {
        var remaining = value
        while ((remaining and -128L) != 0L) {
            output.write(((remaining and 0x7fL) or 0x80L).toInt())
            remaining = remaining ushr 7
        }
        output.write(remaining.toInt())
    }

    private fun isUnreferencedThemeMediaFlatFile(canonical: File, baseCanonical: File): Boolean {
        if (canonical == baseCanonical || !canonical.path.startsWith(baseCanonical.path + File.separator)) {
            return false
        }
        val rel = canonical.path.substring(baseCanonical.path.length + 1)
        if (rel.contains('/')) return false
        val name = canonical.name
        return themeMediaFlatNamePrefixes.any { name.startsWith(it) }
    }

    private fun shouldPruneDirForZip(
        currentDir: File,
        baseDir: File,
        entryPrefix: String,
        excludedTopLevelDirNames: Set<String>
    ): Boolean {
        if (currentDir == baseDir) return false
        val parent = currentDir.parentFile ?: return false
        if (parent != baseDir) return false

        val name = currentDir.name
        if (excludedTopLevelDirNames.contains(name)) return true

        if (entryPrefix == ENTRY_FILES) {
            if (name.startsWith("sherpa-ncnn-")) return true
        }

        return false
    }

    private fun shouldSkipForZip(
        canonical: File,
        baseCanonical: File,
        entryPrefix: String,
        excludedTopLevelDirNames: Set<String>
    ): Boolean {
        if (!canonical.path.startsWith(baseCanonical.path + File.separator)) return true

        if (canonical.name == "lock.mdb" && canonical.parentFile?.name?.startsWith("objectbox") == true) {
            return true
        }

        val rel = canonical.path.substring(baseCanonical.path.length + 1)
        val relNormalized = rel.replace(File.separatorChar, '/')
        val top = relNormalized.substringBefore('/', missingDelimiterValue = relNormalized)
        if (excludedTopLevelDirNames.isNotEmpty() && excludedTopLevelDirNames.contains(top)) {
            return true
        }

        if (entryPrefix == ENTRY_FILES) {
            if (top.startsWith("sherpa-ncnn-")) {
                return true
            }

            // Exclude Ubuntu rootfs package (very large). Stored as a top-level file in filesDir.
            if (!relNormalized.contains('/')) {
                val name = relNormalized
                if (name.startsWith("ubuntu-", ignoreCase = true) && name.endsWith(".tar.xz", ignoreCase = true)) {
                    return true
                }
            }

            if (!relNormalized.contains('/')) {
                if (relNormalized.startsWith("memory_hnsw_") && relNormalized.endsWith(".idx")) {
                    return true
                }
                if (relNormalized.startsWith("doc_index_") && relNormalized.endsWith(".hnsw")) {
                    return true
                }
            }
        }

        return false
    }

    private fun totalFilesForZip(
        dir: File,
        entryPrefix: String,
        excludedTopLevelDirNames: Set<String>,
        referencedResourcePaths: Set<String> = emptySet(),
        onScannedCountChanged: ((Int) -> Unit)? = null
    ): Int {
        if (!dir.exists() || !dir.isDirectory) return 0
        val baseCanonical = dir.canonicalFile
        var total = 0

        var lastReported = 0
        var lastReportAtMs = 0L
        dir.walkTopDown().onEnter { currentDir ->
            !shouldPruneDirForZip(currentDir, dir, entryPrefix, excludedTopLevelDirNames)
        }.forEach { f ->
            if (!f.isFile) return@forEach
            val canonical = f.canonicalFile
            if (referencedResourcePaths.isNotEmpty() && referencedResourcePaths.contains(canonical.path)) {
                return@forEach
            }
            if (entryPrefix == ENTRY_FILES && isUnreferencedThemeMediaFlatFile(canonical, baseCanonical)) {
                return@forEach
            }
            if (shouldSkipForZip(canonical, baseCanonical, entryPrefix, excludedTopLevelDirNames)) return@forEach
            total++

            if (onScannedCountChanged != null) {
                val now = System.currentTimeMillis()
                if (total == 1 || total - lastReported >= 200 || now - lastReportAtMs >= 250L) {
                    lastReported = total
                    lastReportAtMs = now
                    onScannedCountChanged(total)
                }
            }
        }
        return total
    }

    private fun replaceDirContents(
        fromDir: File,
        toDir: File,
        preservedTopLevelDirNames: Set<String> = emptySet()
    ) {
        if (!toDir.exists()) {
            toDir.mkdirs()
        }

        // A raw snapshot is a complete restore point. Keeping entries that are absent from the
        // snapshot leaves newer migration markers behind and changes how restored data is read.
        toDir.listFiles()?.forEach { existing ->
            if (!preservedTopLevelDirNames.contains(existing.name)) {
                if (!existing.deleteRecursively()) {
                    AppLogger.w(TAG, "restore could not remove stale entry, will overwrite: ${existing.absolutePath}")
                }
            }
        }

        if (!fromDir.exists() || !fromDir.isDirectory) return
        copyDir(fromDir, toDir, preservedTopLevelDirNames)
    }

    private fun copyDir(
        fromDir: File,
        toDir: File,
        preservedTopLevelDirNames: Set<String>
    ) {
        val baseCanonical = fromDir.canonicalFile
        fromDir.walkTopDown().forEach { f ->
            val canonical = f.canonicalFile
            if (!canonical.path.startsWith(baseCanonical.path + File.separator) && canonical != baseCanonical) {
                return@forEach
            }

            if (canonical == baseCanonical) return@forEach

            val rel = canonical.path.substring(baseCanonical.path.length + 1)
            if (preservedTopLevelDirNames.isNotEmpty()) {
                val relNormalized = rel.replace(File.separatorChar, '/')
                val top = relNormalized.substringBefore('/', missingDelimiterValue = relNormalized)
                if (preservedTopLevelDirNames.contains(top)) {
                    return@forEach
                }
            }
            val target = File(toDir, rel)

            if (canonical.isDirectory) {
                target.mkdirs()
            } else if (canonical.isFile) {
                target.parentFile?.mkdirs()
                // DataStore observes this directory. Replacing an active preferences file by
                // truncating it exposes a transient empty payload that can be persisted again.
                val atomicFile = AtomicFile(target)
                var output: FileOutputStream? = null
                try {
                    output = atomicFile.startWrite()
                    canonical.inputStream().use { input -> input.copyTo(output) }
                    atomicFile.finishWrite(output)
                } catch (error: Throwable) {
                    output?.let(atomicFile::failWrite)
                    throw error
                }
            }
        }
    }

    private fun ZipInputStream.readBytesSafely(maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            if (out.size() + read > maxBytes) {
                throw IllegalArgumentException("Zip entry too large")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
