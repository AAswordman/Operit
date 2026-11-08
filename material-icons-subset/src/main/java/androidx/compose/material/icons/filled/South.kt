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

public val Icons.Filled.South: ImageVector
    get() {
        if (_south != null) {
            return _south!!
        }
        _south = materialIcon(name = "Filled.South") {
            materialPath {
                moveTo(19.0f, 15.0f)
                lineToRelative(-1.41f, -1.41f)
                lineTo(13.0f, 18.17f)
                verticalLineTo(2.0f)
                horizontalLineTo(11.0f)
                verticalLineToRelative(16.17f)
                lineToRelative(-4.59f, -4.59f)
                lineTo(5.0f, 15.0f)
                lineToRelative(7.0f, 7.0f)
                lineTo(19.0f, 15.0f)
                close()
            }
        }
        return _south!!
    }

private var _south: ImageVector? = null
