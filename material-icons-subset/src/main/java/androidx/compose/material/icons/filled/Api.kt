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

public val Icons.Filled.Api: ImageVector
    get() {
        if (_api != null) {
            return _api!!
        }
        _api = materialIcon(name = "Filled.Api") {
            materialPath {
                moveTo(14.0f, 12.0f)
                lineToRelative(-2.0f, 2.0f)
                lineToRelative(-2.0f, -2.0f)
                lineToRelative(2.0f, -2.0f)
                lineTo(14.0f, 12.0f)
                close()
                moveTo(12.0f, 6.0f)
                lineToRelative(2.12f, 2.12f)
                lineToRelative(2.5f, -2.5f)
                lineTo(12.0f, 1.0f)
                lineTo(7.38f, 5.62f)
                lineToRelative(2.5f, 2.5f)
                lineTo(12.0f, 6.0f)
                close()
                moveTo(6.0f, 12.0f)
                lineToRelative(2.12f, -2.12f)
                lineToRelative(-2.5f, -2.5f)
                lineTo(1.0f, 12.0f)
                lineToRelative(4.62f, 4.62f)
                lineToRelative(2.5f, -2.5f)
                lineTo(6.0f, 12.0f)
                close()
                moveTo(18.0f, 12.0f)
                lineToRelative(-2.12f, 2.12f)
                lineToRelative(2.5f, 2.5f)
                lineTo(23.0f, 12.0f)
                lineToRelative(-4.62f, -4.62f)
                lineToRelative(-2.5f, 2.5f)
                lineTo(18.0f, 12.0f)
                close()
                moveTo(12.0f, 18.0f)
                lineToRelative(-2.12f, -2.12f)
                lineToRelative(-2.5f, 2.5f)
                lineTo(12.0f, 23.0f)
                lineToRelative(4.62f, -4.62f)
                lineToRelative(-2.5f, -2.5f)
                lineTo(12.0f, 18.0f)
                close()
            }
        }
        return _api!!
    }

private var _api: ImageVector? = null
