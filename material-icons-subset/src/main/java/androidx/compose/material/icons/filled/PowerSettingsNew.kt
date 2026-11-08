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

public val Icons.Filled.PowerSettingsNew: ImageVector
    get() {
        if (_powerSettingsNew != null) {
            return _powerSettingsNew!!
        }
        _powerSettingsNew = materialIcon(name = "Filled.PowerSettingsNew") {
            materialPath {
                moveTo(13.0f, 3.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(10.0f)
                horizontalLineToRelative(2.0f)
                lineTo(13.0f, 3.0f)
                close()
                moveTo(17.83f, 5.17f)
                lineToRelative(-1.42f, 1.42f)
                curveTo(17.99f, 7.86f, 19.0f, 9.81f, 19.0f, 12.0f)
                curveToRelative(0.0f, 3.87f, -3.13f, 7.0f, -7.0f, 7.0f)
                reflectiveCurveToRelative(-7.0f, -3.13f, -7.0f, -7.0f)
                curveToRelative(0.0f, -2.19f, 1.01f, -4.14f, 2.58f, -5.42f)
                lineTo(6.17f, 5.17f)
                curveTo(4.23f, 6.82f, 3.0f, 9.26f, 3.0f, 12.0f)
                curveToRelative(0.0f, 4.97f, 4.03f, 9.0f, 9.0f, 9.0f)
                reflectiveCurveToRelative(9.0f, -4.03f, 9.0f, -9.0f)
                curveToRelative(0.0f, -2.74f, -1.23f, -5.18f, -3.17f, -6.83f)
                close()
            }
        }
        return _powerSettingsNew!!
    }

private var _powerSettingsNew: ImageVector? = null
