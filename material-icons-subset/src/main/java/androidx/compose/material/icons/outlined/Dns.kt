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

public val Icons.Outlined.Dns: ImageVector
    get() {
        if (_dns != null) {
            return _dns!!
        }
        _dns = materialIcon(name = "Outlined.Dns") {
            materialPath {
                moveTo(19.0f, 15.0f)
                verticalLineToRelative(4.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(14.0f)
                moveToRelative(1.0f, -2.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(6.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-6.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                close()
                moveTo(7.0f, 18.5f)
                curveToRelative(-0.82f, 0.0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveToRelative(0.68f, -1.5f, 1.5f, -1.5f)
                reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
                reflectiveCurveToRelative(-0.67f, 1.5f, -1.5f, 1.5f)
                close()
                moveTo(19.0f, 5.0f)
                verticalLineToRelative(4.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(14.0f)
                moveToRelative(1.0f, -2.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(6.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineTo(4.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                close()
                moveTo(7.0f, 8.5f)
                curveToRelative(-0.82f, 0.0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(6.18f, 5.5f, 7.0f, 5.5f)
                reflectiveCurveToRelative(1.5f, 0.68f, 1.5f, 1.5f)
                reflectiveCurveTo(7.83f, 8.5f, 7.0f, 8.5f)
                close()
            }
        }
        return _dns!!
    }

private var _dns: ImageVector? = null
