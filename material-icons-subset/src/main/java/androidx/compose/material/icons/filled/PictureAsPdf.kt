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

public val Icons.Filled.PictureAsPdf: ImageVector
    get() {
        if (_pictureAsPdf != null) {
            return _pictureAsPdf!!
        }
        _pictureAsPdf = materialIcon(name = "Filled.PictureAsPdf") {
            materialPath {
                moveTo(20.0f, 2.0f)
                lineTo(8.0f, 2.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(22.0f, 4.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(11.5f, 9.5f)
                curveToRelative(0.0f, 0.83f, -0.67f, 1.5f, -1.5f, 1.5f)
                lineTo(9.0f, 11.0f)
                verticalLineToRelative(2.0f)
                lineTo(7.5f, 13.0f)
                lineTo(7.5f, 7.0f)
                lineTo(10.0f, 7.0f)
                curveToRelative(0.83f, 0.0f, 1.5f, 0.67f, 1.5f, 1.5f)
                verticalLineToRelative(1.0f)
                close()
                moveTo(16.5f, 11.5f)
                curveToRelative(0.0f, 0.83f, -0.67f, 1.5f, -1.5f, 1.5f)
                horizontalLineToRelative(-2.5f)
                lineTo(12.5f, 7.0f)
                lineTo(15.0f, 7.0f)
                curveToRelative(0.83f, 0.0f, 1.5f, 0.67f, 1.5f, 1.5f)
                verticalLineToRelative(3.0f)
                close()
                moveTo(20.5f, 8.5f)
                lineTo(19.0f, 8.5f)
                verticalLineToRelative(1.0f)
                horizontalLineToRelative(1.5f)
                lineTo(20.5f, 11.0f)
                lineTo(19.0f, 11.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-1.5f)
                lineTo(17.5f, 7.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(1.5f)
                close()
                moveTo(9.0f, 9.5f)
                horizontalLineToRelative(1.0f)
                verticalLineToRelative(-1.0f)
                lineTo(9.0f, 8.5f)
                verticalLineToRelative(1.0f)
                close()
                moveTo(4.0f, 6.0f)
                lineTo(2.0f, 6.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(-2.0f)
                lineTo(4.0f, 20.0f)
                lineTo(4.0f, 6.0f)
                close()
                moveTo(14.0f, 11.5f)
                horizontalLineToRelative(1.0f)
                verticalLineToRelative(-3.0f)
                horizontalLineToRelative(-1.0f)
                verticalLineToRelative(3.0f)
                close()
            }
        }
        return _pictureAsPdf!!
    }

private var _pictureAsPdf: ImageVector? = null
