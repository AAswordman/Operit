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

package androidx.a2ui.compose.runtime

/**
 * A marker interface representing a registry of A2UI components and functions that can be utilized
 * by the Jetpack Compose A2UI runtime layer.
 *
 * This interface serves as an opaque token to provide catalogs to the runtime's
 * [a2uiRuntimeMessageProcessor] without tightly coupling the A2UI renderer runtime layer to
 * specific visual UI framework implementations. This marker interface also allows hiding the A2UI
 * core catalog type ([androidx.a2ui.engine.catalog.A2uiCoreCatalog]) from the public API.
 *
 * **Note for implementors:** Any concrete implementation of this interface provided to the runtime
 * must also implement the underlying [androidx.a2ui.engine.catalog.A2uiCoreCatalog] interface so
 * that it can be successfully processed by the core data layer.
 */
public interface A2uiRuntimeCatalog
