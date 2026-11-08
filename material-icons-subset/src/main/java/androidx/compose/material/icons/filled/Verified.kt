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

public val Icons.Filled.Verified: ImageVector
    get() {
        if (_verified != null) {
            return _verified!!
        }
        _verified = materialIcon(name = "Filled.Verified") {
            materialPath {
                moveTo(23.0f, 12.0f)
                lineToRelative(-2.44f, -2.79f)
                lineToRelative(0.34f, -3.69f)
                lineToRelative(-3.61f, -0.82f)
                lineTo(15.4f, 1.5f)
                lineTo(12.0f, 2.96f)
                lineTo(8.6f, 1.5f)
                lineTo(6.71f, 4.69f)
                lineTo(3.1f, 5.5f)
                lineTo(3.44f, 9.2f)
                lineTo(1.0f, 12.0f)
                lineToRelative(2.44f, 2.79f)
                lineToRelative(-0.34f, 3.7f)
                lineToRelative(3.61f, 0.82f)
                lineTo(8.6f, 22.5f)
                lineToRelative(3.4f, -1.47f)
                lineToRelative(3.4f, 1.46f)
                lineToRelative(1.89f, -3.19f)
                lineToRelative(3.61f, -0.82f)
                lineToRelative(-0.34f, -3.69f)
                lineTo(23.0f, 12.0f)
                close()
                moveTo(10.09f, 16.72f)
                lineToRelative(-3.8f, -3.81f)
                lineToRelative(1.48f, -1.48f)
                lineToRelative(2.32f, 2.33f)
                lineToRelative(5.85f, -5.87f)
                lineToRelative(1.48f, 1.48f)
                lineTo(10.09f, 16.72f)
                close()
            }
        }
        return _verified!!
    }

private var _verified: ImageVector? = null
