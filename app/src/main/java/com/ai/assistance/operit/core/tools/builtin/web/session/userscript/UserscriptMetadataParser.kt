package com.ai.assistance.operit.core.tools.builtin.web.session.userscript

internal object UserscriptMetadataParser {
    private val metadataBlockRegex =
        Regex("""(?m)^[ \t]*//[ \t]*==UserScript==\s*$([\s\S]*?)(?m)^[ \t]*//[ \t]*==/UserScript==\s*$""")
    private val metadataLineRegex = Regex("""^[ \t]*//[ \t]*@([A-Za-z0-9:_-]+)(?:[ \t]+(.*))?$""")

    fun parse(rawSource: String): ParsedUserscriptMetadata {
        val match = metadataBlockRegex.find(rawSource)
            ?: throw IllegalArgumentException("Missing userscript metadata block")
        val block = match.groupValues[1]
        val fields = linkedMapOf<String, MutableList<String>>()
        val rawHeaders = mutableListOf<UserscriptHeaderEntry>()

        block.lineSequence().forEach { line ->
            val lineMatch = metadataLineRegex.find(line) ?: return@forEach
            val key = lineMatch.groupValues[1].trim()
            val value = lineMatch.groupValues.getOrNull(2)?.trim().orEmpty()
            fields.getOrPut(key) { mutableListOf() }.add(value)
            rawHeaders += UserscriptHeaderEntry(key = key, value = value)
        }

        val name = fields.firstValue("name")?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing @name in userscript metadata")

        val resourceEntries =
            fields["resource"].orEmpty().mapNotNull { raw ->
                val parts = raw.split(Regex("""\s+"""), limit = 2)
                if (parts.size < 2) {
                    null
                } else {
                    val resourceName = parts[0].trim()
                    val url = parts[1].trim()
                    if (resourceName.isBlank() || url.isBlank()) {
                        null
                    } else {
                        UserscriptResourceEntry(name = resourceName, url = url)
                    }
                }
            }

        val noFrames =
            fields.containsKey("noframes") ||
                fields["noframe"]?.any { it.equals("true", ignoreCase = true) } == true

        return ParsedUserscriptMetadata(
            name = name,
            namespace = fields.firstValue("namespace")?.takeIf { it.isNotBlank() },
            version = fields.firstValue("version")?.takeIf { it.isNotBlank() } ?: "0",
            description = fields.firstValue("description")?.takeIf { it.isNotBlank() },
            author = fields.firstValue("author")?.takeIf { it.isNotBlank() },
            homepage =
                fields.firstValue("homepage")
                    ?.takeIf { it.isNotBlank() }
                    ?: fields.firstValue("homepageURL")?.takeIf { it.isNotBlank() },
            website =
                fields.firstValue("website")
                    ?.takeIf { it.isNotBlank() }
                    ?: fields.firstValue("source")?.takeIf { it.isNotBlank() },
            supportUrl = fields.firstValue("supportURL")?.takeIf { it.isNotBlank() },
            downloadUrl = fields.firstValue("downloadURL")?.takeIf { it.isNotBlank() },
            updateUrl = fields.firstValue("updateURL")?.takeIf { it.isNotBlank() },
            runAt = UserscriptRunAt.fromRaw(fields.firstValue("run-at")),
            grants = fields["grant"].orEmpty().map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            matches = fields["match"].orEmpty().map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            includes = fields["include"].orEmpty().map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            excludes = fields["exclude"].orEmpty().map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            excludeMatches =
                fields["exclude-match"].orEmpty().map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            connects = fields["connect"].orEmpty().map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            requires =
                fields["require"].orEmpty().mapNotNull { value ->
                    value.trim().takeIf { it.isNotBlank() }?.let(::UserscriptRequireEntry)
                },
            resources = resourceEntries,
            icons =
                UserscriptIconSet(
                    icon = fields.firstValue("icon")?.takeIf { it.isNotBlank() },
                    icon64 =
                        fields.firstValue("icon64")
                            ?.takeIf { it.isNotBlank() }
                            ?: fields.firstValue("icon64URL")?.takeIf { it.isNotBlank() },
                    defaultIcon = fields.firstValue("defaulticon")?.takeIf { it.isNotBlank() }
                ),
            tags = fields["tag"].orEmpty().map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            sandbox = fields.firstValue("sandbox")?.takeIf { it.isNotBlank() },
            runIn = fields.firstValue("run-in")?.takeIf { it.isNotBlank() },
            unwrap =
                fields.containsKey("unwrap") ||
                    fields["unwrap"]?.any { it.equals("true", ignoreCase = true) } == true,
            webRequestRules =
                fields["webRequest"].orEmpty().map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            rawHeaders = rawHeaders,
            noFrames = noFrames,
            metadataBlock = block.trim()
        )
    }

    private fun Map<String, List<String>>.firstValue(key: String): String? = this[key]?.firstOrNull()
}
