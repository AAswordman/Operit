package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.oktoast.OKToast

/** 同一消息的过期图片只占一个紧凑入口，数量使用右上角数字角标。 */
@Composable
internal fun ExpiredImageGroupTag(count: Int, enabled: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val message = stringResource(R.string.chat_media_file_expired)
    val description = stringResource(R.string.sent_image_expired_count, count)
    Box(
        modifier.clickable(enabled = enabled, role = Role.Button) {
            OKToast.warning(context, message).show()
        }.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Surface(
            modifier = Modifier.padding(top = 4.dp, end = 5.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(
                stringResource(R.string.image),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).defaultMinSize(minWidth = 14.dp, minHeight = 14.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Box(Modifier.padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
                Text(count.toString(), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, lineHeight = 12.sp)
            }
        }
    }
}
