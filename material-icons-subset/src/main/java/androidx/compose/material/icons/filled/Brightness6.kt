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

public val Icons.Filled.Brightness6: ImageVector
    get() {
        if (_brightness6 != null) {
            return _brightness6!!
        }
        _brightness6 = materialIcon(name = "Filled.Brightness6") {
            materialPath {
                moveTo(20.0f, 15.31f)
                lineTo(23.31f, 12.0f)
                lineTo(20.0f, 8.69f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(-4.69f)
                lineTo(12.0f, 0.69f)
                lineTo(8.69f, 4.0f)
                horizontalLineTo(4.0f)
                verticalLineToRelative(4.69f)
                lineTo(0.69f, 12.0f)
                lineTo(4.0f, 15.31f)
                verticalLineTo(20.0f)
                horizontalLineToRelative(4.69f)
                lineTo(12.0f, 23.31f)
                lineTo(15.31f, 20.0f)
                horizontalLineTo(20.0f)
                verticalLineToRelative(-4.69f)
                close()
                moveTo(12.0f, 18.0f)
                verticalLineTo(6.0f)
                curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
                reflectiveCurveToRelative(-2.69f, 6.0f, -6.0f, 6.0f)
                close()
            }
        }
        return _brightness6!!
    }

private var _brightness6: ImageVector? = null
