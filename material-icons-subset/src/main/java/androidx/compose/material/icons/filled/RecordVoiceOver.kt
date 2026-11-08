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

public val Icons.Filled.RecordVoiceOver: ImageVector
    get() {
        if (_recordVoiceOver != null) {
            return _recordVoiceOver!!
        }
        _recordVoiceOver = materialIcon(name = "Filled.RecordVoiceOver") {
            materialPath {
                moveTo(9.0f, 9.0f)
                moveToRelative(-4.0f, 0.0f)
                arcToRelative(4.0f, 4.0f, 0.0f, true, true, 8.0f, 0.0f)
                arcToRelative(4.0f, 4.0f, 0.0f, true, true, -8.0f, 0.0f)
            }
            materialPath {
                moveTo(9.0f, 15.0f)
                curveToRelative(-2.67f, 0.0f, -8.0f, 1.34f, -8.0f, 4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(-2.0f)
                curveToRelative(0.0f, -2.66f, -5.33f, -4.0f, -8.0f, -4.0f)
                close()
                moveTo(16.76f, 5.36f)
                lineToRelative(-1.68f, 1.69f)
                curveToRelative(0.84f, 1.18f, 0.84f, 2.71f, 0.0f, 3.89f)
                lineToRelative(1.68f, 1.69f)
                curveToRelative(2.02f, -2.02f, 2.02f, -5.07f, 0.0f, -7.27f)
                close()
                moveTo(20.07f, 2.0f)
                lineToRelative(-1.63f, 1.63f)
                curveToRelative(2.77f, 3.02f, 2.77f, 7.56f, 0.0f, 10.74f)
                lineTo(20.07f, 16.0f)
                curveToRelative(3.9f, -3.89f, 3.91f, -9.95f, 0.0f, -14.0f)
                close()
            }
        }
        return _recordVoiceOver!!
    }

private var _recordVoiceOver: ImageVector? = null
