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

public val Icons.Filled.AutoMode: ImageVector
    get() {
        if (_autoMode != null) {
            return _autoMode!!
        }
        _autoMode = materialIcon(name = "Filled.AutoMode") {
            materialPath {
                moveTo(19.03f, 3.56f)
                curveToRelative(-1.67f, -1.39f, -3.74f, -2.3f, -6.03f, -2.51f)
                verticalLineToRelative(2.01f)
                curveToRelative(1.73f, 0.19f, 3.31f, 0.88f, 4.61f, 1.92f)
                lineTo(19.03f, 3.56f)
                close()
            }
            materialPath {
                moveTo(11.0f, 3.06f)
                verticalLineTo(1.05f)
                curveTo(8.71f, 1.25f, 6.64f, 2.17f, 4.97f, 3.56f)
                lineToRelative(1.42f, 1.42f)
                curveTo(7.69f, 3.94f, 9.27f, 3.25f, 11.0f, 3.06f)
                close()
            }
            materialPath {
                moveTo(4.98f, 6.39f)
                lineTo(3.56f, 4.97f)
                curveTo(2.17f, 6.64f, 1.26f, 8.71f, 1.05f, 11.0f)
                horizontalLineToRelative(2.01f)
                curveTo(3.25f, 9.27f, 3.94f, 7.69f, 4.98f, 6.39f)
                close()
            }
            materialPath {
                moveTo(20.94f, 11.0f)
                horizontalLineToRelative(2.01f)
                curveToRelative(-0.21f, -2.29f, -1.12f, -4.36f, -2.51f, -6.03f)
                lineToRelative(-1.42f, 1.42f)
                curveTo(20.06f, 7.69f, 20.75f, 9.27f, 20.94f, 11.0f)
                close()
            }
            materialPath {
                moveTo(7.0f, 12.0f)
                lineToRelative(3.44f, 1.56f)
                lineToRelative(1.56f, 3.44f)
                lineToRelative(1.56f, -3.44f)
                lineToRelative(3.44f, -1.56f)
                lineToRelative(-3.44f, -1.56f)
                lineToRelative(-1.56f, -3.44f)
                lineToRelative(-1.56f, 3.44f)
                close()
            }
            materialPath {
                moveTo(12.0f, 21.0f)
                curveToRelative(-3.11f, 0.0f, -5.85f, -1.59f, -7.46f, -4.0f)
                horizontalLineTo(7.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(1.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.7f)
                curveToRelative(1.99f, 2.84f, 5.27f, 4.7f, 9.0f, 4.7f)
                curveToRelative(4.87f, 0.0f, 9.0f, -3.17f, 10.44f, -7.56f)
                lineToRelative(-1.96f, -0.45f)
                curveTo(19.25f, 18.48f, 15.92f, 21.0f, 12.0f, 21.0f)
                close()
            }
        }
        return _autoMode!!
    }

private var _autoMode: ImageVector? = null
