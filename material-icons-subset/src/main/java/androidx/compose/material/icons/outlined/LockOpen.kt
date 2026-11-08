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

public val Icons.Outlined.LockOpen: ImageVector
    get() {
        if (_lockOpen != null) {
            return _lockOpen!!
        }
        _lockOpen = materialIcon(name = "Outlined.LockOpen") {
            materialPath {
                moveTo(18.0f, 8.0f)
                horizontalLineToRelative(-1.0f)
                lineTo(17.0f, 6.0f)
                curveToRelative(0.0f, -2.76f, -2.24f, -5.0f, -5.0f, -5.0f)
                reflectiveCurveTo(7.0f, 3.24f, 7.0f, 6.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.0f, -1.66f, 1.34f, -3.0f, 3.0f, -3.0f)
                reflectiveCurveToRelative(3.0f, 1.34f, 3.0f, 3.0f)
                verticalLineToRelative(2.0f)
                lineTo(6.0f, 8.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(10.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(20.0f, 10.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(18.0f, 20.0f)
                lineTo(6.0f, 20.0f)
                lineTo(6.0f, 10.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(10.0f)
                close()
                moveTo(12.0f, 17.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                reflectiveCurveToRelative(-2.0f, 0.9f, -2.0f, 2.0f)
                reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                close()
            }
        }
        return _lockOpen!!
    }

private var _lockOpen: ImageVector? = null
