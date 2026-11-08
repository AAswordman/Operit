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

public val Icons.Filled.CreditCard: ImageVector
    get() {
        if (_creditCard != null) {
            return _creditCard!!
        }
        _creditCard = materialIcon(name = "Filled.CreditCard") {
            materialPath {
                moveTo(20.0f, 4.0f)
                lineTo(4.0f, 4.0f)
                curveToRelative(-1.11f, 0.0f, -1.99f, 0.89f, -1.99f, 2.0f)
                lineTo(2.0f, 18.0f)
                curveToRelative(0.0f, 1.11f, 0.89f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.11f, 0.0f, 2.0f, -0.89f, 2.0f, -2.0f)
                lineTo(22.0f, 6.0f)
                curveToRelative(0.0f, -1.11f, -0.89f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(20.0f, 18.0f)
                lineTo(4.0f, 18.0f)
                verticalLineToRelative(-6.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(6.0f)
                close()
                moveTo(20.0f, 8.0f)
                lineTo(4.0f, 8.0f)
                lineTo(4.0f, 6.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(2.0f)
                close()
            }
        }
        return _creditCard!!
    }

private var _creditCard: ImageVector? = null
