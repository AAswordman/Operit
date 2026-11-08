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

public val Icons.Filled.AppShortcut: ImageVector
    get() {
        if (_appShortcut != null) {
            return _appShortcut!!
        }
        _appShortcut = materialIcon(name = "Filled.AppShortcut") {
            materialPath {
                moveTo(17.0f, 18.0f)
                horizontalLineTo(7.0f)
                verticalLineTo(6.0f)
                horizontalLineToRelative(10.0f)
                verticalLineToRelative(1.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(3.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineTo(7.0f)
                curveTo(5.9f, 1.0f, 5.0f, 1.9f, 5.0f, 3.0f)
                verticalLineToRelative(18.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(10.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineTo(18.0f)
                close()
            }
            materialPath {
                moveTo(20.38f, 9.62f)
                lineToRelative(0.62f, 1.38f)
                lineToRelative(0.62f, -1.38f)
                lineToRelative(1.38f, -0.62f)
                lineToRelative(-1.38f, -0.62f)
                lineToRelative(-0.62f, -1.38f)
                lineToRelative(-0.62f, 1.38f)
                lineToRelative(-1.38f, 0.62f)
                close()
            }
            materialPath {
                moveTo(16.0f, 8.0f)
                lineToRelative(-1.25f, 2.75f)
                lineToRelative(-2.75f, 1.25f)
                lineToRelative(2.75f, 1.25f)
                lineToRelative(1.25f, 2.75f)
                lineToRelative(1.25f, -2.75f)
                lineToRelative(2.75f, -1.25f)
                lineToRelative(-2.75f, -1.25f)
                close()
            }
            materialPath {
                moveTo(21.0f, 13.0f)
                lineToRelative(-0.62f, 1.38f)
                lineToRelative(-1.38f, 0.62f)
                lineToRelative(1.38f, 0.62f)
                lineToRelative(0.62f, 1.38f)
                lineToRelative(0.62f, -1.38f)
                lineToRelative(1.38f, -0.62f)
                lineToRelative(-1.38f, -0.62f)
                close()
            }
        }
        return _appShortcut!!
    }

private var _appShortcut: ImageVector? = null
