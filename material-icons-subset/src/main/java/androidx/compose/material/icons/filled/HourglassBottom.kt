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

public val Icons.Filled.HourglassBottom: ImageVector
    get() {
        if (_hourglassBottom != null) {
            return _hourglassBottom!!
        }
        _hourglassBottom = materialIcon(name = "Filled.HourglassBottom") {
            materialPath {
                moveTo(18.0f, 22.0f)
                lineToRelative(-0.01f, -6.0f)
                lineTo(14.0f, 12.0f)
                lineToRelative(3.99f, -4.01f)
                lineTo(18.0f, 2.0f)
                horizontalLineTo(6.0f)
                verticalLineToRelative(6.0f)
                lineToRelative(4.0f, 4.0f)
                lineToRelative(-4.0f, 3.99f)
                verticalLineTo(22.0f)
                horizontalLineTo(18.0f)
                close()
                moveTo(8.0f, 7.5f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(3.5f)
                lineToRelative(-4.0f, 4.0f)
                lineTo(8.0f, 7.5f)
                close()
            }
        }
        return _hourglassBottom!!
    }

private var _hourglassBottom: ImageVector? = null
