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

public val Icons.Filled.Inventory: ImageVector
    get() {
        if (_inventory != null) {
            return _inventory!!
        }
        _inventory = materialIcon(name = "Filled.Inventory") {
            materialPath {
                moveTo(20.0f, 2.0f)
                lineTo(4.0f, 2.0f)
                curveToRelative(-1.0f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(3.01f)
                curveToRelative(0.0f, 0.72f, 0.43f, 1.34f, 1.0f, 1.69f)
                lineTo(3.0f, 20.0f)
                curveToRelative(0.0f, 1.1f, 1.1f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(0.9f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(21.0f, 8.7f)
                curveToRelative(0.57f, -0.35f, 1.0f, -0.97f, 1.0f, -1.69f)
                lineTo(22.0f, 4.0f)
                curveToRelative(0.0f, -1.1f, -1.0f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(15.0f, 14.0f)
                lineTo(9.0f, 14.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(6.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(20.0f, 7.0f)
                lineTo(4.0f, 7.0f)
                lineTo(4.0f, 4.0f)
                lineToRelative(16.0f, -0.02f)
                lineTo(20.0f, 7.0f)
                close()
            }
        }
        return _inventory!!
    }

private var _inventory: ImageVector? = null
