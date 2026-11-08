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

public val Icons.Outlined.Extension: ImageVector
    get() {
        if (_extension != null) {
            return _extension!!
        }
        _extension = materialIcon(name = "Outlined.Extension") {
            materialPath {
                moveTo(10.5f, 4.5f)
                curveToRelative(0.28f, 0.0f, 0.5f, 0.22f, 0.5f, 0.5f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(6.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.28f, 0.0f, 0.5f, 0.22f, 0.5f, 0.5f)
                reflectiveCurveToRelative(-0.22f, 0.5f, -0.5f, 0.5f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(-2.12f)
                curveToRelative(-0.68f, -1.75f, -2.39f, -3.0f, -4.38f, -3.0f)
                reflectiveCurveToRelative(-3.7f, 1.25f, -4.38f, 3.0f)
                horizontalLineTo(4.0f)
                verticalLineToRelative(-2.12f)
                curveToRelative(1.75f, -0.68f, 3.0f, -2.39f, 3.0f, -4.38f)
                curveToRelative(0.0f, -1.99f, -1.24f, -3.7f, -2.99f, -4.38f)
                lineTo(4.0f, 7.0f)
                horizontalLineToRelative(6.0f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -0.28f, 0.22f, -0.5f, 0.5f, -0.5f)
                moveToRelative(0.0f, -2.0f)
                curveTo(9.12f, 2.5f, 8.0f, 3.62f, 8.0f, 5.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                verticalLineToRelative(3.8f)
                horizontalLineToRelative(0.29f)
                curveToRelative(1.49f, 0.0f, 2.7f, 1.21f, 2.7f, 2.7f)
                reflectiveCurveToRelative(-1.21f, 2.7f, -2.7f, 2.7f)
                horizontalLineTo(2.0f)
                verticalLineTo(20.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(3.8f)
                verticalLineToRelative(-0.3f)
                curveToRelative(0.0f, -1.49f, 1.21f, -2.7f, 2.7f, -2.7f)
                reflectiveCurveToRelative(2.7f, 1.21f, 2.7f, 2.7f)
                verticalLineToRelative(0.3f)
                horizontalLineTo(17.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-4.0f)
                curveToRelative(1.38f, 0.0f, 2.5f, -1.12f, 2.5f, -2.5f)
                reflectiveCurveTo(20.38f, 11.0f, 19.0f, 11.0f)
                verticalLineTo(7.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(-4.0f)
                curveToRelative(0.0f, -1.38f, -1.12f, -2.5f, -2.5f, -2.5f)
                close()
            }
        }
        return _extension!!
    }

private var _extension: ImageVector? = null
