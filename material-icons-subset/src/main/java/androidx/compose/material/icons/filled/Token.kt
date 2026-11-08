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

public val Icons.Filled.Token: ImageVector
    get() {
        if (_token != null) {
            return _token!!
        }
        _token = materialIcon(name = "Filled.Token") {
            materialPath {
                moveTo(19.97f, 6.43f)
                lineTo(12.0f, 2.0f)
                lineTo(4.03f, 6.43f)
                lineTo(9.1f, 9.24f)
                curveTo(9.83f, 8.48f, 10.86f, 8.0f, 12.0f, 8.0f)
                reflectiveCurveToRelative(2.17f, 0.48f, 2.9f, 1.24f)
                lineTo(19.97f, 6.43f)
                close()
                moveTo(10.0f, 12.0f)
                curveToRelative(0.0f, -1.1f, 0.9f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                reflectiveCurveToRelative(-0.9f, 2.0f, -2.0f, 2.0f)
                reflectiveCurveTo(10.0f, 13.1f, 10.0f, 12.0f)
                close()
                moveTo(11.0f, 21.44f)
                lineTo(3.0f, 17.0f)
                verticalLineTo(8.14f)
                lineToRelative(5.13f, 2.85f)
                curveTo(8.04f, 11.31f, 8.0f, 11.65f, 8.0f, 12.0f)
                curveToRelative(0.0f, 1.86f, 1.27f, 3.43f, 3.0f, 3.87f)
                verticalLineTo(21.44f)
                close()
                moveTo(13.0f, 21.44f)
                verticalLineToRelative(-5.57f)
                curveToRelative(1.73f, -0.44f, 3.0f, -2.01f, 3.0f, -3.87f)
                curveToRelative(0.0f, -0.35f, -0.04f, -0.69f, -0.13f, -1.01f)
                lineTo(21.0f, 8.14f)
                lineTo(21.0f, 17.0f)
                lineTo(13.0f, 21.44f)
                close()
            }
        }
        return _token!!
    }

private var _token: ImageVector? = null
