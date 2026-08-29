package com.ai.assistance.operit.ui.main.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Displays a header in the navigation drawer */
@Composable
fun NavigationDrawerItemHeader(title: String, appearance: NavigationDrawerAppearance) {
    Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = appearance.titleColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp)
    )
}
