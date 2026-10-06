package com.ai.assistance.operit.ui.features.tokenstats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.model.BillingMode
import com.ai.assistance.operit.data.stats.TokenStatsPriceDraft
import com.ai.assistance.operit.data.stats.TokenStatsPriceScope
import com.ai.assistance.operit.data.stats.TokenStatsPriceSetting
import com.ai.assistance.operit.data.stats.DEFAULT_TOKEN_PEAK_TIME_RANGES
import com.ai.assistance.operit.data.stats.TokenPeakTimeRange
import com.ai.assistance.operit.data.stats.TokenPricingRules
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 信息架构重构后弹窗全部回到 M3 默认配色（随主题明暗自动成立），
 * 主色不再作为容器色出现。
 */

internal fun datePickerMillisToLocalDate(utcMidnightMs: Long): java.time.LocalDate =
    Instant.ofEpochMilli(utcMidnightMs).atZone(java.time.ZoneOffset.UTC).toLocalDate()

internal fun customRangeInclusiveEnd(
    startDate: java.time.LocalDate,
    endDate: java.time.LocalDate,
    zone: ZoneId,
): com.ai.assistance.operit.data.stats.TokenStatsTimeRange {
    require(!endDate.isBefore(startDate)) { "end date must not be before start date" }
    val startMs = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val endMs = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return com.ai.assistance.operit.data.stats.TokenStatsTimeRanges.customRange(startMs, endMs)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TokenStatsDateRangeDialog(
    zone: ZoneId,
    maxRangeDays: Long,
    initialRange: com.ai.assistance.operit.data.stats.TokenStatsTimeRange?,
    onConfirm: (startMs: Long, endMs: Long) -> Boolean,
    onDismiss: () -> Unit,
) {
    var inlineError by remember { mutableStateOf<String?>(null) }
    val initialStartDateMillis = initialRange
        ?.startMs
        ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        ?.atStartOfDay(java.time.ZoneOffset.UTC)
        ?.toInstant()
        ?.toEpochMilli()
    val initialEndDateMillis = initialRange
        ?.endMs
        ?.minus(1L)
        ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        ?.atStartOfDay(java.time.ZoneOffset.UTC)
        ?.toInstant()
        ?.toEpochMilli()
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartDateMillis,
        initialSelectedEndDateMillis = initialEndDateMillis,
    )
    LaunchedEffect(
        pickerState.selectedStartDateMillis,
        pickerState.selectedEndDateMillis,
    ) {
        inlineError = null
    }

    val invalidRangeText = stringResource(R.string.token_stats_custom_range_invalid)
    val rangeTooLongText = stringResource(R.string.token_stats_custom_range_too_long)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 360.dp),
        confirmButton = {
            TextButton(
                enabled =
                    pickerState.selectedStartDateMillis != null &&
                        pickerState.selectedEndDateMillis != null,
                onClick = {
                    val start = pickerState.selectedStartDateMillis ?: return@TextButton
                    val end = pickerState.selectedEndDateMillis ?: return@TextButton
                    val range = customRangeInclusiveEnd(
                        datePickerMillisToLocalDate(start),
                        datePickerMillisToLocalDate(end),
                        zone,
                    )
                    val isUnchangedInitialRange = range == initialRange
                    inlineError =
                        when (validateCustomRange(range.startMs, range.endMs, zone, maxRangeDays)) {
                            CustomRangeValidation.INVALID_BOUNDS -> invalidRangeText
                            CustomRangeValidation.TOO_LONG ->
                                if (isUnchangedInitialRange) null else rangeTooLongText
                            CustomRangeValidation.VALID -> null
                        }
                    if (inlineError == null && onConfirm(range.startMs, range.endMs)) onDismiss()
                },
            ) {
                Text(stringResource(R.string.token_stats_custom_range_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    ) {
        Column {
            DateRangePicker(
                state = pickerState,
                title = {
                    Text(
                        text = stringResource(R.string.token_stats_date_range),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                    )
                },
                headline = {
                    Text(
                        text = formatDatePickerSelection(
                            pickerState.selectedStartDateMillis,
                            pickerState.selectedEndDateMillis,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                    )
                },
                showModeToggle = false,
            )
            inlineError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 24.dp, bottom = 12.dp),
                )
            }
        }
    }
}

