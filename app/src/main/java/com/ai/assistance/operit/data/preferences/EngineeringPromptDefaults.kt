package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.PromptTag
import java.util.Locale

object EngineeringPromptDefaults {
    const val CLAUDE_CODE_CORE_ENGLISH = """# Engineering Guidelines (Claude Code)
1. Anti-Overengineering:
   - Do not make changes beyond what was requested. A bug fix does not need surrounding code refactored. A simple feature does not need extra configurability.
   - Do not add docstrings, comments, or type annotations to code you did not change. Only add comments explaining non-obvious "WHY", never "WHAT".
   - Trust internal framework guarantees; only validate at external system boundaries.
   - Do not create helpers or premature abstractions for one-time operations. Three similar lines of code is better than a premature abstraction.
   - Avoid backwards-compatibility shims like dead aliases or `// removed` comments. If something is unused, delete it cleanly.
2. Faithful Reporting:
   - Report verification outcomes faithfully. If checks fail or were skipped, say so explicitly. Never manufacture fake green results.
   - When confirmed complete, state it plainly without unnecessary hedging.
3. Reversibility & Blast Radius:
   - Freely take safe, reversible actions (reading, small edits, testing). Always confirm before destructive operations (e.g. deleting files, git reset --hard).
   - Investigate root causes instead of bypassing safety checks.
4. Diagnostic Discipline:
   - When an approach fails, diagnose root causes from error outputs before changing tactics. Never blindly repeat failed actions."""

    const val CODEX_CORE_ENGLISH = """# Engineering Guidelines (CodeX)
1. Ground in Environment First:
   - Explore before asking. Discover facts by inspecting files, configs, and entrypoints; do not ask questions that can be answered from the environment.
   - Only ask clarifying questions after reasonable exploration fails to resolve obvious ambiguities.
2. Respect Existing Worktree:
   - Never revert or discard existing changes you did not make; treat them as user work-in-progress.
   - If you notice unexpected external changes while working, stop immediately and ask the user how to proceed.
   - Never use destructive commands like `git reset --hard` unless specifically requested.
3. Decision-Complete Planning:
   - For complex tasks, make a concrete plan leaving no ambiguous decisions for implementation.
   - Keep planning strictly non-mutating (read, search, dry-run only; no file writes).
4. Concise Delivery & Next Steps:
   - State the solution clearly without dumping entire files. Do not instruct the user to "save/copy this file".
   - When suggesting logical next steps, use numbered lists (1. 2. 3.) for quick replies."""

    private val SUPPORTED_LOCALES = listOf(
        Locale.CHINA,
        Locale.ENGLISH,
        Locale.KOREAN,
        Locale("es"),
        Locale("pt", "BR"),
        Locale("id"),
        Locale("ms"),
        Locale("ro")
    )

    private fun getLocalizedDefaults(context: Context, resId: Int): Set<String> {
        val result = mutableSetOf<String>()
        result.add(context.getString(resId).trim())
        for (locale in SUPPORTED_LOCALES) {
            try {
                val config = android.content.res.Configuration(context.resources.configuration)
                config.setLocale(locale)
                val localizedContext = context.createConfigurationContext(config)
                result.add(localizedContext.getString(resId).trim())
            } catch (_: Exception) {}
        }
        return result
    }

    fun isClaudeCodePreset(name: String): Boolean {
        return name.contains("ClaudeCode", ignoreCase = true)
    }

    fun isCodeXPreset(name: String): Boolean {
        return name.contains("CodeX", ignoreCase = true) || name.contains("Codex", ignoreCase = true)
    }

    fun resolvePromptContent(context: Context, tag: PromptTag): String {
        val rawContent = tag.promptContent.trim()
        if (rawContent.isBlank()) return ""

        if (isClaudeCodePreset(tag.name)) {
            val defaults = getLocalizedDefaults(context, R.string.tag_claudecode_content)
            if (rawContent in defaults || rawContent == CLAUDE_CODE_CORE_ENGLISH.trim()) {
                return CLAUDE_CODE_CORE_ENGLISH
            }
            return tag.promptContent
        }

        if (isCodeXPreset(tag.name)) {
            val defaults = getLocalizedDefaults(context, R.string.tag_codex_content)
            if (rawContent in defaults || rawContent == CODEX_CORE_ENGLISH.trim()) {
                return CODEX_CORE_ENGLISH
            }
            return tag.promptContent
        }

        return tag.promptContent
    }
}