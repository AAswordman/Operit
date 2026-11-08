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

package androidx.compose.material.icons.rounded

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Rounded.TipsAndUpdates: ImageVector
    get() {
        if (_tipsAndUpdates != null) {
            return _tipsAndUpdates!!
        }
        _tipsAndUpdates = materialIcon(name = "Rounded.TipsAndUpdates") {
            materialPath {
                moveTo(7.0f, 20.0f)
                horizontalLineToRelative(4.0f)
                curveToRelative(0.0f, 1.1f, -0.9f, 2.0f, -2.0f, 2.0f)
                reflectiveCurveTo(7.0f, 21.1f, 7.0f, 20.0f)
                close()
                moveTo(5.0f, 18.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(6.0f)
                curveTo(5.45f, 17.0f, 5.0f, 17.45f, 5.0f, 18.0f)
                close()
                moveTo(16.5f, 9.5f)
                curveToRelative(0.0f, 3.82f, -2.66f, 5.86f, -3.77f, 6.5f)
                horizontalLineTo(5.27f)
                curveTo(4.16f, 15.36f, 1.5f, 13.32f, 1.5f, 9.5f)
                curveTo(1.5f, 5.36f, 4.86f, 2.0f, 9.0f, 2.0f)
                reflectiveCurveTo(16.5f, 5.36f, 16.5f, 9.5f)
                close()
                moveTo(21.37f, 7.37f)
                lineTo(20.0f, 8.0f)
                lineToRelative(1.37f, 0.63f)
                lineTo(22.0f, 10.0f)
                lineToRelative(0.63f, -1.37f)
                lineTo(24.0f, 8.0f)
                lineToRelative(-1.37f, -0.63f)
                lineTo(22.0f, 6.0f)
                lineTo(21.37f, 7.37f)
                close()
                moveTo(19.0f, 6.0f)
                lineToRelative(0.94f, -2.06f)
                lineTo(22.0f, 3.0f)
                lineToRelative(-2.06f, -0.94f)
                lineTo(19.0f, 0.0f)
                lineToRelative(-0.94f, 2.06f)
                lineTo(16.0f, 3.0f)
                lineToRelative(2.06f, 0.94f)
                lineTo(19.0f, 6.0f)
                close()
            }
        }
        return _tipsAndUpdates!!
    }

private var _tipsAndUpdates: ImageVector? = null
