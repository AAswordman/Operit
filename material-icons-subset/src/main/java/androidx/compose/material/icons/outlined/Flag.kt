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

public val Icons.Outlined.Flag: ImageVector
    get() {
        if (_flag != null) {
            return _flag!!
        }
        _flag = materialIcon(name = "Outlined.Flag") {
            materialPath {
                moveTo(12.36f, 6.0f)
                lineToRelative(0.4f, 2.0f)
                horizontalLineTo(18.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(-3.36f)
                lineToRelative(-0.4f, -2.0f)
                horizontalLineTo(7.0f)
                verticalLineTo(6.0f)
                horizontalLineToRelative(5.36f)
                moveTo(14.0f, 4.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(17.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-7.0f)
                horizontalLineToRelative(5.6f)
                lineToRelative(0.4f, 2.0f)
                horizontalLineToRelative(7.0f)
                verticalLineTo(6.0f)
                horizontalLineToRelative(-5.6f)
                lineTo(14.0f, 4.0f)
                close()
            }
        }
        return _flag!!
    }

private var _flag: ImageVector? = null
