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

public val Icons.Outlined.WarningAmber: ImageVector
    get() {
        if (_warningAmber != null) {
            return _warningAmber!!
        }
        _warningAmber = materialIcon(name = "Outlined.WarningAmber") {
            materialPath {
                moveTo(12.0f, 5.99f)
                lineTo(19.53f, 19.0f)
                lineTo(4.47f, 19.0f)
                lineTo(12.0f, 5.99f)
                moveTo(12.0f, 2.0f)
                lineTo(1.0f, 21.0f)
                horizontalLineToRelative(22.0f)
                lineTo(12.0f, 2.0f)
                close()
                moveTo(13.0f, 16.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                close()
                moveTo(13.0f, 10.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-4.0f)
                close()
            }
        }
        return _warningAmber!!
    }

private var _warningAmber: ImageVector? = null
