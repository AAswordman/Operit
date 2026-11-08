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

public val Icons.Filled.North: ImageVector
    get() {
        if (_north != null) {
            return _north!!
        }
        _north = materialIcon(name = "Filled.North") {
            materialPath {
                moveTo(5.0f, 9.0f)
                lineToRelative(1.41f, 1.41f)
                lineTo(11.0f, 5.83f)
                verticalLineTo(22.0f)
                horizontalLineTo(13.0f)
                verticalLineTo(5.83f)
                lineToRelative(4.59f, 4.59f)
                lineTo(19.0f, 9.0f)
                lineToRelative(-7.0f, -7.0f)
                lineTo(5.0f, 9.0f)
                close()
            }
        }
        return _north!!
    }

private var _north: ImageVector? = null
