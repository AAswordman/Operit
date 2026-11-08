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

public val Icons.Filled.Block: ImageVector
    get() {
        if (_block != null) {
            return _block!!
        }
        _block = materialIcon(name = "Filled.Block") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(4.0f, 12.0f)
                curveToRelative(0.0f, -4.42f, 3.58f, -8.0f, 8.0f, -8.0f)
                curveToRelative(1.85f, 0.0f, 3.55f, 0.63f, 4.9f, 1.69f)
                lineTo(5.69f, 16.9f)
                curveTo(4.63f, 15.55f, 4.0f, 13.85f, 4.0f, 12.0f)
                close()
                moveTo(12.0f, 20.0f)
                curveToRelative(-1.85f, 0.0f, -3.55f, -0.63f, -4.9f, -1.69f)
                lineTo(18.31f, 7.1f)
                curveTo(19.37f, 8.45f, 20.0f, 10.15f, 20.0f, 12.0f)
                curveToRelative(0.0f, 4.42f, -3.58f, 8.0f, -8.0f, 8.0f)
                close()
            }
        }
        return _block!!
    }

private var _block: ImageVector? = null
