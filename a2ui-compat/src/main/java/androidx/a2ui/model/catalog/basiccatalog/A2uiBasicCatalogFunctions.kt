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

@file:JvmName("A2uiBasicCatalogFunctions")

package androidx.a2ui.model.catalog.basiccatalog

import androidx.a2ui.model.catalog.A2uiFunction
import androidx.a2ui.model.catalog.functions.A2uiAndFunction
import androidx.a2ui.model.catalog.functions.A2uiEmailFunction
import androidx.a2ui.model.catalog.functions.A2uiFormatCurrencyFunction
import androidx.a2ui.model.catalog.functions.A2uiFormatDateFunction
import androidx.a2ui.model.catalog.functions.A2uiFormatNumberFunction
import androidx.a2ui.model.catalog.functions.A2uiFormatStringFunction
import androidx.a2ui.model.catalog.functions.A2uiLengthFunction
import androidx.a2ui.model.catalog.functions.A2uiLocaleProvider
import androidx.a2ui.model.catalog.functions.A2uiMessageFormatter
import androidx.a2ui.model.catalog.functions.A2uiNotFunction
import androidx.a2ui.model.catalog.functions.A2uiNumericFunction
import androidx.a2ui.model.catalog.functions.A2uiOpenUrlFunction
import androidx.a2ui.model.catalog.functions.A2uiOrFunction
import androidx.a2ui.model.catalog.functions.A2uiPluralizeFunction
import androidx.a2ui.model.catalog.functions.A2uiRegexFunction
import androidx.a2ui.model.catalog.functions.A2uiRequiredFunction
import androidx.a2ui.model.catalog.functions.A2uiUrlOpener

/**
 * Creates and returns a list containing all the basic catalog [A2uiFunction]s.
 *
 * @param urlOpener Platform-specific handler to open external URLs
 * @param messageFormatter Platform-specific formatter for localized messages and plurals
 * @param localeProvider Provider of the active locale, defaults to null (falls back to default
 *   function-specific providers)
 * @return A list of all standard library functions supported by A2UI
 */
@JvmOverloads
public fun createBasicCatalogFunctions(
    urlOpener: A2uiUrlOpener,
    messageFormatter: A2uiMessageFormatter,
    localeProvider: A2uiLocaleProvider? = null,
): List<A2uiFunction> {
    return listOf(
        A2uiAndFunction.INSTANCE,
        A2uiEmailFunction.INSTANCE,
        if (localeProvider != null) A2uiFormatCurrencyFunction(localeProvider)
        else A2uiFormatCurrencyFunction.INSTANCE,
        if (localeProvider != null) A2uiFormatDateFunction(localeProvider)
        else A2uiFormatDateFunction.INSTANCE,
        if (localeProvider != null) A2uiFormatNumberFunction(localeProvider)
        else A2uiFormatNumberFunction.INSTANCE,
        A2uiFormatStringFunction.INSTANCE,
        A2uiLengthFunction.INSTANCE,
        A2uiNotFunction.INSTANCE,
        A2uiNumericFunction.INSTANCE,
        A2uiOpenUrlFunction(urlOpener),
        A2uiOrFunction.INSTANCE,
        if (localeProvider != null) A2uiPluralizeFunction(messageFormatter, localeProvider)
        else A2uiPluralizeFunction(messageFormatter),
        A2uiRegexFunction.INSTANCE,
        A2uiRequiredFunction.INSTANCE,
    )
}
