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

public val Icons.Outlined.PieChart: ImageVector
    get() {
        if (_pieChart != null) {
            return _pieChart!!
        }
        _pieChart = materialIcon(name = "Outlined.PieChart") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(19.93f, 11.0f)
                lineTo(13.0f, 11.0f)
                lineTo(13.0f, 4.07f)
                curveToRelative(3.61f, 0.45f, 6.48f, 3.32f, 6.93f, 6.93f)
                close()
                moveTo(4.0f, 12.0f)
                curveToRelative(0.0f, -4.07f, 3.06f, -7.44f, 7.0f, -7.93f)
                verticalLineToRelative(15.86f)
                curveToRelative(-3.94f, -0.49f, -7.0f, -3.86f, -7.0f, -7.93f)
                close()
                moveTo(13.0f, 19.93f)
                lineTo(13.0f, 13.0f)
                horizontalLineToRelative(6.93f)
                curveToRelative(-0.45f, 3.61f, -3.32f, 6.48f, -6.93f, 6.93f)
                close()
            }
        }
        return _pieChart!!
    }

private var _pieChart: ImageVector? = null
