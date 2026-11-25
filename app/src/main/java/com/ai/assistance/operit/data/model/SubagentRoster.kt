package com.ai.assistance.operit.data.model

import java.text.Normalizer

/**
 * 子代理名单的规范化与解析。
 *
 * 名单既驱动 UI 编辑也驱动模型委派路由，两处必须看到同一份一致数据。
 * [normalize] 是唯一入库口径：内置行不可被用户数据挤掉或冒充，字段截断到上限，
 * 无名/重名行直接丢弃，永不抛异常——DataStore 读回任何历史脏数据都能收敛。
 */
object SubagentRoster {

    /**
     * 规范化档案列表。
     *
     * 规则：
     * - 内置通用子代理（[SubagentProfile.BUILT_IN_ID]）置顶且始终存在，规范名被恢复，
     *   `isBuiltIn=false` 的冒充行被剔除；
     * - 用户行按 [nameKey] 去重（保留先出现者），无名行丢弃；
     * - 字段截断到 [SubagentLimits]；
     * - 数量上限 [SubagentLimits.MAX_COUNT]（含内置行）；
     * - sortOrder 按「内置 + 剩余顺序」重排为 0..n-1。
     */
    fun normalize(profiles: List<SubagentProfile>): List<SubagentProfile> {
        val normalized = mutableListOf<SubagentProfile>()
        val seenNameKeys = mutableSetOf<String>()
        val seenIds = mutableSetOf<String>()

        fun push(profile: SubagentProfile) {
            val name = profile.name.trim().take(SubagentLimits.NAME)
            if (name.isEmpty()) return
            val key = nameKey(name)
            if (!seenNameKeys.add(key)) return
            if (!seenIds.add(profile.id)) return
            normalized.add(
                profile.copy(
                    id = profile.id,
                    name = name,
                    description = profile.description.trim().take(SubagentLimits.DESCRIPTION),
                    instructions = profile.instructions.take(SubagentLimits.INSTRUCTIONS),
                    modelBindingMode = SubagentProfile.ModelBindingMode.normalize(profile.modelBindingMode),
                    modelIndex = profile.modelIndex.coerceAtLeast(0),
                    sortOrder = normalized.size,
                    updatedAt = profile.updatedAt,
                )
            )
        }

        // 内置行置顶：缺失时补建；存在时恢复规范名，防止改名导致默认执行者路由失效。
        val storedBuiltIn = profiles.filter { it.id == SubagentProfile.BUILT_IN_ID }
        val builtIn = if (storedBuiltIn.isEmpty()) {
            SubagentProfile(
                id = SubagentProfile.BUILT_IN_ID,
                name = SubagentProfile.BUILT_IN_NAME,
                isBuiltIn = true,
            )
        } else {
            storedBuiltIn.first().copy(
                name = SubagentProfile.BUILT_IN_NAME,
                isBuiltIn = true,
            )
        }
        push(builtIn)

        profiles
            .filter { it.id != SubagentProfile.BUILT_IN_ID && !it.isBuiltIn }
            .forEach { candidate -> push(candidate) }

        // 上限以内保留（内置行已在位），超出截断。
        return normalized.take(SubagentLimits.MAX_COUNT).mapIndexed { index, profile ->
            profile.copy(sortOrder = index)
        }
    }

    /**
     * 按名字解析档案。`name` 为 null/空白时返回内置通用子代理；
     * 未命中返回 null，由调用方报告 unknown_agent 并列出有效名，不做猜测式兜底。
     */
    fun resolve(profiles: List<SubagentProfile>, name: String?): SubagentProfile? {
        val normalized = normalize(profiles)
        val requested = name?.trim().orEmpty()
        if (requested.isEmpty()) {
            return normalized.first { it.id == SubagentProfile.BUILT_IN_ID }
        }
        val key = nameKey(requested)
        return normalized.firstOrNull { nameKey(it.name) == key }
    }

    /** 变音折叠后的名字键：NFD 分解去组合标记，再按大小写折叠。 */
    fun nameKey(name: String): String {
        val decomposed = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
        return buildString {
            for (ch in decomposed) {
                val type = Character.getType(ch)
                val isCombiningMark =
                    type == Character.NON_SPACING_MARK.toInt() ||
                        type == Character.COMBINING_SPACING_MARK.toInt()
                if (!isCombiningMark) append(ch)
            }
        }.lowercase()
    }
}
