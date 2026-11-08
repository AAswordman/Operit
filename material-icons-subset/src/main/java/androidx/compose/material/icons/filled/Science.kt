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

public val Icons.Filled.Science: ImageVector
    get() {
        if (_science != null) {
            return _science!!
        }
        _science = materialIcon(name = "Filled.Science") {
            materialPath {
                moveTo(19.8f, 18.4f)
                lineTo(14.0f, 10.67f)
                verticalLineTo(6.5f)
                lineToRelative(1.35f, -1.69f)
                curveTo(15.61f, 4.48f, 15.38f, 4.0f, 14.96f, 4.0f)
                horizontalLineTo(9.04f)
                curveTo(8.62f, 4.0f, 8.39f, 4.48f, 8.65f, 4.81f)
                lineTo(10.0f, 6.5f)
                verticalLineToRelative(4.17f)
                lineTo(4.2f, 18.4f)
                curveTo(3.71f, 19.06f, 4.18f, 20.0f, 5.0f, 20.0f)
                horizontalLineToRelative(14.0f)
                curveTo(19.82f, 20.0f, 20.29f, 19.06f, 19.8f, 18.4f)
                close()
            }
        }
        return _science!!
    }

private var _science: ImageVector? = null
