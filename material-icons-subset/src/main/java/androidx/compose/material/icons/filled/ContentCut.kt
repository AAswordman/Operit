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

public val Icons.Filled.ContentCut: ImageVector
    get() {
        if (_contentCut != null) {
            return _contentCut!!
        }
        _contentCut = materialIcon(name = "Filled.ContentCut") {
            materialPath {
                moveTo(9.64f, 7.64f)
                curveToRelative(0.23f, -0.5f, 0.36f, -1.05f, 0.36f, -1.64f)
                curveToRelative(0.0f, -2.21f, -1.79f, -4.0f, -4.0f, -4.0f)
                reflectiveCurveTo(2.0f, 3.79f, 2.0f, 6.0f)
                reflectiveCurveToRelative(1.79f, 4.0f, 4.0f, 4.0f)
                curveToRelative(0.59f, 0.0f, 1.14f, -0.13f, 1.64f, -0.36f)
                lineTo(10.0f, 12.0f)
                lineToRelative(-2.36f, 2.36f)
                curveTo(7.14f, 14.13f, 6.59f, 14.0f, 6.0f, 14.0f)
                curveToRelative(-2.21f, 0.0f, -4.0f, 1.79f, -4.0f, 4.0f)
                reflectiveCurveToRelative(1.79f, 4.0f, 4.0f, 4.0f)
                reflectiveCurveToRelative(4.0f, -1.79f, 4.0f, -4.0f)
                curveToRelative(0.0f, -0.59f, -0.13f, -1.14f, -0.36f, -1.64f)
                lineTo(12.0f, 14.0f)
                lineToRelative(7.0f, 7.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(-1.0f)
                lineTo(9.64f, 7.64f)
                close()
                moveTo(6.0f, 8.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, -0.89f, -2.0f, -2.0f)
                reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveToRelative(2.0f, 0.89f, 2.0f, 2.0f)
                reflectiveCurveToRelative(-0.9f, 2.0f, -2.0f, 2.0f)
                close()
                moveTo(6.0f, 20.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, -0.89f, -2.0f, -2.0f)
                reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveToRelative(2.0f, 0.89f, 2.0f, 2.0f)
                reflectiveCurveToRelative(-0.9f, 2.0f, -2.0f, 2.0f)
                close()
                moveTo(12.0f, 12.5f)
                curveToRelative(-0.28f, 0.0f, -0.5f, -0.22f, -0.5f, -0.5f)
                reflectiveCurveToRelative(0.22f, -0.5f, 0.5f, -0.5f)
                reflectiveCurveToRelative(0.5f, 0.22f, 0.5f, 0.5f)
                reflectiveCurveToRelative(-0.22f, 0.5f, -0.5f, 0.5f)
                close()
                moveTo(19.0f, 3.0f)
                lineToRelative(-6.0f, 6.0f)
                lineToRelative(2.0f, 2.0f)
                lineToRelative(7.0f, -7.0f)
                lineTo(22.0f, 3.0f)
                close()
            }
        }
        return _contentCut!!
    }

private var _contentCut: ImageVector? = null
