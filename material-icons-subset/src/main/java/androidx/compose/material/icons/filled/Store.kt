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

public val Icons.Filled.Store: ImageVector
    get() {
        if (_store != null) {
            return _store!!
        }
        _store = materialIcon(name = "Filled.Store") {
            materialPath {
                moveTo(20.0f, 4.0f)
                lineTo(4.0f, 4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(16.0f)
                lineTo(20.0f, 4.0f)
                close()
                moveTo(21.0f, 14.0f)
                verticalLineToRelative(-2.0f)
                lineToRelative(-1.0f, -5.0f)
                lineTo(4.0f, 7.0f)
                lineToRelative(-1.0f, 5.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(1.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(10.0f)
                verticalLineToRelative(-6.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-6.0f)
                horizontalLineToRelative(1.0f)
                close()
                moveTo(12.0f, 18.0f)
                lineTo(6.0f, 18.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(6.0f)
                verticalLineToRelative(4.0f)
                close()
            }
        }
        return _store!!
    }

private var _store: ImageVector? = null
