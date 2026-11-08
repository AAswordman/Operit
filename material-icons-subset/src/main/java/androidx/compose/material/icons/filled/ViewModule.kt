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

public val Icons.Filled.ViewModule: ImageVector
    get() {
        if (_viewModule != null) {
            return _viewModule!!
        }
        _viewModule = materialIcon(name = "Filled.ViewModule") {
            materialPath {
                moveTo(14.67f, 5.0f)
                verticalLineToRelative(6.5f)
                horizontalLineTo(9.33f)
                verticalLineTo(5.0f)
                horizontalLineTo(14.67f)
                close()
                moveTo(15.67f, 11.5f)
                horizontalLineTo(21.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(-5.33f)
                verticalLineTo(11.5f)
                close()
                moveTo(14.67f, 19.0f)
                verticalLineToRelative(-6.5f)
                horizontalLineTo(9.33f)
                verticalLineTo(19.0f)
                horizontalLineTo(14.67f)
                close()
                moveTo(15.67f, 12.5f)
                verticalLineTo(19.0f)
                horizontalLineTo(21.0f)
                verticalLineToRelative(-6.5f)
                horizontalLineTo(15.67f)
                close()
                moveTo(8.33f, 12.5f)
                horizontalLineTo(3.0f)
                verticalLineTo(19.0f)
                horizontalLineToRelative(5.33f)
                verticalLineTo(12.5f)
                close()
                moveTo(8.33f, 11.5f)
                verticalLineTo(5.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(6.5f)
                horizontalLineTo(8.33f)
                close()
            }
        }
        return _viewModule!!
    }

private var _viewModule: ImageVector? = null
