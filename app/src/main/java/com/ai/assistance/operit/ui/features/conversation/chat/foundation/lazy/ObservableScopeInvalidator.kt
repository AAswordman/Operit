/*
 * Copyright 2023 The Android Open Source Project
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

package com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import kotlin.jvm.JvmInline

/**
 * Simple wrapper over a mutable state which allows to invalidate an observable scope. We might
 * consider providing something like this in the public api in the future.
 */
@JvmInline
internal value class ObservableScopeInvalidator(
    private val state: MutableState<Unit> = mutableStateOf(Unit, neverEqualPolicy())
) {
    fun attachToScope() {
        state.value
    }

    fun invalidateScope() {
        state.value = Unit
    }
}
