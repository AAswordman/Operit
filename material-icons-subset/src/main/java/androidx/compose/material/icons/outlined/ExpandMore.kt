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

package androidx.compose.material.icons.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Outlined.ExpandMore: ImageVector
    get() {
        if (_expandMore != null) {
            return _expandMore!!
        }
        _expandMore = materialIcon(name = "Outlined.ExpandMore") {
            materialPath {
                moveTo(16.59f, 8.59f)
                lineTo(12.0f, 13.17f)
                lineTo(7.41f, 8.59f)
                lineTo(6.0f, 10.0f)
                lineToRelative(6.0f, 6.0f)
                lineToRelative(6.0f, -6.0f)
                lineToRelative(-1.41f, -1.41f)
                close()
            }
        }
        return _expandMore!!
    }

private var _expandMore: ImageVector? = null
