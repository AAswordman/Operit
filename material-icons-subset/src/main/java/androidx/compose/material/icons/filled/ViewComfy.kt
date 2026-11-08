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

public val Icons.Filled.ViewComfy: ImageVector
    get() {
        if (_viewComfy != null) {
            return _viewComfy!!
        }
        _viewComfy = materialIcon(name = "Filled.ViewComfy") {
            materialPath {
                moveTo(2.0f, 4.0f)
                verticalLineToRelative(7.0f)
                horizontalLineToRelative(20.0f)
                verticalLineTo(4.0f)
                horizontalLineTo(2.0f)
                close()
                moveTo(10.0f, 20.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(-7.0f)
                horizontalLineTo(10.0f)
                verticalLineTo(20.0f)
                close()
                moveTo(2.0f, 20.0f)
                horizontalLineToRelative(6.0f)
                verticalLineToRelative(-7.0f)
                horizontalLineTo(2.0f)
                verticalLineTo(20.0f)
                close()
            }
        }
        return _viewComfy!!
    }

private var _viewComfy: ImageVector? = null
