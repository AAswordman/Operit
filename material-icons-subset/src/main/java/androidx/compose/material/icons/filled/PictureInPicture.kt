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

public val Icons.Filled.PictureInPicture: ImageVector
    get() {
        if (_pictureInPicture != null) {
            return _pictureInPicture!!
        }
        _pictureInPicture = materialIcon(name = "Filled.PictureInPicture") {
            materialPath {
                moveTo(19.0f, 7.0f)
                horizontalLineToRelative(-8.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(8.0f)
                lineTo(19.0f, 7.0f)
                close()
                moveTo(21.0f, 3.0f)
                lineTo(3.0f, 3.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 1.98f, 2.0f, 1.98f)
                horizontalLineToRelative(18.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.88f, 2.0f, -1.98f)
                lineTo(23.0f, 5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(21.0f, 19.01f)
                lineTo(3.0f, 19.01f)
                lineTo(3.0f, 4.98f)
                horizontalLineToRelative(18.0f)
                verticalLineToRelative(14.03f)
                close()
            }
        }
        return _pictureInPicture!!
    }

private var _pictureInPicture: ImageVector? = null
