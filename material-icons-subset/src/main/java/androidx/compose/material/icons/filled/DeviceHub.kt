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

public val Icons.Filled.DeviceHub: ImageVector
    get() {
        if (_deviceHub != null) {
            return _deviceHub!!
        }
        _deviceHub = materialIcon(name = "Filled.DeviceHub") {
            materialPath {
                moveTo(17.0f, 16.0f)
                lineToRelative(-4.0f, -4.0f)
                verticalLineTo(8.82f)
                curveTo(14.16f, 8.4f, 15.0f, 7.3f, 15.0f, 6.0f)
                curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                reflectiveCurveTo(9.0f, 4.34f, 9.0f, 6.0f)
                curveToRelative(0.0f, 1.3f, 0.84f, 2.4f, 2.0f, 2.82f)
                verticalLineTo(12.0f)
                lineToRelative(-4.0f, 4.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(5.0f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(-3.05f)
                lineToRelative(4.0f, -4.2f)
                lineToRelative(4.0f, 4.2f)
                verticalLineTo(21.0f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(-5.0f)
                horizontalLineToRelative(-4.0f)
                close()
            }
        }
        return _deviceHub!!
    }

private var _deviceHub: ImageVector? = null
