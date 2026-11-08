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

public val Icons.Outlined.Print: ImageVector
    get() {
        if (_print != null) {
            return _print!!
        }
        _print = materialIcon(name = "Outlined.Print") {
            materialPath {
                moveTo(19.0f, 8.0f)
                horizontalLineToRelative(-1.0f)
                lineTo(18.0f, 3.0f)
                lineTo(6.0f, 3.0f)
                verticalLineToRelative(5.0f)
                lineTo(5.0f, 8.0f)
                curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-6.0f)
                curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                close()
                moveTo(8.0f, 5.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(3.0f)
                lineTo(8.0f, 8.0f)
                lineTo(8.0f, 5.0f)
                close()
                moveTo(16.0f, 17.0f)
                verticalLineToRelative(2.0f)
                lineTo(8.0f, 19.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(18.0f, 15.0f)
                verticalLineToRelative(-2.0f)
                lineTo(6.0f, 13.0f)
                verticalLineToRelative(2.0f)
                lineTo(4.0f, 15.0f)
                verticalLineToRelative(-4.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(-2.0f)
                close()
            }
            materialPath {
                moveTo(18.0f, 11.5f)
                moveToRelative(-1.0f, 0.0f)
                arcToRelative(1.0f, 1.0f, 0.0f, true, true, 2.0f, 0.0f)
                arcToRelative(1.0f, 1.0f, 0.0f, true, true, -2.0f, 0.0f)
            }
        }
        return _print!!
    }

private var _print: ImageVector? = null
