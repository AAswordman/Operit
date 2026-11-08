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

public val Icons.Filled.NewReleases: ImageVector
    get() {
        if (_newReleases != null) {
            return _newReleases!!
        }
        _newReleases = materialIcon(name = "Filled.NewReleases") {
            materialPath {
                moveTo(23.0f, 12.0f)
                lineToRelative(-2.44f, -2.78f)
                lineToRelative(0.34f, -3.68f)
                lineToRelative(-3.61f, -0.82f)
                lineToRelative(-1.89f, -3.18f)
                lineTo(12.0f, 3.0f)
                lineTo(8.6f, 1.54f)
                lineTo(6.71f, 4.72f)
                lineToRelative(-3.61f, 0.81f)
                lineToRelative(0.34f, 3.68f)
                lineTo(1.0f, 12.0f)
                lineToRelative(2.44f, 2.78f)
                lineToRelative(-0.34f, 3.69f)
                lineToRelative(3.61f, 0.82f)
                lineToRelative(1.89f, 3.18f)
                lineTo(12.0f, 21.0f)
                lineToRelative(3.4f, 1.46f)
                lineToRelative(1.89f, -3.18f)
                lineToRelative(3.61f, -0.82f)
                lineToRelative(-0.34f, -3.68f)
                lineTo(23.0f, 12.0f)
                close()
                moveTo(13.0f, 17.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(13.0f, 13.0f)
                horizontalLineToRelative(-2.0f)
                lineTo(11.0f, 7.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(6.0f)
                close()
            }
        }
        return _newReleases!!
    }

private var _newReleases: ImageVector? = null
