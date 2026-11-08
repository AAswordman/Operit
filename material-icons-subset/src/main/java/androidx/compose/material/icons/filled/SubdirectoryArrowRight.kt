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

public val Icons.Filled.SubdirectoryArrowRight: ImageVector
    get() {
        if (_subdirectoryArrowRight != null) {
            return _subdirectoryArrowRight!!
        }
        _subdirectoryArrowRight = materialIcon(name = "Filled.SubdirectoryArrowRight") {
            materialPath {
                moveTo(19.0f, 15.0f)
                lineToRelative(-6.0f, 6.0f)
                lineToRelative(-1.42f, -1.42f)
                lineTo(15.17f, 16.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(10.0f)
                horizontalLineToRelative(9.17f)
                lineToRelative(-3.59f, -3.58f)
                lineTo(13.0f, 9.0f)
                lineToRelative(6.0f, 6.0f)
                close()
            }
        }
        return _subdirectoryArrowRight!!
    }

private var _subdirectoryArrowRight: ImageVector? = null
