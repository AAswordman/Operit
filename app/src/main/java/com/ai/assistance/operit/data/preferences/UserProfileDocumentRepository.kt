package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.util.AtomicFile
import com.ai.assistance.operit.data.model.LegacyUserProfile
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single source of truth for the human user's stable profile.
 *
 * Keeping this as a normal private Markdown file makes the exact model context inspectable and
 * editable. AtomicFile is required because a partially written profile would silently change the
 * model's behavior on every subsequent request.
 */
class UserProfileDocumentRepository private constructor(private val context: Context) {
    companion object {
        const val MAX_CONTENT_CHARS = 12_000
        const val USER_FILE_NAME = "user.md"
        const val LEGACY_ARCHIVE_FILE_NAME = "legacy-user-profiles.md"

        const val DEFAULT_TEMPLATE = ""

        private val LEGACY_EMPTY_TEMPLATE = """# About me

<!-- Describe yourself, your preferences, and how you want the assistant to communicate. -->
"""

        private const val MIGRATION_PREFERENCES = "user_profile_document"
        private const val SCHEMA_VERSION_KEY = "schema_version"
        private const val CURRENT_SCHEMA_VERSION = 2

        @Volatile
        private var INSTANCE: UserProfileDocumentRepository? = null

        fun getInstance(context: Context): UserProfileDocumentRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserProfileDocumentRepository(context.applicationContext ?: context).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val userFile = File(context.filesDir, USER_FILE_NAME)
    private val archiveFile = File(context.filesDir, LEGACY_ARCHIVE_FILE_NAME)
    private val migrationPreferences =
        context.getSharedPreferences(MIGRATION_PREFERENCES, Context.MODE_PRIVATE)
    private val initializationMutex = Mutex()
    private val writeMutex = Mutex()
    private val contentState = MutableStateFlow<String?>(null)

    val contentFlow: Flow<String> = flow {
        initialize()
        emitAll(contentState.filterNotNull())
    }

    suspend fun initialize() {
        initializationMutex.withLock {
            if (contentState.value != null) return

            val schemaVersion = migrationPreferences.getInt(SCHEMA_VERSION_KEY, 0)
            if (schemaVersion < CURRENT_SCHEMA_VERSION) {
                migrateReleasedStructuredProfiles()
            }

            // The first user.md implementation stored its editor hint inside the document, which
            // made an otherwise empty profile part of every system prompt. Remove only that exact
            // untouched template; real user-authored Markdown is never rewritten here.
            if (
                userFile.exists() &&
                    userFile.readText(StandardCharsets.UTF_8) == LEGACY_EMPTY_TEMPLATE
            ) {
                writeAtomically(userFile, DEFAULT_TEMPLATE)
            }

            if (!userFile.exists()) {
                writeAtomically(userFile, DEFAULT_TEMPLATE)
            }
            contentState.value = userFile.readText(StandardCharsets.UTF_8)
        }
    }

    suspend fun load(): String {
        initialize()
        return requireNotNull(contentState.value)
    }

    suspend fun save(markdown: String) {
        require(markdown.length <= MAX_CONTENT_CHARS) {
            "user.md exceeds the $MAX_CONTENT_CHARS character limit"
        }
        initialize()
        writeMutex.withLock {
            writeAtomically(userFile, markdown)
            contentState.value = markdown
        }
    }

    suspend fun resetToTemplate() {
        save(DEFAULT_TEMPLATE)
    }

    fun hasLegacyArchive(): Boolean = archiveFile.isFile && archiveFile.length() > 0L

    fun readLegacyArchive(): String? {
        return archiveFile.takeIf { hasLegacyArchive() }?.readText(StandardCharsets.UTF_8)
    }

    private suspend fun migrateReleasedStructuredProfiles() {
        val manager = UserPreferencesManager.getInstance(context)
        val snapshot = manager.readLegacyUserProfiles()
        val activeProfile = snapshot.profiles.firstOrNull { it.id == snapshot.activeProfileId }
        val migratedActiveDocument = activeProfile?.toUserMarkdown() ?: DEFAULT_TEMPLATE
        val activeDocumentFits = migratedActiveDocument.length <= MAX_CONTENT_CHARS
        val userDocumentAlreadyExisted = userFile.exists()

        if (!userDocumentAlreadyExisted) {
            writeAtomically(
                userFile,
                if (activeDocumentFits) migratedActiveDocument else DEFAULT_TEMPLATE
            )
        }

        val archivedProfiles = snapshot.profiles.filter { profile ->
            profile.hasStructuredUserContent() &&
                (
                    profile.id != snapshot.activeProfileId ||
                        !activeDocumentFits ||
                        userDocumentAlreadyExisted
                    )
        }
        if (archivedProfiles.isNotEmpty() && !archiveFile.exists()) {
            writeAtomically(archiveFile, buildLegacyArchive(archivedProfiles))
        }

        manager.migrateLegacyProfilesToMemorySpaces(snapshot)
        check(
            migrationPreferences.edit()
                .putInt(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION)
                .commit()
        ) { "Failed to persist user.md migration version" }
    }

    private fun LegacyUserProfile.hasStructuredUserContent(): Boolean {
        return birthDate > 0L || gender.isNotBlank() || personality.isNotBlank() ||
            identity.isNotBlank() || occupation.isNotBlank() || aiStyle.isNotBlank()
    }

    private fun LegacyUserProfile.toUserMarkdown(): String {
        if (!hasStructuredUserContent()) return DEFAULT_TEMPLATE

        return buildString {
            appendLine("# About me")
            appendLine()
            appendProfileSections(this@toUserMarkdown)
        }.trimEnd() + "\n"
    }

    private fun buildLegacyArchive(profiles: List<LegacyUserProfile>): String {
        return buildString {
            appendLine("# Archived user profiles")
            appendLine()
            appendLine("> These profiles were preserved during the user.md migration and are not injected automatically.")
            profiles.forEach { profile ->
                appendLine()
                appendLine("## ${profile.name}")
                appendLine()
                appendProfileSections(profile)
            }
        }.trimEnd() + "\n"
    }

    private fun StringBuilder.appendProfileSections(profile: LegacyUserProfile) {
        val basicItems = buildList {
            if (profile.gender.isNotBlank()) add("Gender: ${profile.gender}")
            if (profile.birthDate > 0L) {
                val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                add("Birth date: ${formatter.format(Date(profile.birthDate))}")
            }
            if (profile.identity.isNotBlank()) add("Identity: ${profile.identity}")
            if (profile.occupation.isNotBlank()) add("Occupation: ${profile.occupation}")
        }
        if (basicItems.isNotEmpty()) {
            appendLine("## Basic information")
            appendLine()
            basicItems.forEach { appendLine("- $it") }
        }
        if (profile.personality.isNotBlank()) {
            if (basicItems.isNotEmpty()) appendLine()
            appendLine("## Personality")
            appendLine()
            appendLine(profile.personality)
        }
        if (profile.aiStyle.isNotBlank()) {
            if (basicItems.isNotEmpty() || profile.personality.isNotBlank()) appendLine()
            appendLine("## Preferred assistant style")
            appendLine()
            appendLine(profile.aiStyle)
        }
    }

    private fun writeAtomically(target: File, content: String) {
        val atomicFile = AtomicFile(target)
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(content.toByteArray(StandardCharsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            output?.let(atomicFile::failWrite)
            throw error
        }
    }
}
