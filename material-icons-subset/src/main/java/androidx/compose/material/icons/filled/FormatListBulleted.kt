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
import kotlin.Deprecated

@Deprecated(
    "Use the AutoMirrored version at Icons.AutoMirrored.Filled.FormatListBulleted",
    ReplaceWith( "Icons.AutoMirrored.Filled.FormatListBulleted",
            "androidx.compose.material.icons.automirrored.filled.FormatListBulleted"),
)
public val Icons.Filled.FormatListBulleted: ImageVector
    get() {
        if (_formatListBulleted != null) {
            return _formatListBulleted!!
        }
        _formatListBulleted = materialIcon(name = "Filled.FormatListBulleted") {
            materialPath {
                moveTo(4.0f, 10.5f)
                curveToRelative(-0.83f, 0.0f, -1.5f, 0.67f, -1.5f, 1.5f)
                reflectiveCurveToRelative(0.67f, 1.5f, 1.5f, 1.5f)
                reflectiveCurveToRelative(1.5f, -0.67f, 1.5f, -1.5f)
                reflectiveCurveToRelative(-0.67f, -1.5f, -1.5f, -1.5f)
                close()
                moveTo(4.0f, 4.5f)
                curveToRelative(-0.83f, 0.0f, -1.5f, 0.67f, -1.5f, 1.5f)
                reflectiveCurveTo(3.17f, 7.5f, 4.0f, 7.5f)
                reflectiveCurveTo(5.5f, 6.83f, 5.5f, 6.0f)
                reflectiveCurveTo(4.83f, 4.5f, 4.0f, 4.5f)
                close()
                moveTo(4.0f, 16.5f)
                curveToRelative(-0.83f, 0.0f, -1.5f, 0.68f, -1.5f, 1.5f)
                reflectiveCurveToRelative(0.68f, 1.5f, 1.5f, 1.5f)
                reflectiveCurveToRelative(1.5f, -0.68f, 1.5f, -1.5f)
                reflectiveCurveToRelative(-0.67f, -1.5f, -1.5f, -1.5f)
                close()
                moveTo(7.0f, 19.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(-2.0f)
                lineTo(7.0f, 17.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(7.0f, 13.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(-2.0f)
                lineTo(7.0f, 11.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(7.0f, 5.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(14.0f)
                lineTo(21.0f, 5.0f)
                lineTo(7.0f, 5.0f)
                close()
            }
        }
        return _formatListBulleted!!
    }

private var _formatListBulleted: ImageVector? = null
