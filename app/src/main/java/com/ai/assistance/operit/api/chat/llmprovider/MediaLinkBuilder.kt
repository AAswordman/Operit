package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.R

object MediaLinkBuilder {
    fun image(context: Context, id: String): String {
        return context.getString(R.string.conversation_media_image_link, id)
    }

    fun audio(context: Context, id: String): String {
        return context.getString(R.string.conversation_media_audio_link, id)
    }

    fun video(context: Context, id: String): String {
        return context.getString(R.string.conversation_media_video_link, id)
    }

    fun file(context: Context, id: String, fileName: String): String {
        return context.getString(
            R.string.conversation_media_file_link,
            id,
            escapeXmlAttribute(fileName),
        )
    }

    private fun escapeXmlAttribute(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
