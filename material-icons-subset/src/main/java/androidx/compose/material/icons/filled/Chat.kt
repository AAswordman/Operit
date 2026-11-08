/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.material.icons.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.Deprecated

@Deprecated(
    "Use the AutoMirrored version at Icons.AutoMirrored.Filled.Chat",
    ReplaceWith( "Icons.AutoMirrored.Filled.Chat",
            "androidx.compose.material.icons.automirrored.filled.Chat"),
)
public val Icons.Filled.Chat: ImageVector
    get() {
        if (_chat != null) {
            return _chat!!
        }
        _chat = materialIcon(name = "Filled.Chat") {
            materialPath {
                moveTo(20.0f, 2.0f)
                lineTo(4.0f, 2.0f)
                curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                lineTo(2.0f, 22.0f)
                lineToRelative(4.0f, -4.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(22.0f, 4.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(6.0f, 9.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(2.0f)
                lineTo(6.0f, 11.0f)
                lineTo(6.0f, 9.0f)
                close()
                moveTo(14.0f, 14.0f)
                lineTo(6.0f, 14.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(18.0f, 8.0f)
                lineTo(6.0f, 8.0f)
                lineTo(6.0f, 6.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(2.0f)
                close()
            }
        }
        return _chat!!
    }

private var _chat: ImageVector? = null
