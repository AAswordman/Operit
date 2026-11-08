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

public val Icons.Filled.ScreenshotMonitor: ImageVector
    get() {
        if (_screenshotMonitor != null) {
            return _screenshotMonitor!!
        }
        _screenshotMonitor = materialIcon(name = "Filled.ScreenshotMonitor") {
            materialPath {
                moveTo(20.0f, 3.0f)
                horizontalLineTo(4.0f)
                curveTo(2.89f, 3.0f, 2.0f, 3.89f, 2.0f, 5.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(4.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(5.0f)
                curveTo(22.0f, 3.89f, 21.1f, 3.0f, 20.0f, 3.0f)
                close()
                moveTo(20.0f, 17.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(16.0f)
                verticalLineTo(17.0f)
                close()
            }
            materialPath {
                moveTo(6.5f, 7.5f)
                lineToRelative(2.5f, 0.0f)
                lineToRelative(0.0f, -1.5f)
                lineToRelative(-4.0f, 0.0f)
                lineToRelative(0.0f, 4.0f)
                lineToRelative(1.5f, 0.0f)
                close()
            }
            materialPath {
                moveTo(19.0f, 12.0f)
                lineToRelative(-1.5f, 0.0f)
                lineToRelative(0.0f, 2.5f)
                lineToRelative(-2.5f, 0.0f)
                lineToRelative(0.0f, 1.5f)
                lineToRelative(4.0f, 0.0f)
                close()
            }
        }
        return _screenshotMonitor!!
    }

private var _screenshotMonitor: ImageVector? = null
