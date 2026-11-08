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

public val Icons.Filled.Public: ImageVector
    get() {
        if (_public != null) {
            return _public!!
        }
        _public = materialIcon(name = "Filled.Public") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(11.0f, 19.93f)
                curveToRelative(-3.95f, -0.49f, -7.0f, -3.85f, -7.0f, -7.93f)
                curveToRelative(0.0f, -0.62f, 0.08f, -1.21f, 0.21f, -1.79f)
                lineTo(9.0f, 15.0f)
                verticalLineToRelative(1.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                verticalLineToRelative(1.93f)
                close()
                moveTo(17.9f, 17.39f)
                curveToRelative(-0.26f, -0.81f, -1.0f, -1.39f, -1.9f, -1.39f)
                horizontalLineToRelative(-1.0f)
                verticalLineToRelative(-3.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                lineTo(8.0f, 12.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                lineTo(11.0f, 7.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-0.41f)
                curveToRelative(2.93f, 1.19f, 5.0f, 4.06f, 5.0f, 7.41f)
                curveToRelative(0.0f, 2.08f, -0.8f, 3.97f, -2.1f, 5.39f)
                close()
            }
        }
        return _public!!
    }

private var _public: ImageVector? = null
