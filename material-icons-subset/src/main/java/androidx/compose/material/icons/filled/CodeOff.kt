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

public val Icons.Filled.CodeOff: ImageVector
    get() {
        if (_codeOff != null) {
            return _codeOff!!
        }
        _codeOff = materialIcon(name = "Filled.CodeOff") {
            materialPath {
                moveTo(19.17f, 12.0f)
                lineToRelative(-4.58f, -4.59f)
                lineTo(16.0f, 6.0f)
                lineToRelative(6.0f, 6.0f)
                lineToRelative(-3.59f, 3.59f)
                lineTo(17.0f, 14.17f)
                lineTo(19.17f, 12.0f)
                close()
                moveTo(1.39f, 4.22f)
                lineToRelative(4.19f, 4.19f)
                lineTo(2.0f, 12.0f)
                lineToRelative(6.0f, 6.0f)
                lineToRelative(1.41f, -1.41f)
                lineTo(4.83f, 12.0f)
                lineTo(7.0f, 9.83f)
                lineToRelative(12.78f, 12.78f)
                lineToRelative(1.41f, -1.41f)
                lineTo(2.81f, 2.81f)
                lineTo(1.39f, 4.22f)
                close()
            }
        }
        return _codeOff!!
    }

private var _codeOff: ImageVector? = null
