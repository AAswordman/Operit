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

public val Icons.Filled.DarkMode: ImageVector
    get() {
        if (_darkMode != null) {
            return _darkMode!!
        }
        _darkMode = materialIcon(name = "Filled.DarkMode") {
            materialPath {
                moveTo(12.0f, 3.0f)
                curveToRelative(-4.97f, 0.0f, -9.0f, 4.03f, -9.0f, 9.0f)
                reflectiveCurveToRelative(4.03f, 9.0f, 9.0f, 9.0f)
                reflectiveCurveToRelative(9.0f, -4.03f, 9.0f, -9.0f)
                curveToRelative(0.0f, -0.46f, -0.04f, -0.92f, -0.1f, -1.36f)
                curveToRelative(-0.98f, 1.37f, -2.58f, 2.26f, -4.4f, 2.26f)
                curveToRelative(-2.98f, 0.0f, -5.4f, -2.42f, -5.4f, -5.4f)
                curveToRelative(0.0f, -1.81f, 0.89f, -3.42f, 2.26f, -4.4f)
                curveTo(12.92f, 3.04f, 12.46f, 3.0f, 12.0f, 3.0f)
                lineTo(12.0f, 3.0f)
                close()
            }
        }
        return _darkMode!!
    }

private var _darkMode: ImageVector? = null
