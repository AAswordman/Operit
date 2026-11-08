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

public val Icons.Filled.FullscreenExit: ImageVector
    get() {
        if (_fullscreenExit != null) {
            return _fullscreenExit!!
        }
        _fullscreenExit = materialIcon(name = "Filled.FullscreenExit") {
            materialPath {
                moveTo(5.0f, 16.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-5.0f)
                lineTo(5.0f, 14.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(8.0f, 8.0f)
                lineTo(5.0f, 8.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(5.0f)
                lineTo(10.0f, 5.0f)
                lineTo(8.0f, 5.0f)
                verticalLineToRelative(3.0f)
                close()
                moveTo(14.0f, 19.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-3.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-5.0f)
                verticalLineToRelative(5.0f)
                close()
                moveTo(16.0f, 8.0f)
                lineTo(16.0f, 5.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(5.0f)
                horizontalLineToRelative(5.0f)
                lineTo(19.0f, 8.0f)
                horizontalLineToRelative(-3.0f)
                close()
            }
        }
        return _fullscreenExit!!
    }

private var _fullscreenExit: ImageVector? = null
