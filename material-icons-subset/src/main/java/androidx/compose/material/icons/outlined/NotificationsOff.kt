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

package androidx.compose.material.icons.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Outlined.NotificationsOff: ImageVector
    get() {
        if (_notificationsOff != null) {
            return _notificationsOff!!
        }
        _notificationsOff = materialIcon(name = "Outlined.NotificationsOff") {
            materialPath {
                moveTo(12.0f, 22.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                horizontalLineToRelative(-4.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                close()
                moveTo(12.0f, 6.5f)
                curveToRelative(2.49f, 0.0f, 4.0f, 2.02f, 4.0f, 4.5f)
                verticalLineToRelative(0.1f)
                lineToRelative(2.0f, 2.0f)
                lineTo(18.0f, 11.0f)
                curveToRelative(0.0f, -3.07f, -1.63f, -5.64f, -4.5f, -6.32f)
                lineTo(13.5f, 4.0f)
                curveToRelative(0.0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f)
                reflectiveCurveToRelative(-1.5f, 0.67f, -1.5f, 1.5f)
                verticalLineToRelative(0.68f)
                curveToRelative(-0.24f, 0.06f, -0.47f, 0.15f, -0.69f, 0.23f)
                lineToRelative(1.64f, 1.64f)
                curveToRelative(0.18f, -0.02f, 0.36f, -0.05f, 0.55f, -0.05f)
                close()
                moveTo(5.41f, 3.35f)
                lineTo(4.0f, 4.76f)
                lineToRelative(2.81f, 2.81f)
                curveTo(6.29f, 8.57f, 6.0f, 9.74f, 6.0f, 11.0f)
                verticalLineToRelative(5.0f)
                lineToRelative(-2.0f, 2.0f)
                verticalLineToRelative(1.0f)
                horizontalLineToRelative(14.24f)
                lineToRelative(1.74f, 1.74f)
                lineToRelative(1.41f, -1.41f)
                lineTo(5.41f, 3.35f)
                close()
                moveTo(16.0f, 17.0f)
                lineTo(8.0f, 17.0f)
                verticalLineToRelative(-6.0f)
                curveToRelative(0.0f, -0.68f, 0.12f, -1.32f, 0.34f, -1.9f)
                lineTo(16.0f, 16.76f)
                lineTo(16.0f, 17.0f)
                close()
            }
        }
        return _notificationsOff!!
    }

private var _notificationsOff: ImageVector? = null
