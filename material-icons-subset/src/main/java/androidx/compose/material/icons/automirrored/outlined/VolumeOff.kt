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

package androidx.compose.material.icons.automirrored.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.AutoMirrored.Outlined.VolumeOff: ImageVector
    get() {
        if (_volumeOff != null) {
            return _volumeOff!!
        }
        _volumeOff = materialIcon(name = "AutoMirrored.Outlined.VolumeOff", autoMirror = true) {
            materialPath {
                moveTo(4.34f, 2.93f)
                lineTo(2.93f, 4.34f)
                lineTo(7.29f, 8.7f)
                lineTo(7.0f, 9.0f)
                lineTo(3.0f, 9.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(4.0f)
                lineToRelative(5.0f, 5.0f)
                verticalLineToRelative(-6.59f)
                lineToRelative(4.18f, 4.18f)
                curveToRelative(-0.65f, 0.49f, -1.38f, 0.88f, -2.18f, 1.11f)
                verticalLineToRelative(2.06f)
                curveToRelative(1.34f, -0.3f, 2.57f, -0.92f, 3.61f, -1.75f)
                lineToRelative(2.05f, 2.05f)
                lineToRelative(1.41f, -1.41f)
                lineTo(4.34f, 2.93f)
                close()
                moveTo(10.0f, 15.17f)
                lineTo(7.83f, 13.0f)
                lineTo(5.0f, 13.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.83f)
                lineToRelative(0.88f, -0.88f)
                lineTo(10.0f, 11.41f)
                verticalLineToRelative(3.76f)
                close()
                moveTo(19.0f, 12.0f)
                curveToRelative(0.0f, 0.82f, -0.15f, 1.61f, -0.41f, 2.34f)
                lineToRelative(1.53f, 1.53f)
                curveToRelative(0.56f, -1.17f, 0.88f, -2.48f, 0.88f, -3.87f)
                curveToRelative(0.0f, -4.28f, -2.99f, -7.86f, -7.0f, -8.77f)
                verticalLineToRelative(2.06f)
                curveToRelative(2.89f, 0.86f, 5.0f, 3.54f, 5.0f, 6.71f)
                close()
                moveTo(12.0f, 4.0f)
                lineToRelative(-1.88f, 1.88f)
                lineTo(12.0f, 7.76f)
                close()
                moveTo(16.5f, 12.0f)
                curveToRelative(0.0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                verticalLineToRelative(1.79f)
                lineToRelative(2.48f, 2.48f)
                curveToRelative(0.01f, -0.08f, 0.02f, -0.16f, 0.02f, -0.24f)
                close()
            }
        }
        return _volumeOff!!
    }

private var _volumeOff: ImageVector? = null
