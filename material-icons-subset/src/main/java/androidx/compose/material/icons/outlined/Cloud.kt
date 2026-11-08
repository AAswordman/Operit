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

public val Icons.Outlined.Cloud: ImageVector
    get() {
        if (_cloud != null) {
            return _cloud!!
        }
        _cloud = materialIcon(name = "Outlined.Cloud") {
            materialPath {
                moveTo(12.0f, 6.0f)
                curveToRelative(2.62f, 0.0f, 4.88f, 1.86f, 5.39f, 4.43f)
                lineToRelative(0.3f, 1.5f)
                lineToRelative(1.53f, 0.11f)
                curveToRelative(1.56f, 0.1f, 2.78f, 1.41f, 2.78f, 2.96f)
                curveToRelative(0.0f, 1.65f, -1.35f, 3.0f, -3.0f, 3.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-2.21f, 0.0f, -4.0f, -1.79f, -4.0f, -4.0f)
                curveToRelative(0.0f, -2.05f, 1.53f, -3.76f, 3.56f, -3.97f)
                lineToRelative(1.07f, -0.11f)
                lineToRelative(0.5f, -0.95f)
                curveTo(8.08f, 7.14f, 9.94f, 6.0f, 12.0f, 6.0f)
                moveToRelative(0.0f, -2.0f)
                curveTo(9.11f, 4.0f, 6.6f, 5.64f, 5.35f, 8.04f)
                curveTo(2.34f, 8.36f, 0.0f, 10.91f, 0.0f, 14.0f)
                curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
                horizontalLineToRelative(13.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
                curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
                close()
            }
        }
        return _cloud!!
    }

private var _cloud: ImageVector? = null
