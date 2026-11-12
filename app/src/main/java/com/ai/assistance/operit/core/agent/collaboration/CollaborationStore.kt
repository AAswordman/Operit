package com.ai.assistance.operit.core.agent.collaboration

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal interface CollaborationFileAccess {
    fun exists(file: File): Boolean
    fun read(file: File): String
    fun write(file: File, text: String)
}

private object AndroidCollaborationFiles : CollaborationFileAccess {
    override fun exists(file: File) = file.exists() || File(file.path + ".bak").exists()
    override fun read(file: File) = AtomicFile(file).openRead().bufferedReader().use { it.readText() }
    override fun write(file: File, text: String) {
        file.parentFile!!.mkdirs()
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
    }
}

/** Immutable tree generations become visible together through one atomic manifest commit. */
internal class CollaborationStore(
    private val filesDir: File,
    private val access: CollaborationFileAccess = AndroidCollaborationFiles,
) {
    constructor(context: Context) : this(context.filesDir)

    companion object {
        const val DIRECTORY = "subagent-v2-store"
        const val LEGACY = "subagent-v2.json"
        private val locks = ConcurrentHashMap<String, Any>()

        /** Raw restore normally merges files. These alternative formats represent one dataset. */
        fun prepareRestore(payloadFiles: File, targetFiles: File) {
            val incomingStore = File(payloadFiles, "$DIRECTORY/manifest.json").isFile
            val incomingLegacy = File(payloadFiles, LEGACY).isFile ||
                File(payloadFiles, "$LEGACY.bak").isFile
            if (!incomingStore && !incomingLegacy) return // preserve merge semantics for older backups
            synchronized(locks.computeIfAbsent(File(targetFiles, DIRECTORY).absolutePath) { Any() }) {
                val oldStore = File(targetFiles, DIRECTORY)
                check(!oldStore.exists() || oldStore.deleteRecursively()) { "Cannot replace collaboration store" }
                listOf(LEGACY, "$LEGACY.bak", "$LEGACY.new").forEach { name ->
                    val file = File(targetFiles, name)
                    check(!file.exists() || file.delete()) { "Cannot replace legacy collaboration store" }
                }
            }
        }
    }

    /**
     * [trees] holds each tree's live agents, rewritten as they work. A tree's archived agents never
     * change once archived, so [archiveSegments] keeps them in segment files: newly archived ones go
     * into a small new segment, and equal neighbours merge, so a tree with a long past writes about
     * log(n) times each entry in all instead of rewriting the whole past every time. [archives] is
     * the single archive file of version 2, only read.
     */
    @Serializable
    private data class Segment(val file: String, val size: Int)

    @Serializable
    private data class Manifest(
        val version: Int = 1,
        val trees: Map<String, String> = emptyMap(),
        val archives: Map<String, String> = emptyMap(),
        val archiveSegments: Map<String, List<Segment>> = emptyMap(),
    ) {
        val files: Set<String>
            get() = trees.values.toSet() + archives.values + archiveSegments.values.flatten().map { it.file }
    }

    /** Version 3 has archive segments; an older app refuses it instead of dropping them. */
    private fun versionFor(segments: Map<String, List<Segment>>) = if (segments.isEmpty()) 1 else 3

    private val directory = File(filesDir, DIRECTORY)
    private val manifestFile = File(directory, "manifest.json")
    private val legacy = File(filesDir, LEGACY)
    private val lock = locks.computeIfAbsent(directory.absolutePath) { Any() }
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private var committed = emptyMap<String, List<CollaborationAgent>>()
    /** Each tree's archive segments as saved, in the order of [Manifest.archiveSegments]. */
    private var committedSegments = emptyMap<String, List<List<CollaborationAgent>>>()
    private var committedArchiveIds = emptyMap<String, Set<String>>()
    private var manifest = Manifest()
    private var loaded = false

    private fun treeFile(name: String): File {
        require(Regex("[a-f0-9-]{36}\\.json").matches(name)) { "Invalid collaboration tree filename" }
        return File(directory, name)
    }

    fun load(): CollaborationState = synchronized(lock) {
        var segments = emptyMap<String, List<List<CollaborationAgent>>>()
        val state = if (access.exists(manifestFile)) {
            val read = json.decodeFromString<Manifest>(access.read(manifestFile))
            require(when (read.version) {
                1 -> read.archives.isEmpty() && read.archiveSegments.isEmpty()
                2 -> read.archiveSegments.isEmpty()
                3 -> read.archives.isEmpty()
                else -> false
            }) { "Unsupported collaboration manifest version" }
            // Version 2 kept one archive file per tree: it reads as that tree's only segment.
            manifest = read.copy(archives = emptyMap(), archiveSegments = read.archiveSegments +
                read.archives.mapValues { (_, name) -> listOf(Segment(name, -1)) })
            val live = manifest.trees.flatMap { (root, name) ->
                val tree = json.decodeFromString<CollaborationState>(access.read(treeFile(name)))
                require(tree.version == 1 && tree.agents.isNotEmpty() &&
                    tree.agents.all { it.rootChatId == root }) { "Invalid collaboration tree" }
                tree.agents
            }
            segments = manifest.archiveSegments.mapValues { (root, list) ->
                list.map { segment ->
                    val tree = json.decodeFromString<CollaborationState>(access.read(treeFile(segment.file)))
                    require(tree.version == 1 && tree.agents.all { it.rootChatId == root && it.archived }) {
                        "Invalid collaboration archive"
                    }
                    tree.agents
                }
            }
            manifest = manifest.copy(archiveSegments = manifest.archiveSegments.mapValues { (root, list) ->
                list.mapIndexed { i, segment -> segment.copy(size = segments.getValue(root)[i].size) }
            })
            CollaborationState(agents = live + segments.values.flatten().flatten())
        } else if (access.exists(legacy)) {
            json.decodeFromString<CollaborationState>(access.read(legacy)).also {
                require(it.version == 1) { "Unsupported subagent v2 storage version" }
            }
        } else CollaborationState()
        committed = state.agents.filterNot { it.archived }.groupBy { it.rootChatId }
        committedSegments = segments
        committedArchiveIds = segments.mapValues { (_, list) -> list.flatten().mapTo(HashSet()) { it.chatId } }
        loaded = true
        state
    }

    private fun writeSegment(agents: List<CollaborationAgent>): String {
        val name = "${UUID.randomUUID()}.json"
        access.write(treeFile(name), json.encodeToString(CollaborationState(agents = agents)))
        return name
    }

    fun save(state: CollaborationState) = synchronized(lock) {
        require(state.version == 1)
        if (!loaded) load()
        val trees = state.agents.filterNot { it.archived }.groupBy { it.rootChatId }
        val archives = state.agents.filter { it.archived }.groupBy { it.rootChatId }
        val nextTrees = trees.mapValues { (root, agents) ->
            val previous = manifest.trees[root]
            if (previous != null && agents == committed[root]) previous else writeSegment(agents)
        }
        val nextSegments = mutableMapOf<String, List<List<CollaborationAgent>>>()
        val nextSegmentFiles = mutableMapOf<String, List<Segment>>()
        val nextArchiveIds = mutableMapOf<String, Set<String>>()
        archives.forEach { (root, agents) ->
            val ids = agents.mapTo(HashSet()) { it.chatId }
            val previousIds = committedArchiveIds[root].orEmpty()
            var contents = committedSegments[root].orEmpty()
            var files = manifest.archiveSegments[root].orEmpty()
            // An archived agent is not expected to change, but if one did, its saved copy would be
            // stale: then the whole tree is written again, as when some left.
            val saved = contents.asSequence().flatten().associateBy { it.chatId }
            val changed = agents.any { agent -> saved[agent.chatId]?.let { it != agent } == true }
            if (ids != previousIds || changed) {
                if (!changed && ids.containsAll(previousIds)) {
                    // Only new ones: a small segment, then merge while the one before is no bigger.
                    val added = agents.filter { it.chatId !in previousIds }
                    contents = contents + listOf(added)
                    files = files + Segment(writeSegment(added), added.size)
                    while (files.size >= 2 && files[files.size - 2].size <= files.last().size) {
                        val merged = contents[contents.size - 2] + contents.last()
                        contents = contents.dropLast(2) + listOf(merged)
                        files = files.dropLast(2) + Segment(writeSegment(merged), merged.size)
                    }
                } else {
                    // Some left (their chat was deleted) or changed: one fresh segment.
                    contents = listOf(agents)
                    files = listOf(Segment(writeSegment(agents), agents.size))
                }
            }
            nextSegments[root] = contents
            nextSegmentFiles[root] = files
            nextArchiveIds[root] = ids
        }
        val next = Manifest(
            version = versionFor(nextSegmentFiles),
            trees = nextTrees,
            archiveSegments = nextSegmentFiles,
        )
        if (next != manifest || !access.exists(manifestFile)) {
            access.write(manifestFile, json.encodeToString(next))
        }
        // Nothing after the commit may turn a successful durable save into a reported failure.
        manifest = next
        committed = trees
        committedSegments = nextSegments
        committedArchiveIds = nextArchiveIds
        runCatching {
            val retained = next.files + "manifest.json"
            directory.listFiles()?.filter { it.name !in retained }?.forEach { it.delete() }
            legacy.delete()
            File(legacy.path + ".bak").delete()
        }
        Unit
    }

    /** Copies a consistent snapshot before raw backup walks the rest of filesDir. */
    fun copyForBackup(destinationFiles: File) = synchronized(lock) {
        if (access.exists(manifestFile)) {
            val text = access.read(manifestFile)
            val current = json.decodeFromString<Manifest>(text)
            require(current.version in 1..3)
            val target = File(destinationFiles, DIRECTORY).apply { mkdirs() }
            current.files.forEach { name ->
                File(target, name).writeText(access.read(treeFile(name)))
            }
            File(target, "manifest.json").writeText(text)
        } else if (access.exists(legacy)) {
            destinationFiles.mkdirs()
            File(destinationFiles, LEGACY).writeText(access.read(legacy))
        }
    }
}
