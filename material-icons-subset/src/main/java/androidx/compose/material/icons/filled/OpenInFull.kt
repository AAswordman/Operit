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

public val Icons.Filled.OpenInFull: ImageVector
    get() {
        if (_openInFull != null) {
            return _openInFull!!
        }
        _openInFull = materialIcon(name = "Filled.OpenInFull") {
            materialPath {
                moveTo(21.0f, 11.0f)
                lineToRelative(0.0f, -8.0f)
                lineToRelative(-8.0f, 0.0f)
                lineToRelative(3.29f, 3.29f)
                lineToRelative(-10.0f, 10.0f)
                lineToRelative(-3.29f, -3.29f)
                lineToRelative(0.0f, 8.0f)
                lineToRelative(8.0f, 0.0f)
                lineToRelative(-3.29f, -3.29f)
                lineToRelative(10.0f, -10.0f)
                close()
            }
        }
        return _openInFull!!
    }

private var _openInFull: ImageVector? = null
