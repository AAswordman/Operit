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

public val Icons.Filled.Assistant: ImageVector
    get() {
        if (_assistant != null) {
            return _assistant!!
        }
        _assistant = materialIcon(name = "Filled.Assistant") {
            materialPath {
                moveTo(19.0f, 2.0f)
                lineTo(5.0f, 2.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(4.0f)
                lineToRelative(3.0f, 3.0f)
                lineToRelative(3.0f, -3.0f)
                horizontalLineToRelative(4.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(21.0f, 4.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(13.88f, 12.88f)
                lineTo(12.0f, 17.0f)
                lineToRelative(-1.88f, -4.12f)
                lineTo(6.0f, 11.0f)
                lineToRelative(4.12f, -1.88f)
                lineTo(12.0f, 5.0f)
                lineToRelative(1.88f, 4.12f)
                lineTo(18.0f, 11.0f)
                lineToRelative(-4.12f, 1.88f)
                close()
            }
        }
        return _assistant!!
    }

private var _assistant: ImageVector? = null
