package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.data.model.ChatMessage
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** UI callbacks still use display indices; the stored selection survives inserted history. */
internal class TranscriptSelection {
    var timestamps by mutableStateOf(emptySet<Long>())
        private set

    fun forMessages(messages: List<ChatMessage>): ReadWriteProperty<Any?, Set<Int>> =
        object : ReadWriteProperty<Any?, Set<Int>> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Set<Int> =
                messages.indices.filterTo(linkedSetOf()) { messages[it].timestamp in timestamps }

            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Set<Int>) {
                timestamps = value.mapNotNullTo(linkedSetOf()) { messages.getOrNull(it)?.timestamp }
            }
        }
}
