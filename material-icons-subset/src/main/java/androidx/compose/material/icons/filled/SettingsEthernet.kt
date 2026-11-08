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

public val Icons.Filled.SettingsEthernet: ImageVector
    get() {
        if (_settingsEthernet != null) {
            return _settingsEthernet!!
        }
        _settingsEthernet = materialIcon(name = "Filled.SettingsEthernet") {
            materialPath {
                moveTo(7.77f, 6.76f)
                lineTo(6.23f, 5.48f)
                lineTo(0.82f, 12.0f)
                lineToRelative(5.41f, 6.52f)
                lineToRelative(1.54f, -1.28f)
                lineTo(3.42f, 12.0f)
                lineToRelative(4.35f, -5.24f)
                close()
                moveTo(7.0f, 13.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                lineTo(7.0f, 11.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(17.0f, 11.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                close()
                moveTo(11.0f, 13.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(17.77f, 5.48f)
                lineToRelative(-1.54f, 1.28f)
                lineTo(20.58f, 12.0f)
                lineToRelative(-4.35f, 5.24f)
                lineToRelative(1.54f, 1.28f)
                lineTo(23.18f, 12.0f)
                lineToRelative(-5.41f, -6.52f)
                close()
            }
        }
        return _settingsEthernet!!
    }

private var _settingsEthernet: ImageVector? = null
