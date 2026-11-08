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

public val Icons.Filled.DeleteForever: ImageVector
    get() {
        if (_deleteForever != null) {
            return _deleteForever!!
        }
        _deleteForever = materialIcon(name = "Filled.DeleteForever") {
            materialPath {
                moveTo(6.0f, 19.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(8.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(18.0f, 7.0f)
                lineTo(6.0f, 7.0f)
                verticalLineToRelative(12.0f)
                close()
                moveTo(8.46f, 11.88f)
                lineToRelative(1.41f, -1.41f)
                lineTo(12.0f, 12.59f)
                lineToRelative(2.12f, -2.12f)
                lineToRelative(1.41f, 1.41f)
                lineTo(13.41f, 14.0f)
                lineToRelative(2.12f, 2.12f)
                lineToRelative(-1.41f, 1.41f)
                lineTo(12.0f, 15.41f)
                lineToRelative(-2.12f, 2.12f)
                lineToRelative(-1.41f, -1.41f)
                lineTo(10.59f, 14.0f)
                lineToRelative(-2.13f, -2.12f)
                close()
                moveTo(15.5f, 4.0f)
                lineToRelative(-1.0f, -1.0f)
                horizontalLineToRelative(-5.0f)
                lineToRelative(-1.0f, 1.0f)
                lineTo(5.0f, 4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(14.0f)
                lineTo(19.0f, 4.0f)
                close()
            }
        }
        return _deleteForever!!
    }

private var _deleteForever: ImageVector? = null
