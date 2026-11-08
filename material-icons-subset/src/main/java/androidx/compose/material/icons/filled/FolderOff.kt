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

public val Icons.Filled.FolderOff: ImageVector
    get() {
        if (_folderOff != null) {
            return _folderOff!!
        }
        _folderOff = materialIcon(name = "Filled.FolderOff") {
            materialPath {
                moveTo(20.0f, 6.0f)
                horizontalLineToRelative(-8.0f)
                lineToRelative(-2.0f, -2.0f)
                horizontalLineTo(6.83f)
                lineToRelative(14.93f, 14.93f)
                curveTo(21.91f, 18.65f, 22.0f, 18.34f, 22.0f, 18.0f)
                verticalLineTo(8.0f)
                curveTo(22.0f, 6.9f, 21.1f, 6.0f, 20.0f, 6.0f)
                close()
            }
            materialPath {
                moveTo(2.1f, 2.1f)
                lineTo(0.69f, 3.51f)
                lineToRelative(1.56f, 1.56f)
                curveTo(2.1f, 5.35f, 2.01f, 5.66f, 2.01f, 6.0f)
                lineTo(2.0f, 18.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(13.17f)
                lineToRelative(3.31f, 3.31f)
                lineToRelative(1.41f, -1.41f)
                lineTo(2.1f, 2.1f)
                close()
            }
        }
        return _folderOff!!
    }

private var _folderOff: ImageVector? = null
