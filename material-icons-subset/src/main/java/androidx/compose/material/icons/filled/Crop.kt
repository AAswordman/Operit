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

public val Icons.Filled.Crop: ImageVector
    get() {
        if (_crop != null) {
            return _crop!!
        }
        _crop = materialIcon(name = "Filled.Crop") {
            materialPath {
                moveTo(17.0f, 15.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(7.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineTo(9.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(8.0f)
                close()
                moveTo(7.0f, 17.0f)
                verticalLineTo(1.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(4.0f)
                horizontalLineTo(1.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(10.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(10.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(7.0f)
                close()
            }
        }
        return _crop!!
    }

private var _crop: ImageVector? = null
