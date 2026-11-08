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

public val Icons.Filled.SdCard: ImageVector
    get() {
        if (_sdCard != null) {
            return _sdCard!!
        }
        _sdCard = materialIcon(name = "Filled.SdCard") {
            materialPath {
                moveTo(18.0f, 2.0f)
                horizontalLineToRelative(-8.0f)
                lineTo(4.02f, 8.0f)
                lineTo(4.0f, 20.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(20.0f, 4.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(12.0f, 8.0f)
                horizontalLineToRelative(-2.0f)
                lineTo(10.0f, 4.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(15.0f, 8.0f)
                horizontalLineToRelative(-2.0f)
                lineTo(13.0f, 4.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(18.0f, 8.0f)
                horizontalLineToRelative(-2.0f)
                lineTo(16.0f, 4.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(4.0f)
                close()
            }
        }
        return _sdCard!!
    }

private var _sdCard: ImageVector? = null
