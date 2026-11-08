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

public val Icons.Filled.Archive: ImageVector
    get() {
        if (_archive != null) {
            return _archive!!
        }
        _archive = materialIcon(name = "Filled.Archive") {
            materialPath {
                moveTo(20.54f, 5.23f)
                lineToRelative(-1.39f, -1.68f)
                curveTo(18.88f, 3.21f, 18.47f, 3.0f, 18.0f, 3.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-0.47f, 0.0f, -0.88f, 0.21f, -1.16f, 0.55f)
                lineTo(3.46f, 5.23f)
                curveTo(3.17f, 5.57f, 3.0f, 6.02f, 3.0f, 6.5f)
                verticalLineTo(19.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(6.5f)
                curveToRelative(0.0f, -0.48f, -0.17f, -0.93f, -0.46f, -1.27f)
                close()
                moveTo(12.0f, 17.5f)
                lineTo(6.5f, 12.0f)
                horizontalLineTo(10.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(3.5f)
                lineTo(12.0f, 17.5f)
                close()
                moveTo(5.12f, 5.0f)
                lineToRelative(0.81f, -1.0f)
                horizontalLineToRelative(12.0f)
                lineToRelative(0.94f, 1.0f)
                horizontalLineTo(5.12f)
                close()
            }
        }
        return _archive!!
    }

private var _archive: ImageVector? = null
