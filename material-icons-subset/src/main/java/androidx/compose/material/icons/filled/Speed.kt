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

public val Icons.Filled.Speed: ImageVector
    get() {
        if (_speed != null) {
            return _speed!!
        }
        _speed = materialIcon(name = "Filled.Speed") {
            materialPath {
                moveTo(20.38f, 8.57f)
                lineToRelative(-1.23f, 1.85f)
                arcToRelative(8.0f, 8.0f, 0.0f, false, true, -0.22f, 7.58f)
                lineTo(5.07f, 18.0f)
                arcTo(8.0f, 8.0f, 0.0f, false, true, 15.58f, 6.85f)
                lineToRelative(1.85f, -1.23f)
                arcTo(10.0f, 10.0f, 0.0f, false, false, 3.35f, 19.0f)
                arcToRelative(2.0f, 2.0f, 0.0f, false, false, 1.72f, 1.0f)
                horizontalLineToRelative(13.85f)
                arcToRelative(2.0f, 2.0f, 0.0f, false, false, 1.74f, -1.0f)
                arcToRelative(10.0f, 10.0f, 0.0f, false, false, -0.27f, -10.44f)
                close()
                moveTo(10.59f, 15.41f)
                arcToRelative(2.0f, 2.0f, 0.0f, false, false, 2.83f, 0.0f)
                lineToRelative(5.66f, -8.49f)
                lineToRelative(-8.49f, 5.66f)
                arcToRelative(2.0f, 2.0f, 0.0f, false, false, 0.0f, 2.83f)
                close()
            }
        }
        return _speed!!
    }

private var _speed: ImageVector? = null
