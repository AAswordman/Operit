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

package androidx.a2ui.model.protocol

/** Common constants used across the A2UI protocol data models. */
public object A2uiProtocolConstants {
    /** The A2UI protocol version supported by this client library. */
    public const val PROTOCOL_VERSION: String = "v0.9"

    /** The default surface ID used for global errors when no specific surface ID is known. */
    internal const val GLOBAL_SURFACE_ID: String = "__global__"
}
