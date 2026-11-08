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

public val Icons.Filled.SortByAlpha: ImageVector
    get() {
        if (_sortByAlpha != null) {
            return _sortByAlpha!!
        }
        _sortByAlpha = materialIcon(name = "Filled.SortByAlpha") {
            materialPath {
                moveTo(14.94f, 4.66f)
                horizontalLineToRelative(-4.72f)
                lineToRelative(2.36f, -2.36f)
                close()
                moveTo(10.25f, 19.37f)
                horizontalLineToRelative(4.66f)
                lineToRelative(-2.33f, 2.33f)
                close()
                moveTo(6.1f, 6.27f)
                lineTo(1.6f, 17.73f)
                horizontalLineToRelative(1.84f)
                lineToRelative(0.92f, -2.45f)
                horizontalLineToRelative(5.11f)
                lineToRelative(0.92f, 2.45f)
                horizontalLineToRelative(1.84f)
                lineTo(7.74f, 6.27f)
                lineTo(6.1f, 6.27f)
                close()
                moveTo(4.97f, 13.64f)
                lineToRelative(1.94f, -5.18f)
                lineToRelative(1.94f, 5.18f)
                lineTo(4.97f, 13.64f)
                close()
                moveTo(15.73f, 16.14f)
                horizontalLineToRelative(6.12f)
                verticalLineToRelative(1.59f)
                horizontalLineToRelative(-8.53f)
                verticalLineToRelative(-1.29f)
                lineToRelative(5.92f, -8.56f)
                horizontalLineToRelative(-5.88f)
                verticalLineToRelative(-1.6f)
                horizontalLineToRelative(8.3f)
                verticalLineToRelative(1.26f)
                lineToRelative(-5.93f, 8.6f)
                close()
            }
        }
        return _sortByAlpha!!
    }

private var _sortByAlpha: ImageVector? = null
