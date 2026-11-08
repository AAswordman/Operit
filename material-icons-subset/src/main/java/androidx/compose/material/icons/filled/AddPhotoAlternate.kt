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

public val Icons.Filled.AddPhotoAlternate: ImageVector
    get() {
        if (_addPhotoAlternate != null) {
            return _addPhotoAlternate!!
        }
        _addPhotoAlternate = materialIcon(name = "Filled.AddPhotoAlternate") {
            materialPath {
                moveTo(19.0f, 7.0f)
                verticalLineToRelative(2.99f)
                reflectiveCurveToRelative(-1.99f, 0.01f, -2.0f, 0.0f)
                lineTo(17.0f, 7.0f)
                horizontalLineToRelative(-3.0f)
                reflectiveCurveToRelative(0.01f, -1.99f, 0.0f, -2.0f)
                horizontalLineToRelative(3.0f)
                lineTo(17.0f, 2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-3.0f)
                close()
                moveTo(16.0f, 11.0f)
                lineTo(16.0f, 8.0f)
                horizontalLineToRelative(-3.0f)
                lineTo(13.0f, 5.0f)
                lineTo(5.0f, 5.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-8.0f)
                horizontalLineToRelative(-3.0f)
                close()
                moveTo(5.0f, 19.0f)
                lineToRelative(3.0f, -4.0f)
                lineToRelative(2.0f, 3.0f)
                lineToRelative(3.0f, -4.0f)
                lineToRelative(4.0f, 5.0f)
                lineTo(5.0f, 19.0f)
                close()
            }
        }
        return _addPhotoAlternate!!
    }

private var _addPhotoAlternate: ImageVector? = null
