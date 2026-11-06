package com.ai.assistance.operit.core.tools.agent

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/** Private app storage, bounded and scoped to an app install/version. The arguments object is not persisted; selectors remain part of the template. */
internal class PhoneSequenceStore(
    private val directory: File,
    private val versionForPackage: (String) -> String,
) {
    @Synchronized
    fun save(sequence: PhoneLocalSequence) {
        val name = sequence.saveAs ?: throw PhoneUiException("No sequence name provided")
        PhoneLocalPlanParser.validateName(name)
        if (sequence.steps.any { it.action == PhoneLocalAction.Type && !isPhoneArgument(requireNotNull(it.text)) }) {
            throw PhoneUiException("Reusable typed content must be parameterized")
        }
        val version = versionForPackage(sequence.packageName)
        val payload = JSONObject().put("format", 1).put("name", name).put("version", version)
            .put("plan", PhoneLocalPlanParser.encode(sequence)).toString()
        if (payload.toByteArray(Charsets.UTF_8).size > 65536) throw PhoneUiException("Reusable sequence exceeds 64 KiB")
        if (!directory.isDirectory && !directory.mkdirs()) throw PhoneUiException("Could not create private sequence directory")
        val target = file(sequence.packageName, version, name)
        val temporary = File.createTempFile("sequence-", ".tmp", directory)
        try {
            temporary.writeText(payload, Charsets.UTF_8)
            if (!temporary.renameTo(target)) throw PhoneUiException("Could not commit reusable sequence")
        } finally {
            if (temporary.exists() && !temporary.delete()) throw PhoneUiException("Could not remove temporary sequence")
        }
        val files = directory.listFiles { it.extension == "json" }
            ?: throw PhoneUiException("Could not list private sequence directory")
        files.sortedByDescending { it.lastModified() }.drop(32).forEach {
            if (!it.delete()) throw PhoneUiException("Could not remove expired sequence entry")
        }
    }

    @Synchronized
    fun load(packageName: String, name: String): PhoneLocalSequence {
        PhoneLocalPlanParser.validateName(name)
        val version = versionForPackage(packageName)
        val entry = file(packageName, version, name)
        if (!entry.isFile) throw PhoneUiException("No reusable sequence with this name for the current app version")
        val json = read(entry)
        if (json.getString("version") != version || json.getString("name") != name) throw PhoneUiException("Reusable sequence identity does not match")
        val parsed = PhoneLocalPlanParser.parse(json.getJSONObject("plan").toString()) as PhoneLocalCommand.Sequence
        if (parsed.sequence.packageName != packageName) throw PhoneUiException("Reusable sequence belongs to another app")
        return parsed.sequence
    }

    @Synchronized
    fun names(packageName: String): List<String> {
        if (!directory.exists()) return emptyList()
        val version = versionForPackage(packageName)
        val entries = directory.listFiles { it.extension == "json" }
            ?: throw PhoneUiException("Could not list private sequence directory")
        return entries.map(::read).filter {
            it.getString("version") == version && it.getJSONObject("plan").getString("package") == packageName
        }.map { it.getString("name") }.sorted()
    }

    @Synchronized
    fun catalog(packageName: String): String = names(packageName).joinToString(", ") { name ->
        val plan = load(packageName, name)
        val parameters = phoneArgumentNames(PhoneLocalPlanParser.encode(plan).toString()).sorted()
        "$name(${parameters.joinToString(",")})"
    }

    private fun read(file: File): JSONObject {
        if (file.length() > 65536) throw PhoneUiException("Reusable sequence file exceeds 64 KiB")
        val json = JSONObject(file.readText(Charsets.UTF_8))
        if (json.getInt("format") != 1) throw PhoneUiException("Unsupported reusable sequence format")
        return json
    }

    private fun file(packageName: String, version: String, name: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest("$packageName\u0000$version\u0000$name".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$hash.json")
    }
}
