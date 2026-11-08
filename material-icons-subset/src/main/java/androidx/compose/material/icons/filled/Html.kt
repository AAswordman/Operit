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

public val Icons.Filled.Html: ImageVector
    get() {
        if (_html != null) {
            return _html!!
        }
        _html = materialIcon(name = "Filled.Html") {
            materialPath {
                moveTo(3.5f, 9.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(6.0f)
                horizontalLineTo(3.5f)
                verticalLineToRelative(-2.5f)
                horizontalLineToRelative(-2.0f)
                verticalLineTo(15.0f)
                horizontalLineTo(0.0f)
                verticalLineTo(9.0f)
                horizontalLineToRelative(1.5f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(9.0f)
                close()
                moveTo(17.5f, 9.0f)
                horizontalLineTo(13.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(5.0f)
                horizontalLineToRelative(1.5f)
                verticalLineToRelative(-4.5f)
                horizontalLineToRelative(1.0f)
                verticalLineTo(14.0f)
                horizontalLineTo(16.0f)
                verticalLineToRelative(-3.51f)
                horizontalLineToRelative(1.0f)
                verticalLineTo(15.0f)
                horizontalLineToRelative(1.5f)
                verticalLineToRelative(-5.0f)
                curveTo(18.5f, 9.45f, 18.05f, 9.0f, 17.5f, 9.0f)
                close()
                moveTo(11.0f, 9.0f)
                horizontalLineTo(6.0f)
                verticalLineToRelative(1.5f)
                horizontalLineToRelative(1.75f)
                verticalLineTo(15.0f)
                horizontalLineToRelative(1.5f)
                verticalLineToRelative(-4.5f)
                horizontalLineTo(11.0f)
                verticalLineTo(9.0f)
                close()
                moveTo(24.0f, 15.0f)
                verticalLineToRelative(-1.5f)
                horizontalLineToRelative(-2.5f)
                verticalLineTo(9.0f)
                horizontalLineTo(20.0f)
                verticalLineToRelative(6.0f)
                horizontalLineTo(24.0f)
                close()
            }
        }
        return _html!!
    }

private var _html: ImageVector? = null
