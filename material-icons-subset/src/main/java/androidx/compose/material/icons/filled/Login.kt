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
import kotlin.Deprecated

@Deprecated(
    "Use the AutoMirrored version at Icons.AutoMirrored.Filled.Login",
    ReplaceWith( "Icons.AutoMirrored.Filled.Login",
            "androidx.compose.material.icons.automirrored.filled.Login"),
)
public val Icons.Filled.Login: ImageVector
    get() {
        if (_login != null) {
            return _login!!
        }
        _login = materialIcon(name = "Filled.Login") {
            materialPath {
                moveTo(11.0f, 7.0f)
                lineTo(9.6f, 8.4f)
                lineToRelative(2.6f, 2.6f)
                horizontalLineTo(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(10.2f)
                lineToRelative(-2.6f, 2.6f)
                lineTo(11.0f, 17.0f)
                lineToRelative(5.0f, -5.0f)
                lineTo(11.0f, 7.0f)
                close()
                moveTo(20.0f, 19.0f)
                horizontalLineToRelative(-8.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(8.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(-8.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(8.0f)
                verticalLineTo(19.0f)
                close()
            }
        }
        return _login!!
    }

private var _login: ImageVector? = null