private fun formatDatePickerSelection(startMillis: Long?, endMillis: Long?): String {
    if (startMillis == null) return ""
    val start = datePickerMillisToLocalDate(startMillis).format(datePickerSelectionFormatter)
    if (endMillis == null) return start
    val end = datePickerMillisToLocalDate(endMillis).format(datePickerSelectionFormatter)
    return "$start - $end"
}

private val datePickerSelectionFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.getDefault())

@Composable
internal fun PriceSettingsDialog(
    existing: TokenStatsPriceSetting?,
    initialDraft: TokenStatsPriceDraft,
    configurationName: String?,
    onSave: (TokenStatsPriceDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = initialDraft.scope
    val provider = initialDraft.provider
    val model = initialDraft.model
    val configId = initialDraft.configId.orEmpty()
    var billingMode by remember(existing, initialDraft) {
        mutableStateOf(existing?.billingMode ?: initialDraft.billingMode)
    }
    var currency by remember(existing, initialDraft) {
        mutableStateOf(existing?.currency ?: initialDraft.currency)
    }
    var inputPrice by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(existing?.inputPricePerMillion ?: initialDraft.inputPricePerMillion)
        )
    }
    var cachedInputPrice by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(
                existing?.cachedInputPricePerMillion ?: initialDraft.cachedInputPricePerMillion
            )
        )
    }
    var cacheWritePrice by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(
                existing?.cacheWritePricePerMillion ?: initialDraft.cacheWritePricePerMillion
            )
        )
    }
    var outputPrice by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(existing?.outputPricePerMillion ?: initialDraft.outputPricePerMillion)
        )
    }
    var pricePerRequest by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(existing?.pricePerRequest ?: initialDraft.pricePerRequest)
        )
    }
    var peakPricingEnabled by remember(existing, initialDraft) {
        mutableStateOf(existing?.peakPricingEnabled ?: initialDraft.peakPricingEnabled)
    }
    var weekendOffPeakPricingEnabled by remember(existing, initialDraft) {
        mutableStateOf(
            existing?.weekendOffPeakPricingEnabled
                ?: initialDraft.weekendOffPeakPricingEnabled
        )
    }
    var holidayOffPeakPricingEnabled by remember(existing, initialDraft) {
        mutableStateOf(
            existing?.holidayOffPeakPricingEnabled
                ?: initialDraft.holidayOffPeakPricingEnabled
        )
    }
    var advancedRulesExpanded by remember { mutableStateOf(false) }
    var peakExpanded by remember { mutableStateOf(false) }
    var peakPeriods by remember(existing, initialDraft) {
        mutableStateOf(
            (existing?.peakSchedule ?: initialDraft.peakSchedule)
                .ifEmpty { DEFAULT_TOKEN_PEAK_TIME_RANGES }
                .map { TokenPricingRules.formatTimeMinutes(it.startMinute) to TokenPricingRules.formatTimeMinutes(it.endMinute) }
        )
    }
    var peakInputMultiplier by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(existing?.peakInputMultiplier ?: initialDraft.peakInputMultiplier)
        )
    }
    var peakCachedInputMultiplier by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(
                existing?.peakCachedInputMultiplier ?: initialDraft.peakCachedInputMultiplier
            )
        )
    }
    var peakCacheWriteMultiplier by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(
                existing?.peakCacheWriteMultiplier ?: initialDraft.peakCacheWriteMultiplier
            )
        )
    }
    var peakOutputMultiplier by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(existing?.peakOutputMultiplier ?: initialDraft.peakOutputMultiplier)
        )
    }
    var longContextPricingEnabled by remember(existing, initialDraft) {
        mutableStateOf(
            existing?.longContextPricingEnabled ?: initialDraft.longContextPricingEnabled
        )
    }
    var longContextExpanded by remember { mutableStateOf(false) }
    var longContextThreshold by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditableLong(existing?.longContextThreshold ?: initialDraft.longContextThreshold)
        )
    }
    var longContextInputMultiplier by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(
                existing?.longContextInputMultiplier ?: initialDraft.longContextInputMultiplier
            )
        )
    }
    var longContextCachedInputMultiplier by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(
                existing?.longContextCachedInputMultiplier
                    ?: initialDraft.longContextCachedInputMultiplier
            )
        )
    }
    var longContextCacheWriteMultiplier by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(
                existing?.longContextCacheWriteMultiplier
                    ?: initialDraft.longContextCacheWriteMultiplier
            )
        )
    }
    var longContextOutputMultiplier by remember(existing, initialDraft) {
        mutableStateOf(
            formatEditablePrice(
                existing?.longContextOutputMultiplier ?: initialDraft.longContextOutputMultiplier
            )
        )
    }
    val priceFields =
        if (billingMode == BillingMode.TOKEN) {
            listOf(inputPrice, cachedInputPrice, cacheWritePrice, outputPrice)
        } else {
            listOf(pricePerRequest)
        }
    val allPricesValid =
        priceFields.all { raw ->
            raw.isBlank() ||
                raw.toDoubleOrNull()?.let { it.isFinite() && it >= 0.0 } == true
        }
    val parsedPeakPeriods = parsePeakPeriods(peakPeriods)
    val peakRulesValid =
        !peakPricingEnabled ||
            (parsedPeakPeriods != null &&
                peakPeriods.isNotEmpty() &&
                listOf(
                    peakInputMultiplier,
                    peakCachedInputMultiplier,
                    peakCacheWriteMultiplier,
                    peakOutputMultiplier,
                ).all(::isValidMultiplier))
    val longContextRulesValid =
        !longContextPricingEnabled ||
            (longContextThreshold.toLongOrNull()?.let { it > 0L } == true &&
                listOf(
                    longContextInputMultiplier,
                    longContextCachedInputMultiplier,
                    longContextCacheWriteMultiplier,
                    longContextOutputMultiplier,
                ).all(::isValidMultiplier))
    val advancedRulesValid =
        billingMode != BillingMode.TOKEN || (peakRulesValid && longContextRulesValid)
    val targetValid = scope != TokenStatsPriceScope.CONFIG || configId.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.token_stats_pricing_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "$provider · $model",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (scope == TokenStatsPriceScope.CONFIG) {
                    Text(
                        text = configurationName.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = billingMode == BillingMode.TOKEN,
                        onClick = {
                            if (billingMode != BillingMode.TOKEN) {
                                pricePerRequest = ""
                                billingMode = BillingMode.TOKEN
                            }
                        },
                        label = { Text(stringResource(R.string.settings_billing_mode_token)) },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = billingMode == BillingMode.COUNT,
                        onClick = {
                            if (billingMode != BillingMode.COUNT) {
                                inputPrice = ""
                                cachedInputPrice = ""
                                cacheWritePrice = ""
                                outputPrice = ""
                                billingMode = BillingMode.COUNT
                            }
                        },
                        label = { Text(stringResource(R.string.settings_billing_mode_count)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = currency == PricingCurrency.CNY,
                        onClick = { currency = PricingCurrency.CNY },
                        label = { Text(stringResource(R.string.token_stats_currency_cny)) },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = currency == PricingCurrency.USD,
                        onClick = { currency = PricingCurrency.USD },
                        label = { Text(stringResource(R.string.token_stats_currency_usd)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalDivider()
                if (billingMode == BillingMode.TOKEN) {
                    PriceField(
                        label = stringResource(R.string.token_stats_pricing_input),
                        value = inputPrice,
                        onChange = { inputPrice = it },
                    )
                    PriceField(
                        label = stringResource(R.string.token_stats_pricing_cached),
                        value = cachedInputPrice,
                        onChange = { cachedInputPrice = it },
                    )
                    PriceField(
                        label = stringResource(R.string.token_stats_pricing_cache_write),
                        value = cacheWritePrice,
                        onChange = { cacheWritePrice = it },
                    )
                    PriceField(
                        label = stringResource(R.string.token_stats_pricing_output),
                        value = outputPrice,
                        onChange = { outputPrice = it },
                    )
                    HorizontalDivider()
                    PricingRuleGroup(
                        title = stringResource(R.string.token_stats_advanced_pricing),
                        expanded = advancedRulesExpanded,
                        onExpandedChange = { advancedRulesExpanded = it },
                    ) {
                        PricingRuleSection(
                            title = stringResource(R.string.token_stats_peak_pricing),
                        enabled = peakPricingEnabled,
                        expanded = peakExpanded,
                        onEnabledChange = { peakPricingEnabled = it },
                        onExpandedChange = { peakExpanded = it },
                    ) {
                        PricingRuleSwitch(
                            title = stringResource(R.string.token_stats_weekend_off_peak),
                            checked = weekendOffPeakPricingEnabled,
                            onCheckedChange = { weekendOffPeakPricingEnabled = it },
                        )
                        PricingRuleSwitch(
                            title = stringResource(R.string.token_stats_holiday_off_peak),
                            checked = holidayOffPeakPricingEnabled,
                            onCheckedChange = { holidayOffPeakPricingEnabled = it },
                        )
                        Text(
                            text = stringResource(R.string.token_stats_holiday_schedule_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(
                                R.string.token_stats_pricing_timezone,
                                TokenPricingRules.PRICING_ZONE.id,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(R.string.token_stats_peak_periods),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        peakPeriods.forEachIndexed { index, period ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                TimeField(
                                    label = stringResource(R.string.token_stats_peak_start),
                                    value = period.first,
                                    onChange = { value ->
                                        peakPeriods = peakPeriods.mapIndexed { itemIndex, item ->
                                            if (itemIndex == index) item.copy(first = value) else item
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                TimeField(
                                    label = stringResource(R.string.token_stats_peak_end),
                                    value = period.second,
                                    onChange = { value ->
                                        peakPeriods = peakPeriods.mapIndexed { itemIndex, item ->
                                            if (itemIndex == index) item.copy(second = value) else item
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                if (peakPeriods.size > 1) {
                                    IconButton(
                                        onClick = {
                                            peakPeriods = peakPeriods.filterIndexed { itemIndex, _ ->
                                                itemIndex != index
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.RemoveCircleOutline,
                                            contentDescription = stringResource(
                                                R.string.token_stats_remove_peak_period,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                        TextButton(
                            onClick = { peakPeriods = peakPeriods + ("09:00" to "12:00") },
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(stringResource(R.string.token_stats_add_peak_period))
                        }
                        MultiplierGrid(
                            firstLabel = stringResource(R.string.token_stats_peak_input_multiplier),
                            firstValue = peakInputMultiplier,
                            onFirstChange = { peakInputMultiplier = it },
                            secondLabel = stringResource(R.string.token_stats_peak_cached_multiplier),
                            secondValue = peakCachedInputMultiplier,
                            onSecondChange = { peakCachedInputMultiplier = it },
                            thirdLabel = stringResource(R.string.token_stats_peak_cache_write_multiplier),
                            thirdValue = peakCacheWriteMultiplier,
                            onThirdChange = { peakCacheWriteMultiplier = it },
                            fourthLabel = stringResource(R.string.token_stats_peak_output_multiplier),
                            fourthValue = peakOutputMultiplier,
                            onFourthChange = { peakOutputMultiplier = it },
                        )
                    }
                    PricingRuleSection(
                        title = stringResource(R.string.token_stats_long_context_pricing),
                        enabled = longContextPricingEnabled,
                        expanded = longContextExpanded,
                        onEnabledChange = { longContextPricingEnabled = it },
                        onExpandedChange = { longContextExpanded = it },
                    ) {
                        PriceField(
                            label = stringResource(R.string.token_stats_long_context_threshold),
                            value = longContextThreshold,
                            onChange = { longContextThreshold = it },
                            keyboardType = KeyboardType.Number,
                        )
                        MultiplierGrid(
                            firstLabel = stringResource(R.string.token_stats_long_context_input_multiplier),
                            firstValue = longContextInputMultiplier,
                            onFirstChange = { longContextInputMultiplier = it },
                            secondLabel = stringResource(R.string.token_stats_long_context_cached_multiplier),
                            secondValue = longContextCachedInputMultiplier,
                            onSecondChange = { longContextCachedInputMultiplier = it },
                            thirdLabel = stringResource(R.string.token_stats_long_context_cache_write_multiplier),
                            thirdValue = longContextCacheWriteMultiplier,
                            onThirdChange = { longContextCacheWriteMultiplier = it },
                            fourthLabel = stringResource(R.string.token_stats_long_context_output_multiplier),
                            fourthValue = longContextOutputMultiplier,
                            onFourthChange = { longContextOutputMultiplier = it },
                        )
                        Text(
                            text = stringResource(R.string.token_stats_long_context_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    }
                } else {
                    PriceField(
                        label = stringResource(R.string.token_stats_pricing_per_request),
                        value = pricePerRequest,
                        onChange = { pricePerRequest = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = targetValid && allPricesValid && advancedRulesValid,
                onClick = {
                    val parse = { raw: String -> raw.trim().toDoubleOrNull() }
                    onSave(
                        TokenStatsPriceDraft(
                            scope = scope,
                            provider = provider,
                            model = model,
                            configId = configId.takeIf { scope == TokenStatsPriceScope.CONFIG },
                            billingMode = billingMode,
                            currency = currency,
                            inputPricePerMillion =
                                if (billingMode == BillingMode.TOKEN) parse(inputPrice) else null,
                            cachedInputPricePerMillion =
                                if (billingMode == BillingMode.TOKEN) {
                                    parse(cachedInputPrice)
                                } else {
                                    null
                                },
                            cacheWritePricePerMillion =
                                if (billingMode == BillingMode.TOKEN) {
                                    parse(cacheWritePrice)
                                } else {
                                    null
                                },
                            outputPricePerMillion =
                                if (billingMode == BillingMode.TOKEN) parse(outputPrice) else null,
                            pricePerRequest =
                                if (billingMode == BillingMode.COUNT) {
                                    parse(pricePerRequest)
                                } else {
                                    null
                                },
                            peakPricingEnabled = peakPricingEnabled,
                            weekendOffPeakPricingEnabled = weekendOffPeakPricingEnabled,
                            holidayOffPeakPricingEnabled = holidayOffPeakPricingEnabled,
                            peakSchedule = parsedPeakPeriods ?: DEFAULT_TOKEN_PEAK_TIME_RANGES,
                            peakInputMultiplier = peakInputMultiplier.toDoubleOrNull() ?: 1.0,
                            peakCachedInputMultiplier = peakCachedInputMultiplier.toDoubleOrNull() ?: 1.0,
                            peakCacheWriteMultiplier = peakCacheWriteMultiplier.toDoubleOrNull() ?: 1.0,
                            peakOutputMultiplier = peakOutputMultiplier.toDoubleOrNull() ?: 1.0,
                            longContextPricingEnabled = longContextPricingEnabled,
                            longContextThreshold = longContextThreshold.toLongOrNull(),
                            longContextInputMultiplier = longContextInputMultiplier.toDoubleOrNull() ?: 1.0,
                            longContextCachedInputMultiplier =
                                longContextCachedInputMultiplier.toDoubleOrNull() ?: 1.0,
                            longContextCacheWriteMultiplier =
                                longContextCacheWriteMultiplier.toDoubleOrNull() ?: 1.0,
                            longContextOutputMultiplier =
                                longContextOutputMultiplier.toDoubleOrNull() ?: 1.0,
                        )
                    )
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
private fun PricingRuleGroup(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onExpandedChange(!expanded) }) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun PricingRuleSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PricingRuleSection(
    title: String,
    enabled: Boolean,
    expanded: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
            IconButton(
                enabled = enabled,
                onClick = { onExpandedChange(!expanded) },
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }
        }
        if (enabled && expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun MultiplierGrid(
    firstLabel: String,
    firstValue: String,
    onFirstChange: (String) -> Unit,
    secondLabel: String,
    secondValue: String,
    onSecondChange: (String) -> Unit,
    thirdLabel: String,
    thirdValue: String,
    onThirdChange: (String) -> Unit,
    fourthLabel: String,
    fourthValue: String,
    onFourthChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PriceField(
                label = firstLabel,
                value = firstValue,
                onChange = onFirstChange,
                modifier = Modifier.weight(1f),
            )
            PriceField(
                label = secondLabel,
                value = secondValue,
                onChange = onSecondChange,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PriceField(
                label = thirdLabel,
                value = thirdValue,
                onChange = onThirdChange,
                modifier = Modifier.weight(1f),
            )
            PriceField(
                label = fourthLabel,
                value = fourthValue,
                onChange = onFourthChange,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TimeField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PriceField(
        label = label,
        value = value,
        onChange = onChange,
        keyboardType = KeyboardType.Text,
        modifier = modifier,
    )
}

@Composable
private fun PriceField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

private fun parsePeakPeriods(
    values: List<Pair<String, String>>,
): List<TokenPeakTimeRange>? {
    val result = mutableListOf<TokenPeakTimeRange>()
    values.forEach { (start, end) ->
        val startMinute = TokenPricingRules.parseTimeMinutes(start) ?: return null
        val endMinute = TokenPricingRules.parseTimeMinutes(end) ?: return null
        val period = runCatching { TokenPeakTimeRange(startMinute, endMinute) }.getOrNull()
            ?: return null
        result += period
    }
    return result
}

private fun isValidMultiplier(raw: String): Boolean =
    raw.toDoubleOrNull()?.let { it.isFinite() && it >= 0.0 } == true

private fun formatEditableLong(value: Long?): String = value?.toString().orEmpty()

private fun formatEditablePrice(value: Double?): String =
    value?.let { String.format(Locale.US, "%.6f", it).trimEnd('0').trimEnd('.') } ?: ""
