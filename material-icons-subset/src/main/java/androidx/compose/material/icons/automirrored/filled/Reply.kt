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

package androidx.compose.material.icons.automirrored.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.AutoMirrored.Filled.Reply: ImageVector
    get() {
        if (_reply != null) {
            return _reply!!
        }
        _reply = materialIcon(name = "AutoMirrored.Filled.Reply", autoMirror = true) {
            materialPath {
                moveTo(10.0f, 9.0f)
                verticalLineTo(5.0f)
                lineToRelative(-7.0f, 7.0f)
                lineToRelative(7.0f, 7.0f)
                verticalLineToRelative(-4.1f)
                curveToRelative(5.0f, 0.0f, 8.5f, 1.6f, 11.0f, 5.1f)
                curveToRelative(-1.0f, -5.0f, -4.0f, -10.0f, -11.0f, -11.0f)
                close()
            }
        }
        return _reply!!
    }

private var _reply: ImageVector? = null
