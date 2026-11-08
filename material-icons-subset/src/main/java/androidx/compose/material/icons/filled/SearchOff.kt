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

public val Icons.Filled.SearchOff: ImageVector
    get() {
        if (_searchOff != null) {
            return _searchOff!!
        }
        _searchOff = materialIcon(name = "Filled.SearchOff") {
            materialPath {
                moveTo(15.5f, 14.0f)
                horizontalLineToRelative(-0.79f)
                lineToRelative(-0.28f, -0.27f)
                curveTo(15.41f, 12.59f, 16.0f, 11.11f, 16.0f, 9.5f)
                curveTo(16.0f, 5.91f, 13.09f, 3.0f, 9.5f, 3.0f)
                curveTo(6.08f, 3.0f, 3.28f, 5.64f, 3.03f, 9.0f)
                horizontalLineToRelative(2.02f)
                curveTo(5.3f, 6.75f, 7.18f, 5.0f, 9.5f, 5.0f)
                curveTo(11.99f, 5.0f, 14.0f, 7.01f, 14.0f, 9.5f)
                reflectiveCurveTo(11.99f, 14.0f, 9.5f, 14.0f)
                curveToRelative(-0.17f, 0.0f, -0.33f, -0.03f, -0.5f, -0.05f)
                verticalLineToRelative(2.02f)
                curveTo(9.17f, 15.99f, 9.33f, 16.0f, 9.5f, 16.0f)
                curveToRelative(1.61f, 0.0f, 3.09f, -0.59f, 4.23f, -1.57f)
                lineTo(14.0f, 14.71f)
                verticalLineToRelative(0.79f)
                lineToRelative(5.0f, 4.99f)
                lineTo(20.49f, 19.0f)
                lineTo(15.5f, 14.0f)
                close()
            }
            materialPath {
                moveTo(6.47f, 10.82f)
                lineToRelative(-2.47f, 2.47f)
                lineToRelative(-2.47f, -2.47f)
                lineToRelative(-0.71f, 0.71f)
                lineToRelative(2.47f, 2.47f)
                lineToRelative(-2.47f, 2.47f)
                lineToRelative(0.71f, 0.71f)
                lineToRelative(2.47f, -2.47f)
                lineToRelative(2.47f, 2.47f)
                lineToRelative(0.71f, -0.71f)
                lineToRelative(-2.47f, -2.47f)
                lineToRelative(2.47f, -2.47f)
                close()
            }
        }
        return _searchOff!!
    }

private var _searchOff: ImageVector? = null
