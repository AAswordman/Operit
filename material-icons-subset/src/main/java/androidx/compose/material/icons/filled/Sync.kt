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

public val Icons.Filled.Sync: ImageVector
    get() {
        if (_sync != null) {
            return _sync!!
        }
        _sync = materialIcon(name = "Filled.Sync") {
            materialPath {
                moveTo(12.0f, 4.0f)
                lineTo(12.0f, 1.0f)
                lineTo(8.0f, 5.0f)
                lineToRelative(4.0f, 4.0f)
                lineTo(12.0f, 6.0f)
                curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
                curveToRelative(0.0f, 1.01f, -0.25f, 1.97f, -0.7f, 2.8f)
                lineToRelative(1.46f, 1.46f)
                curveTo(19.54f, 15.03f, 20.0f, 13.57f, 20.0f, 12.0f)
                curveToRelative(0.0f, -4.42f, -3.58f, -8.0f, -8.0f, -8.0f)
                close()
                moveTo(12.0f, 18.0f)
                curveToRelative(-3.31f, 0.0f, -6.0f, -2.69f, -6.0f, -6.0f)
                curveToRelative(0.0f, -1.01f, 0.25f, -1.97f, 0.7f, -2.8f)
                lineTo(5.24f, 7.74f)
                curveTo(4.46f, 8.97f, 4.0f, 10.43f, 4.0f, 12.0f)
                curveToRelative(0.0f, 4.42f, 3.58f, 8.0f, 8.0f, 8.0f)
                verticalLineToRelative(3.0f)
                lineToRelative(4.0f, -4.0f)
                lineToRelative(-4.0f, -4.0f)
                verticalLineToRelative(3.0f)
                close()
            }
        }
        return _sync!!
    }

private var _sync: ImageVector? = null
