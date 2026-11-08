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

package androidx.a2ui.model.processor

import androidx.a2ui.model.protocol.A2uiServerToClientMessage

/**
 * Parses raw input into [A2uiServerToClientMessage] protocol messages.
 *
 * @param T the type of input to parse
 */
public fun interface A2uiMessageParser<T> {
    /**
     * Parses the [input] into an [A2uiServerToClientMessage].
     *
     * @throws androidx.a2ui.model.protocol.A2uiException.A2uiValidationException if the input is
     *   malformed or invalid
     */
    public fun parse(input: T): A2uiServerToClientMessage
}
