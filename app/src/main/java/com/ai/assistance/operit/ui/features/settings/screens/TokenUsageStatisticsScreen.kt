package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.collects.DefaultModelPricingCollect
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.model.BillingMode
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.ui.components.CustomScaffold
import java.util.Locale
import kotlinx.coroutines.launch

private data class ModelCost(
    val amount: Double,
    val currency: PricingCurrency
)

private const val DEFAULT_USD_TO_CNY_RATE = 7.2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenUsageStatisticsScreen(
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiPreferences = remember { ApiPreferences.getInstance(context) }
    val chatHistoryManager = remember { ChatHistoryManager.getInstance(context) }

    var totalChats by remember { mutableStateOf(0) }
    var totalMessages by remember { mutableStateOf(0) }

    val providerModelTokenUsage = remember { mutableStateMapOf<String, Triple<Long, Long, Long>>() }
    val providerModelRequestCounts = remember { mutableStateMapOf<String, Int>() }
    val modelPricing = remember { mutableStateMapOf<String, Triple<Double, Double, Double>>() }
    val modelBillingMode = remember { mutableStateMapOf<String, BillingMode>() }
    val modelPricePerRequest = remember { mutableStateMapOf<String, Double>() }
    val modelCurrencies = remember { mutableStateMapOf<String, PricingCurrency>() }

    var showPricingDialog by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }
    var showResetModelDialog by remember { mutableStateOf(false) }
    var resetModel by remember { mutableStateOf("") }

    var usdToCnyRate by remember { mutableStateOf(DEFAULT_USD_TO_CNY_RATE) }
    var usdToCnyRateInput by remember { mutableStateOf(DEFAULT_USD_TO_CNY_RATE.toString()) }

    LaunchedEffect(Unit) {
        apiPreferences.allProviderModelTokensFlow.collect { tokensMap ->
            providerModelTokenUsage.clear()
            providerModelTokenUsage.putAll(tokensMap)

            tokensMap.keys.forEach { providerModel ->
                val defaults = DefaultModelPricingCollect.getDefaultPricing(providerModel)
                modelCurrencies[providerModel] = defaults.currency

                if (!modelPricing.containsKey(providerModel)) {
                    modelPricing[providerModel] = Triple(
                        defaults.inputPricePerMillion,
                        defaults.outputPricePerMillion,
                        defaults.cachedInputPricePerMillion
                    )
                }
                if (!modelBillingMode.containsKey(providerModel)) {
                    modelBillingMode[providerModel] = defaults.billingMode
                }
                if (!modelPricePerRequest.containsKey(providerModel)) {
                    modelPricePerRequest[providerModel] = defaults.pricePerRequest
                }
            }
        }
    }

    LaunchedEffect(providerModelTokenUsage.keys.toSet()) {
        providerModelTokenUsage.keys.forEach { providerModel ->
            val defaults = DefaultModelPricingCollect.getDefaultPricing(providerModel)

            val inputPrice = apiPreferences.getModelInputPrice(providerModel)
            val outputPrice = apiPreferences.getModelOutputPrice(providerModel)
            val cachedInputPrice = apiPreferences.getModelCachedInputPrice(providerModel)
            modelPricing[providerModel] = if (
                inputPrice > 0.0 || outputPrice > 0.0 || cachedInputPrice > 0.0
            ) {
                Triple(inputPrice, outputPrice, cachedInputPrice)
            } else {
                Triple(
                    defaults.inputPricePerMillion,
                    defaults.outputPricePerMillion,
                    defaults.cachedInputPricePerMillion
                )
            }

            modelBillingMode[providerModel] = apiPreferences.getBillingModeForProviderModel(providerModel)

            val savedPricePerRequest = apiPreferences.getPricePerRequestForProviderModel(providerModel)
            modelPricePerRequest[providerModel] = if (savedPricePerRequest > 0.0) {
                savedPricePerRequest
            } else {
                defaults.pricePerRequest
            }
        }
    }

    LaunchedEffect(Unit) {
        val requestCounts = apiPreferences.getAllProviderModelRequestCounts()
        providerModelRequestCounts.clear()
        providerModelRequestCounts.putAll(requestCounts)
    }

    LaunchedEffect(Unit) {
        runCatching {
            totalChats = chatHistoryManager.getTotalChatCount()
            totalMessages = chatHistoryManager.getTotalMessageCount()
        }
    }

    LaunchedEffect(Unit) {
        val rate = apiPreferences.getUsdToCnyExchangeRate()
        if (rate > 0.0) {
            usdToCnyRate = rate
            usdToCnyRateInput = rate.toString()
        }
    }

    val providerModelCosts by remember {
        derivedStateOf {
            providerModelTokenUsage.mapValues { (providerModel, tokens) ->
                val defaults = DefaultModelPricingCollect.getDefaultPricing(providerModel)
                val currency = modelCurrencies[providerModel] ?: defaults.currency
                val billingMode = modelBillingMode[providerModel] ?: defaults.billingMode

                val amount = when (billingMode) {
                    BillingMode.TOKEN -> {
                        val pricing = modelPricing[providerModel] ?: Triple(
                            defaults.inputPricePerMillion,
                            defaults.outputPricePerMillion,
                            defaults.cachedInputPricePerMillion
                        )
                        val nonCachedInput = (tokens.first - tokens.third).coerceAtLeast(0L)
                        (nonCachedInput / 1_000_000.0 * pricing.first) +
                                (tokens.second / 1_000_000.0 * pricing.second) +
                                (tokens.third / 1_000_000.0 * pricing.third)
                    }

                    BillingMode.COUNT -> {
                        val pricePerRequest = modelPricePerRequest[providerModel] ?: defaults.pricePerRequest
                        val requestCount = providerModelRequestCounts[providerModel] ?: 0
                        requestCount * pricePerRequest
                    }
                }

                ModelCost(amount = amount, currency = currency)
            }
        }
    }

    val totalInputTokens = providerModelTokenUsage.values.sumOf { it.first }
    val totalOutputTokens = providerModelTokenUsage.values.sumOf { it.second }
    val totalCachedInputTokens = providerModelTokenUsage.values.sumOf { it.third }
    val totalTokens = totalInputTokens + totalOutputTokens
    val totalRequests = providerModelRequestCounts.values.sum()

    val totalCostCny = providerModelCosts.values.sumOf { cost ->
        convertToCny(cost.amount, cost.currency, usdToCnyRate)
    }

    val hasUsdCost = providerModelCosts.values.any { it.currency == PricingCurrency.USD && it.amount > 0.0 }

    val modelUsageDistribution by remember {
        derivedStateOf {
            providerModelTokenUsage.entries
                .map { it.key to (it.value.first + it.value.second) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
        }
    }

    CustomScaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showResetDialog = true },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = stringResource(id = R.string.settings_reset_all_counts)
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ExchangeRateSettingsCard(
                    rateInput = usdToCnyRateInput,
                    onRateInputChange = { usdToCnyRateInput = it },
                    onSave = {
                        val parsedRate = usdToCnyRateInput.toDoubleOrNull()
                        if (parsedRate != null && parsedRate > 0.0) {
                            usdToCnyRate = parsedRate
                            scope.launch {
                                apiPreferences.setUsdToCnyExchangeRate(parsedRate)
                            }
                        }
                    }
                )
            }

            item {
                TokenUsageSummarySection(
                    totalChats = totalChats,
                    totalMessages = totalMessages,
                    totalTokens = totalTokens,
                    totalInputTokens = totalInputTokens,
                    totalOutputTokens = totalOutputTokens,
                    totalCachedInputTokens = totalCachedInputTokens,
                    totalRequests = totalRequests,
                    totalCostText = formatCurrencyAmount(totalCostCny, PricingCurrency.CNY),
                    exchangeRateHint = if (hasUsdCost) {
                        stringResource(
                            id = R.string.settings_rate_applied_hint,
                            usdToCnyRate
                        )
                    } else {
                        null
                    }
                )
            }

            if (modelUsageDistribution.isNotEmpty()) {
                item {
                    ModelUsageDistributionSection(items = modelUsageDistribution)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.settings_model_details),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(id = R.string.settings_click_to_edit_pricing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val sortedProviderModels = providerModelTokenUsage.entries.sortedBy { it.key }

            if (sortedProviderModels.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(id = R.string.settings_no_token_records),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(sortedProviderModels) { (providerModel, tokens) ->
                    val defaults = DefaultModelPricingCollect.getDefaultPricing(providerModel)
                    val (input, output, cached) = tokens
                    val cost = providerModelCosts[providerModel]?.amount ?: 0.0
                    val currency = modelCurrencies[providerModel] ?: defaults.currency
                    val pricing = modelPricing[providerModel] ?: Triple(
                        defaults.inputPricePerMillion,
                        defaults.outputPricePerMillion,
                        defaults.cachedInputPricePerMillion
                    )
                    val billingMode = modelBillingMode[providerModel] ?: defaults.billingMode
                    val requestCount = providerModelRequestCounts[providerModel] ?: 0
                    val pricePerRequest = modelPricePerRequest[providerModel] ?: defaults.pricePerRequest
                    val displayCurrency = PricingCurrency.CNY

                    TokenUsageModelCard(
                        modelName = providerModel,
                        inputTokens = input,
                        cachedInputTokens = cached,
                        outputTokens = output,
                        requestCount = requestCount,
                        cost = convertToCny(cost, currency, usdToCnyRate),
                        inputPrice = convertToCny(pricing.first, currency, usdToCnyRate),
                        outputPrice = convertToCny(pricing.second, currency, usdToCnyRate),
                        billingMode = billingMode,
                        pricePerRequest = convertToCny(pricePerRequest, currency, usdToCnyRate),
                        currency = displayCurrency,
                        onClick = {
                            selectedModel = providerModel
                            showPricingDialog = true
                        },
                        onResetClick = {
                            resetModel = providerModel
                            showResetModelDialog = true
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }

    if (showPricingDialog && selectedModel.isNotEmpty()) {
        val defaults = DefaultModelPricingCollect.getDefaultPricing(selectedModel)
        val currentPricing = modelPricing[selectedModel] ?: Triple(
            defaults.inputPricePerMillion,
            defaults.outputPricePerMillion,
            defaults.cachedInputPricePerMillion
        )
        val currentBillingMode = modelBillingMode[selectedModel] ?: defaults.billingMode
        val currentPricePerRequest = modelPricePerRequest[selectedModel] ?: defaults.pricePerRequest
        val currency = modelCurrencies[selectedModel] ?: defaults.currency
        val editCurrency = PricingCurrency.CNY
        val currentPricingCny = Triple(
            convertToCny(currentPricing.first, currency, usdToCnyRate),
            convertToCny(currentPricing.second, currency, usdToCnyRate),
            convertToCny(currentPricing.third, currency, usdToCnyRate)
        )
        val currentPricePerRequestCny = convertToCny(
            currentPricePerRequest,
            currency,
            usdToCnyRate
        )

        var billingMode by remember { mutableStateOf(currentBillingMode) }
        var inputPrice by remember { mutableStateOf(formatEditablePrice(currentPricingCny.first)) }
        var outputPrice by remember { mutableStateOf(formatEditablePrice(currentPricingCny.second)) }
        var cachedInputPrice by remember { mutableStateOf(formatEditablePrice(currentPricingCny.third)) }
        var pricePerRequest by remember { mutableStateOf(formatEditablePrice(currentPricePerRequestCny)) }
        val tokenPriceInputsAreValid =
            inputPrice.toDoubleOrNull() != null &&
                    outputPrice.toDoubleOrNull() != null &&
                    cachedInputPrice.toDoubleOrNull() != null
        val countPriceInputIsValid = pricePerRequest.toDoubleOrNull() != null
        val pricingInputIsValid = when (billingMode) {
            BillingMode.TOKEN -> tokenPriceInputsAreValid
            BillingMode.COUNT -> countPriceInputIsValid
        }

        AlertDialog(
            onDismissRequest = { showPricingDialog = false },
            title = {
                Text(text = stringResource(id = R.string.settings_edit_model_pricing, selectedModel))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(id = R.string.settings_pricing_currency_hint, editCurrency.code),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = stringResource(id = R.string.settings_billing_mode),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = billingMode == BillingMode.TOKEN,
                            onClick = { billingMode = BillingMode.TOKEN },
                            label = { Text(stringResource(id = R.string.settings_billing_mode_token)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = billingMode == BillingMode.COUNT,
                            onClick = { billingMode = BillingMode.COUNT },
                            label = { Text(stringResource(id = R.string.settings_billing_mode_count)) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider()

                    if (billingMode == BillingMode.TOKEN) {
                        Text(
                            text = stringResource(
                                id = R.string.settings_pricing_description_with_currency,
                                editCurrency.code
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = inputPrice,
                            onValueChange = { inputPrice = it },
                            label = {
                                Text(
                                    "${stringResource(id = R.string.settings_input_price_per_million)} (${editCurrency.code})"
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = cachedInputPrice,
                            onValueChange = { cachedInputPrice = it },
                            label = {
                                Text(
                                    "${stringResource(id = R.string.settings_cached_input_price_per_million)} (${editCurrency.code})"
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = outputPrice,
                            onValueChange = { outputPrice = it },
                            label = {
                                Text(
                                    "${stringResource(id = R.string.settings_output_price_per_million)} (${editCurrency.code})"
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = stringResource(
                                id = R.string.settings_token_price_description_with_currency,
                                editCurrency.code
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = pricePerRequest,
                            onValueChange = { pricePerRequest = it },
                            label = {
                                Text(
                                    stringResource(
                                        id = R.string.settings_price_per_request_with_currency,
                                        editCurrency.code
                                    )
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pricingInputIsValid,
                    onClick = {
                        scope.launch {
                            modelBillingMode[selectedModel] = billingMode
                            apiPreferences.setBillingModeForProviderModel(selectedModel, billingMode)

                            if (billingMode == BillingMode.TOKEN) {
                                val inputPriceValueCny = inputPrice.toDoubleOrNull()
                                val outputPriceValueCny = outputPrice.toDoubleOrNull()
                                val cachedInputPriceValueCny = cachedInputPrice.toDoubleOrNull()

                                if (
                                    inputPriceValueCny != null &&
                                    outputPriceValueCny != null &&
                                    cachedInputPriceValueCny != null
                                ) {
                                    val inputPriceValue = convertCnyToPricingCurrency(
                                        inputPriceValueCny,
                                        currency,
                                        usdToCnyRate
                                    )
                                    val outputPriceValue = convertCnyToPricingCurrency(
                                        outputPriceValueCny,
                                        currency,
                                        usdToCnyRate
                                    )
                                    val cachedInputPriceValue = convertCnyToPricingCurrency(
                                        cachedInputPriceValueCny,
                                        currency,
                                        usdToCnyRate
                                    )

                                    modelPricing[selectedModel] = Triple(
                                        inputPriceValue,
                                        outputPriceValue,
                                        cachedInputPriceValue
                                    )
                                    apiPreferences.setModelInputPrice(selectedModel, inputPriceValue)
                                    apiPreferences.setModelOutputPrice(selectedModel, outputPriceValue)
                                    apiPreferences.setModelCachedInputPrice(
                                        selectedModel,
                                        cachedInputPriceValue
                                    )
                                }
                            } else {
                                val pricePerRequestValueCny = pricePerRequest.toDoubleOrNull()

                                if (pricePerRequestValueCny != null) {
                                    val pricePerRequestValue = convertCnyToPricingCurrency(
                                        pricePerRequestValueCny,
                                        currency,
                                        usdToCnyRate
                                    )
                                    modelPricePerRequest[selectedModel] = pricePerRequestValue
                                    apiPreferences.setPricePerRequestForProviderModel(
                                        selectedModel,
                                        pricePerRequestValue
                                    )
                                }
                            }
                        }

                        showPricingDialog = false
                    }
                ) {
                    Text(stringResource(id = R.string.settings_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPricingDialog = false }) {
                    Text(stringResource(id = R.string.settings_cancel))
                }
            }
        )
    }

    if (showResetModelDialog && resetModel.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showResetModelDialog = false },
            title = {
                Text(text = stringResource(id = R.string.settings_reset_model_confirmation))
            },
            text = {
                Text(text = stringResource(id = R.string.settings_reset_model_warning, resetModel))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            apiPreferences.resetProviderModelTokenCounts(resetModel)
                            providerModelRequestCounts.remove(resetModel)
                        }
                        showResetModelDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(id = R.string.settings_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetModelDialog = false }) {
                    Text(stringResource(id = R.string.settings_cancel))
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(text = stringResource(id = R.string.settings_reset_confirmation))
            },
            text = {
                Text(text = stringResource(id = R.string.settings_reset_warning))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            apiPreferences.resetAllProviderModelTokenCounts()
                            providerModelRequestCounts.clear()
                        }
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(id = R.string.settings_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(id = R.string.settings_cancel))
                }
            }
        )
    }
}

@Composable
private fun ExchangeRateSettingsCard(
    rateInput: String,
    onRateInputChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(id = R.string.settings_exchange_rate_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(id = R.string.settings_exchange_rate_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = rateInput,
                onValueChange = { onRateInputChange(it) },
                label = { Text(stringResource(id = R.string.settings_usd_to_cny_rate_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onSave) {
                    Text(stringResource(id = R.string.settings_save))
                }
            }
        }
    }
}

@Composable
private fun TokenUsageModelCard(
    modelName: String,
    inputTokens: Long,
    cachedInputTokens: Long,
    outputTokens: Long,
    requestCount: Int,
    cost: Double,
    inputPrice: Double,
    outputPrice: Double,
    billingMode: BillingMode,
    pricePerRequest: Double,
    currency: PricingCurrency,
    onClick: () -> Unit,
    onResetClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = modelName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                text = when (billingMode) {
                                    BillingMode.TOKEN -> stringResource(id = R.string.settings_billing_mode_token)
                                    BillingMode.COUNT -> stringResource(id = R.string.settings_billing_mode_count)
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = when (billingMode) {
                                BillingMode.TOKEN -> MaterialTheme.colorScheme.secondaryContainer
                                BillingMode.COUNT -> MaterialTheme.colorScheme.tertiaryContainer
                            }
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onResetClick) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = stringResource(id = R.string.settings_reset_model_counts),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(id = R.string.settings_edit_pricing),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = R.string.settings_request_count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$requestCount",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(id = R.string.settings_input_tokens),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$inputTokens",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (cachedInputTokens > 0L) {
                        val hitRate = if (inputTokens > 0) (cachedInputTokens * 100.0 / inputTokens) else 0.0
                        Text(
                            text = stringResource(R.string.settings_cached_tokens, cachedInputTokens) + " (${String.format("%.1f", hitRate)}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (billingMode == BillingMode.TOKEN) {
                        Text(
                            text = formatPricePerMillion(inputPrice, currency),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(id = R.string.settings_output_tokens),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$outputTokens",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (billingMode == BillingMode.TOKEN) {
                        Text(
                            text = formatPricePerMillion(outputPrice, currency),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(id = R.string.settings_total_cost),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatCurrencyAmount(cost, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (billingMode == BillingMode.COUNT) {
                        Text(
                            text = stringResource(
                                id = R.string.settings_per_request_cost_with_currency,
                                currency.symbol,
                                pricePerRequest
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun convertToCny(amount: Double, currency: PricingCurrency, usdToCnyRate: Double): Double {
    return when (currency) {
        PricingCurrency.CNY -> amount
        PricingCurrency.USD -> amount * usdToCnyRate
    }
}

private fun convertCnyToPricingCurrency(
    amount: Double,
    currency: PricingCurrency,
    usdToCnyRate: Double
): Double {
    return when (currency) {
        PricingCurrency.CNY -> amount
        PricingCurrency.USD -> amount / usdToCnyRate
    }
}

private fun formatCurrencyAmount(amount: Double, currency: PricingCurrency): String {
    return "${currency.symbol}${String.format(Locale.US, "%.2f", amount)}"
}

private fun formatPricePerMillion(price: Double, currency: PricingCurrency): String {
    return "${currency.symbol}${String.format(Locale.US, "%.2f", price)}/1M"
}

private fun formatEditablePrice(price: Double): String {
    return String.format(Locale.US, "%.6f", price).trimEnd('0').trimEnd('.')
}
