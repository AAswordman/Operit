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

public val Icons.Filled.MyLocation: ImageVector
    get() {
        if (_myLocation != null) {
            return _myLocation!!
        }
        _myLocation = materialIcon(name = "Filled.MyLocation") {
            materialPath {
                moveTo(12.0f, 8.0f)
                curveToRelative(-2.21f, 0.0f, -4.0f, 1.79f, -4.0f, 4.0f)
                reflectiveCurveToRelative(1.79f, 4.0f, 4.0f, 4.0f)
                reflectiveCurveToRelative(4.0f, -1.79f, 4.0f, -4.0f)
                reflectiveCurveToRelative(-1.79f, -4.0f, -4.0f, -4.0f)
                close()
                moveTo(20.94f, 11.0f)
                curveToRelative(-0.46f, -4.17f, -3.77f, -7.48f, -7.94f, -7.94f)
                lineTo(13.0f, 1.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.06f)
                curveTo(6.83f, 3.52f, 3.52f, 6.83f, 3.06f, 11.0f)
                lineTo(1.0f, 11.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.06f)
                curveToRelative(0.46f, 4.17f, 3.77f, 7.48f, 7.94f, 7.94f)
                lineTo(11.0f, 23.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.06f)
                curveToRelative(4.17f, -0.46f, 7.48f, -3.77f, 7.94f, -7.94f)
                lineTo(23.0f, 13.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.06f)
                close()
                moveTo(12.0f, 19.0f)
                curveToRelative(-3.87f, 0.0f, -7.0f, -3.13f, -7.0f, -7.0f)
                reflectiveCurveToRelative(3.13f, -7.0f, 7.0f, -7.0f)
                reflectiveCurveToRelative(7.0f, 3.13f, 7.0f, 7.0f)
                reflectiveCurveToRelative(-3.13f, 7.0f, -7.0f, 7.0f)
                close()
            }
        }
        return _myLocation!!
    }

private var _myLocation: ImageVector? = null
