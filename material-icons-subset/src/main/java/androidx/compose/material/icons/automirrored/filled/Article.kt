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

package androidx.compose.material.icons.automirrored.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.AutoMirrored.Filled.Article: ImageVector
    get() {
        if (_article != null) {
            return _article!!
        }
        _article = materialIcon(name = "AutoMirrored.Filled.Article", autoMirror = true) {
            materialPath {
                moveTo(19.0f, 3.0f)
                lineTo(5.0f, 3.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(21.0f, 5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(14.0f, 17.0f)
                lineTo(7.0f, 17.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(7.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(17.0f, 13.0f)
                lineTo(7.0f, 13.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(10.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(17.0f, 9.0f)
                lineTo(7.0f, 9.0f)
                lineTo(7.0f, 7.0f)
                horizontalLineToRelative(10.0f)
                verticalLineToRelative(2.0f)
                close()
            }
        }
        return _article!!
    }

private var _article: ImageVector? = null
