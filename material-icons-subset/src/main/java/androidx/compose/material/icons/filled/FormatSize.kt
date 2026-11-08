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

public val Icons.Filled.FormatSize: ImageVector
    get() {
        if (_formatSize != null) {
            return _formatSize!!
        }
        _formatSize = materialIcon(name = "Filled.FormatSize") {
            materialPath {
                moveTo(9.0f, 4.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(12.0f)
                horizontalLineToRelative(3.0f)
                lineTo(17.0f, 7.0f)
                horizontalLineToRelative(5.0f)
                lineTo(22.0f, 4.0f)
                lineTo(9.0f, 4.0f)
                close()
                moveTo(3.0f, 12.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(7.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(-7.0f)
                horizontalLineToRelative(3.0f)
                lineTo(12.0f, 9.0f)
                lineTo(3.0f, 9.0f)
                verticalLineToRelative(3.0f)
                close()
            }
        }
        return _formatSize!!
    }

private var _formatSize: ImageVector? = null
