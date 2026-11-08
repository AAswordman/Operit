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

public val Icons.Filled.AutoFixHigh: ImageVector
    get() {
        if (_autoFixHigh != null) {
            return _autoFixHigh!!
        }
        _autoFixHigh = materialIcon(name = "Filled.AutoFixHigh") {
            materialPath {
                moveTo(7.5f, 5.6f)
                lineTo(10.0f, 7.0f)
                lineTo(8.6f, 4.5f)
                lineTo(10.0f, 2.0f)
                lineTo(7.5f, 3.4f)
                lineTo(5.0f, 2.0f)
                lineToRelative(1.4f, 2.5f)
                lineTo(5.0f, 7.0f)
                close()
                moveTo(19.5f, 15.4f)
                lineTo(17.0f, 14.0f)
                lineToRelative(1.4f, 2.5f)
                lineTo(17.0f, 19.0f)
                lineToRelative(2.5f, -1.4f)
                lineTo(22.0f, 19.0f)
                lineToRelative(-1.4f, -2.5f)
                lineTo(22.0f, 14.0f)
                close()
                moveTo(22.0f, 2.0f)
                lineToRelative(-2.5f, 1.4f)
                lineTo(17.0f, 2.0f)
                lineToRelative(1.4f, 2.5f)
                lineTo(17.0f, 7.0f)
                lineToRelative(2.5f, -1.4f)
                lineTo(22.0f, 7.0f)
                lineToRelative(-1.4f, -2.5f)
                close()
                moveTo(14.37f, 7.29f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                lineTo(1.29f, 18.96f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(2.34f, 2.34f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineTo(16.7f, 11.05f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                lineToRelative(-2.33f, -2.35f)
                close()
                moveTo(13.34f, 12.78f)
                lineToRelative(-2.12f, -2.12f)
                lineToRelative(2.44f, -2.44f)
                lineToRelative(2.12f, 2.12f)
                lineToRelative(-2.44f, 2.44f)
                close()
            }
        }
        return _autoFixHigh!!
    }

private var _autoFixHigh: ImageVector? = null
