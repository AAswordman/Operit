/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.a2ui.model.catalog.functions

import java.util.Locale

/** Resolves the current [Locale]. */
public fun interface A2uiLocaleProvider {

    /** Returns the resolved [Locale]. */
    public fun getLocale(): Locale

    public companion object {
        /** Default provider using the current system locale. */
        @JvmField
        public val Default: A2uiLocaleProvider = A2uiLocaleProvider { Locale.getDefault() }
    }
}
