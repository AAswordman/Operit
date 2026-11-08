package com.ai.assistance.operit.ui.common.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 复用供应商素材与现有加载逻辑；没有已登记素材时不增加占位图标。 */
@Composable
fun ProviderLogo(
    providerTypeId: String?,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    val painter = rememberProviderLogoPainter(providerTypeId, size)
    if (painter != null) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = modifier.size(size),
            colorFilter = providerLogoColorFilter(),
        )
    }
}