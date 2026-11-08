package com.ai.assistance.operit.ui.common.a2ui

import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.a2ui.compose.runtime.*
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneOffset
import com.ai.assistance.operit.R

internal val LocalCardInteractive = compositionLocalOf { false }
internal val LocalCardIconWarning = compositionLocalOf<() -> Unit> { {} }
internal val LocalCardReadOnly = compositionLocalOf { false }
internal val LocalCardBrowse = compositionLocalOf { false }
internal val LocalCardGridCell = compositionLocalOf { false }

internal fun cardNumberLabel(value: Float): String =
    if (!value.isFinite()) "—" else java.math.BigDecimal(value.toString()).setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

internal fun updateCardField(onError: () -> Unit, update: () -> Unit): Boolean {
    return try { update(); true } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (_: Exception) { onError(); false }
}

/** Official protocol and Material components, plus small Operit catalog extensions. */
internal val operitA2uiCatalog by lazy {
    A2uiCatalog(
        catalogId = A2uiProtocol.CATALOG,
        components = run {
            listOf(CardIcon, CardHtml, FramedCard, CardDivider, CardButton, CardPressable, CardVector, CardPopover, CardTooltip, CardStepper, CardFormula, CardFlow) +
                listOf("Text", "Row", "Column", "List", "Panel", "Scroll", "Frame").map(::CardLayoutComponent) +
                listOf(
                    "Heading", "Header", "InfoRow", "Display", "Badge", "Progress", "Code", "Table", "Switch", "RadioGroup",
                    "Select", "ChoicePicker", "DatePicker", "TextArea", "TextField", "Slider",
                    "Grid", "Form", "Expandable", "CheckBox",
                ).map(::OperitCardComponent) + cardAdvancedNames.map(::AdvancedCardComponent)
        },
        functions = CardFunctions.functions,
        isInline = true,
    )
}

private class OperitCardComponent(override val name: String) : A2uiComponent {
    private val text = A2uiProperty.dynamicString("text")
    private val label = A2uiProperty.dynamicString("label")
    private val value = A2uiProperty.dynamicString("value")
    private val checked = A2uiProperty.dynamicBoolean("checked")
    private val number = A2uiProperty.dynamicNumber("number")
    private val options = A2uiProperty.dynamicStringList("options")
    private val rows = A2uiProperty.dynamicValue("rows")
    private val columns = A2uiProperty.dynamicStringList("columns")
    private val children = A2uiProperty.childList("children")
    private val minimum = A2uiProperty.dynamicNumber("min")
    private val maximum = A2uiProperty.dynamicNumber("max")
    private val stepCount = A2uiProperty.dynamicNumber("steps")
    private val columnCount = A2uiProperty.dynamicNumber("columnCount")
    private val columnSpans = A2uiProperty.dynamicValue("columnSpans")
    private val minItemWidth = A2uiProperty.dynamicNumber("minItemWidth")
    private val expandedProperty = A2uiProperty.dynamicBoolean("expanded")
    private val inputType = A2uiProperty.dynamicString("inputType")
    private val tone = A2uiProperty.dynamicString("tone")
    private val status = A2uiProperty.dynamicString("status")
    private val align = A2uiProperty.dynamicString("align")
    private val disabled = A2uiProperty.dynamicBoolean("disabled")
    private val checkboxValue = A2uiProperty.dynamicBoolean("value")
    private val error = A2uiProperty.dynamicString("error")
    private val mask = A2uiProperty.dynamicString("mask")
    private val minLines = A2uiProperty.dynamicNumber("minLines")
    private val maxLines = A2uiProperty.dynamicNumber("maxLines")
    private val minDate = A2uiProperty.dynamicString("minDate")
    private val maxDate = A2uiProperty.dynamicString("maxDate")
    private val clearable = A2uiProperty.dynamicBoolean("clearable")
    private val widths = A2uiProperty.dynamicValue("columnWidths")
    private val tableHeight = A2uiProperty.dynamicNumber("maxHeight")
    override val description = "Operit native $name. Local field changes do not send model requests."
    // onChange contains host-side event_value placeholders, not a DynamicValue binding.
    // A2uiProtocol validates it and resolveLocalCardContext reads it after real input.
    override val properties = listOf(text, label, if (name == "CheckBox") checkboxValue else value, checked, number, options, rows, columns, children, minimum, maximum, stepCount, columnCount, columnSpans, minItemWidth, expandedProperty, inputType, tone, status, align, disabled, error, mask, minLines, maxLines, minDate, maxDate, clearable, widths, tableHeight, A2uiProperty.any("onChange"))

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val title = properties.bind(label).orEmpty()
        val interactive = LocalCardInteractive.current && properties.bind(disabled) != true
        val change = cardChangeHandler(properties, when (name) {
            "Switch" -> "checked"; "Slider" -> "number"; "Expandable" -> "expanded"; else -> "value"
        })
        when (name) {
            "Heading" -> Text(properties.bind(text).orEmpty(), modifier,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            "Header" -> CardHeader(properties.bind(text).orEmpty(), title,
                properties.bind(status).orEmpty(), properties.bind(tone), modifier)
            "InfoRow" -> CardInfoRow(title, properties.bind(text).orEmpty(), properties.bind(tone), modifier)
            "Display" -> {
                val rowFill = LocalCardRowFill.current
                val slot = remember { Any() }
                if (rowFill != null) DisposableEffect(rowFill) { onDispose { rowFill.remove(slot) } }
                Surface(
                    modifier.fillMaxWidth().cardRowStretch(rowFill),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                val atStart = properties.bind(align) != "end"
                // The tile may be stretched to its row; the content keeps its own height and
                // reports it, so the row can also shrink again.
                Column(
                    Modifier.wrapContentHeight(Alignment.Top).cardRowReport(rowFill, slot).padding(16.dp),
                    horizontalAlignment = if (atStart) Alignment.Start else Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (title.isNotBlank()) Text(title, Modifier.fillMaxWidth(), textAlign = if (atStart) TextAlign.Start else TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    CardDisplayValue(properties.bind(text).orEmpty(), atStart)
                }
                }
            }
            "Badge" -> CardStatusBadge(properties.bind(text).orEmpty(), properties.bind(tone), modifier)
            "Code" -> Text(
                properties.bind(text).orEmpty(), modifier.horizontalScroll(rememberScrollState()).padding(8.dp),
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
            )
            "Progress" -> {
                val progress = (properties.bind(number)?.toFloat() ?: 0f).coerceIn(0f, 1f)
                Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    }
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }
            "TextField", "TextArea" -> {
                val current = properties.bind(value).orEmpty()
                val update = properties.bindUpdater(value)
                val fieldError = properties.bind(error).orEmpty()
                val fieldMask = properties.bind(mask)
                if (LocalCardReadOnly.current) Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text((if (fieldMask == "password") "•".repeat(current.length.coerceAtMost(32)) else current)
                        .ifBlank { stringResource(R.string.a2ui_empty_value) }, style = MaterialTheme.typography.bodyMedium)
                    if (fieldError.isNotBlank()) Text(fieldError, color = MaterialTheme.colorScheme.error)
                } else OutlinedTextField(
                    value = current, onValueChange = { next ->
                        if (interactive && update != null && next.length <= 8192 &&
                            (fieldMask != "digits" || next.all { it in '0'..'9' })) change(current, next, -1) { update(next) }
                    },
                    readOnly = update == null || !interactive, enabled = interactive, label = { Text(title) },
                    modifier = modifier.fillMaxWidth(), singleLine = name == "TextField",
                    keyboardOptions = KeyboardOptions(keyboardType = when {
                        fieldMask == "password" -> KeyboardType.Password
                        fieldMask == "digits" -> KeyboardType.Number
                        properties.bind(inputType) == "number" -> KeyboardType.Decimal
                        else -> KeyboardType.Text
                    }),
                    visualTransformation = if (fieldMask == "password") PasswordVisualTransformation() else VisualTransformation.None,
                    isError = fieldError.isNotBlank(),
                    supportingText = if (fieldError.isNotBlank()) ({ Text(fieldError) }) else null,
                    minLines = if (name == "TextArea") properties.bind(minLines)?.toInt() ?: 3 else 1,
                    maxLines = if (name == "TextArea") properties.bind(maxLines)?.toInt() ?: 8 else 1,
                )
            }
            "Switch" -> {
                val current = properties.bind(checked) ?: false
                val update = properties.bindUpdater(checked)
                Row(modifier.fillMaxWidth().toggleable(
                    current, enabled = update != null && interactive, role = Role.Switch,
                    onValueChange = { next -> if (interactive && update != null) change(current, next, -1) { update(next) } },
                ), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f))
                    Switch(current, onCheckedChange = null, enabled = update != null && interactive)
                }
            }
            "RadioGroup", "Select", "ChoicePicker" -> {
                val current = properties.bind(value).orEmpty()
                val update = properties.bindUpdater(value)
                val choices = properties.bind(options).orEmpty().take(32)
                var expanded by remember { mutableStateOf(false) }
                Column(modifier) {
                    if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.labelLarge)
                    if (LocalCardReadOnly.current) Text(current.ifBlank { stringResource(R.string.a2ui_empty_value) })
                    else if (name == "Select") OutlinedButton(onClick = { expanded = !expanded }, enabled = update != null && interactive, modifier = Modifier.fillMaxWidth()) {
                        Text(current.ifBlank { stringResource(R.string.a2ui_select) }, Modifier.weight(1f))
                        Icon(Icons.Outlined.ExpandMore, contentDescription = null)
                    }
                    if (!LocalCardReadOnly.current && (name != "Select" || expanded)) choices.forEach { choice ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                                selected = current == choice, enabled = update != null && interactive, role = Role.RadioButton,
                                onClick = { if (interactive && update != null) change(current, choice, choices.indexOf(choice)) { update(choice) }; expanded = false },
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            RadioButton(current == choice, onClick = null, enabled = update != null && interactive)
                            Text(choice, Modifier.weight(1f).padding(vertical = 8.dp))
                        }
                    }
                }
            }
            "Slider" -> {
                val min = properties.bind(minimum)?.toFloat() ?: 0f
                val max = properties.bind(maximum)?.toFloat() ?: 100f
                val current = properties.bind(number)?.toFloat() ?: min
                val update = properties.bindUpdater(number)
                Column(modifier) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        Text(cardNumberLabel(current), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    if (current.isFinite() && min.isFinite() && max.isFinite() && max > min) {
                        Slider(current.coerceIn(min, max), { next -> if (interactive && update != null) change(current, next, -1) { update(next) } }, enabled = update != null && interactive, valueRange = min..max,
                            steps = (properties.bind(stepCount)?.toInt() ?: 0).coerceIn(0, 999))
                    } else Text(stringResource(R.string.a2ui_bad_number), color = MaterialTheme.colorScheme.error)
                }
            }
            "DatePicker" -> {
                val current = properties.bind(value).orEmpty()
                val update = properties.bindUpdater(value)
                val lower = properties.bind(minDate).orEmpty()
                val upper = properties.bind(maxDate).orEmpty()
                val bounds = remember(lower, upper) { cardDateBounds(lower, upper) }
                var expanded by remember { mutableStateOf(false) }
                Column(modifier) {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                    if (bounds == null) {
                        Text(stringResource(R.string.a2ui_invalid), color = MaterialTheme.colorScheme.error)
                        return@Column
                    }
                    val selectedDate = remember(current, bounds) {
                        runCatching { LocalDate.parse(current) }.getOrNull()?.takeIf { it in bounds }
                    }
                    if (current.isNotEmpty() && selectedDate == null)
                        Text(stringResource(R.string.a2ui_date_outside_range), color = MaterialTheme.colorScheme.error)
                    if (LocalCardReadOnly.current) Text(current.ifBlank { stringResource(R.string.a2ui_empty_value) })
                    else OutlinedButton(onClick = { expanded = !expanded }, enabled = update != null && interactive, modifier = Modifier.fillMaxWidth()) {
                        Text(current.ifBlank { stringResource(R.string.a2ui_select_date) }, Modifier.weight(1f))
                        Icon(Icons.Outlined.ExpandMore, contentDescription = null)
                    }
                    if (!LocalCardReadOnly.current && properties.bind(clearable) == true && current.isNotEmpty())
                        TextButton(onClick = {
                            if (interactive && update != null) change(current, "", -1) { update("") }
                        }, enabled = interactive && update != null) { Text(stringResource(R.string.a2ui_clear)) }
                    if (expanded && interactive) {
                        val initial = selectedDate?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
                        // Recreate only when the bound date changes. Programmatic updates
                        // update the highlight but never masquerade as a user's selection.
                        key(current, lower, upper) {
                        val selectable = remember(bounds) { object : SelectableDates {
                            override fun isSelectableDate(utcTimeMillis: Long) =
                                java.time.Instant.ofEpochMilli(utcTimeMillis).atOffset(ZoneOffset.UTC).toLocalDate() in bounds
                            override fun isSelectableYear(year: Int) = year in bounds.start.year..bounds.endInclusive.year
                        } }
                        val state = rememberDatePickerState(initialSelectedDateMillis = initial, selectableDates = selectable)
                        Box(Modifier.horizontalScroll(rememberScrollState())) {
                            DatePicker(state, modifier = Modifier.width(328.dp), showModeToggle = false)
                        }
                        LaunchedEffect(state.selectedDateMillis) {
                            state.selectedDateMillis?.let {
                                val next = java.time.Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate().toString()
                                if (interactive && update != null && it != initial && LocalDate.parse(next) in bounds) change(current, next, -1) { update(next) }
                            }
                        }
                        }
                    }
                }
            }
            "CheckBox" -> {
                val current = properties.bind(checkboxValue) ?: false
                val update = properties.bindUpdater(checkboxValue)
                val enabled = interactive && update != null
                Row(modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(current, enabled = enabled, role = Role.Checkbox,
                    onValueChange = { next -> if (enabled) change(current, next, -1) { update?.invoke(next) } }),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Checkbox(current, onCheckedChange = null, enabled = enabled)
                    Text(title, Modifier.weight(1f).padding(vertical = 8.dp))
                }
            }
            "Table" -> {
                val headers = properties.bind(columns).orEmpty()
                val data = properties.bind(rows) as? List<*>
                if (data == null || data.size > 32 || data.any { it !is List<*> || it.size != headers.size ||
                        it.any { cell -> cell is List<*> || cell is Map<*, *> } }) {
                    Text(stringResource(R.string.a2ui_bad_table), color = MaterialTheme.colorScheme.error)
                    return
                }
                val columnWidths = (properties.bind(widths) as? List<*>)?.map { (it as Number).toInt() }
                    ?: List(headers.size) { 140 }
                val height = properties.bind(tableHeight)?.toInt()
                Column(modifier.horizontalScroll(rememberScrollState())) {
                    Row { headers.forEachIndexed { i, heading -> Text(heading, Modifier.width(columnWidths[i].dp).padding(8.dp), style = MaterialTheme.typography.labelLarge) } }
                    HorizontalDivider()
                    Column(if (height != null) Modifier.heightIn(max = height.dp).verticalScroll(rememberScrollState()) else Modifier) {
                        data.take(32).forEach { row ->
                            Row { (row as? List<*>).orEmpty().take(8).forEachIndexed { i, cell -> Text(cell?.toString().orEmpty(), Modifier.width(columnWidths[i].dp).padding(8.dp)) } }
                        }
                    }
                }
            }
            else -> {
                val refs = properties.bindChildReferences(children).orEmpty()
                val boundExpanded = properties.bind(expandedProperty)
                val updateExpanded = properties.bindUpdater(expandedProperty)
                val readOnly = LocalCardReadOnly.current
                var localExpanded by rememberSaveable(readOnly, boundExpanded) { mutableStateOf(boundExpanded ?: false) }
                val expanded = if (readOnly) localExpanded else boundExpanded ?: localExpanded
                val child: @Composable (Int, Boolean, CardRowFill?, Boolean) -> Unit = { index, gridCell, rowFill, compact ->
                    val ref = refs[index]
                    key(ref.id, ref.baseDataPath) {
                        when (val state = observeA2uiComponentState(ref)) {
                            is A2uiComponentState.Success ->
                                CompositionLocalProvider(
                                    LocalCardRowItem provides false,
                                    LocalCardGridCell provides (gridCell && state.component.type == "Button"),
                                    LocalCardRowFill provides rowFill?.takeIf { state.component.type in setOf("Display", "Panel") },
                                    LocalCardCompactHeadings provides (compact || LocalCardCompactHeadings.current),
                                ) {
                                    A2uiComponent(state.component)
                                }
                            is A2uiComponentState.Error -> Text("⚠", color = MaterialTheme.colorScheme.error)
                            A2uiComponentState.Loading -> CircularProgressIndicator(Modifier.size(16.dp))
                        }
                    }
                }
                Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (name == "Expandable") TextButton(onClick = {
                        if (updateExpanded != null && !readOnly) change(expanded, !expanded, -1) { updateExpanded(!expanded) } else localExpanded = !expanded
                    }, enabled = LocalCardBrowse.current && properties.bind(disabled) != true) { Text(title) }
                    else if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleMedium)
                    if (name != "Expandable" || expanded) {
                        if (name == "Grid") BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val explicitCount = properties.bind(columnCount)?.toInt()?.coerceIn(1, 4)
                            val count = explicitCount ?: cardAutoColumns(maxWidth.value,
                                properties.bind(minItemWidth)?.toFloat() ?: 160f,
                                androidx.compose.ui.platform.LocalDensity.current.fontScale)
                            val spans = (properties.bind(columnSpans) as? List<*>)?.map { (it as Number).toInt() }
                                ?: List(refs.size) { 1 }
                            val gridRows = cardGridRows(count, spans)
                            val width = if (explicitCount != null) maxOf(maxWidth, (count * 56 + (count - 1) * 8).dp) else maxWidth
                            Box(Modifier.horizontalScroll(rememberScrollState())) {
                            Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val rowFills = remember(gridRows.size) { List(gridRows.size) { CardRowFill() } }
                                gridRows.forEachIndexed { rowIndex, cells ->
                                    val rowFill = rowFills[rowIndex].takeIf { cells.size > 1 }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val cellWidth = (width - 8.dp * (count - 1)) / count
                                        cells.forEach { cell ->
                                            Box(Modifier.width(cellWidth * cell.span + 8.dp * (cell.span - 1))) {
                                                // Narrow cells only: a wide tablet column or a full-width span keeps its sizes.
                                                child(cell.index, true, rowFill,
                                                    cells.size > 1 && cellWidth * cell.span < 240.dp)
                                            }
                                        }
                                    }
                                }
                            }
                            }
                        } else refs.indices.forEach { child(it, false, null, false) }
                    }
                }
            }
        }
    }
}

/** Preserve Material button behavior with Operit events and incremental replay controls. */
private val LocalCardButtonLoading = compositionLocalOf { false }
private object CardButton : A2uiComponent {
    override val name = "Button"
    override val description = "Button with checks and local busy state"
    private val loading = A2uiProperty.dynamicBoolean("loading")
    override val properties = BaseCardButton.properties.map {
        if (it.key == "action") cardEventActionProperty else it
    } + loading
    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        CompositionLocalProvider(LocalCardButtonLoading provides (properties.bind(loading) == true)) {
            with(BaseCardButton) { Content(properties, modifier) }
        }
    }
}
private object BaseCardButton : A2uiBasicCatalogV1.Button {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        childId: String, variant: A2uiBasicCatalogV1.Button.Variant,
        action: Map<String, Any?>, accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        checks: List<A2uiBasicCatalogV1.CheckRule>, modifier: Modifier,
    ) {
        val child = observeA2uiComponentState(childId)
        val event = action["event"] as? Map<*, *>
        if (LocalCardReadOnly.current && event?.get("name") == "local_action") return
        val loading = LocalCardButtonLoading.current
        val enabled = !loading && LocalCardBrowse.current && child is A2uiComponentState.Success && checks.all { it.condition }
        val content: @Composable RowScope.() -> Unit = {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            when (child) {
                is A2uiComponentState.Success -> A2uiComponent(child.component)
                else -> Text("…")
            }
        }
        Column(modifier) {
            val buttonModifier = if (LocalCardGridCell.current) Modifier.fillMaxWidth().heightIn(min = 48.dp) else Modifier
            val buttonPadding = if (LocalCardGridCell.current) PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                else if (variant == A2uiBasicCatalogV1.Button.Variant.Borderless) ButtonDefaults.TextButtonContentPadding
                else ButtonDefaults.ContentPadding
            when (variant) {
                A2uiBasicCatalogV1.Button.Variant.Primary -> Button({ dispatchAction(action) }, modifier = buttonModifier, contentPadding = buttonPadding, enabled = enabled, content = content)
                A2uiBasicCatalogV1.Button.Variant.Secondary -> OutlinedButton({ dispatchAction(action) }, modifier = buttonModifier, contentPadding = buttonPadding, enabled = enabled, content = content)
                A2uiBasicCatalogV1.Button.Variant.Borderless -> TextButton({ dispatchAction(action) }, modifier = buttonModifier, contentPadding = buttonPadding, enabled = enabled, content = content)
            }
            checks.firstOrNull { !it.condition }?.let { Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** Decorative mistakes must not remove unrelated form fields or disable their actions. */
private object CardIcon : A2uiComponent {
    override val name = "Icon"
    override val description = "A native outlined icon from the shared Operit icon catalog."
    private val iconName = A2uiProperty.dynamicString("name")
    private val tone = A2uiProperty.dynamicString("tone")
    private val size = A2uiProperty.dynamicNumber("size")
    override val properties = listOf(iconName, A2uiBasicCatalogV1.Icon.AccessibilityProperty,
        A2uiBasicCatalogV1.WeightProperty, tone, size)
    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val name = properties.bind(iconName)
        val accessibility = properties.bind(A2uiBasicCatalogV1.Icon.AccessibilityProperty)
        val vector = CardIcons.get(name)
        val iconSize = (properties.bind(size)?.toInt() ?: 24).coerceIn(12, 96).dp
        val boundTone = properties.bind(tone)
        val tint = cardToneInk(if (boundTone == "accent") "primary" else boundTone,
            MaterialTheme.colorScheme, LocalA2uiBackdrop.current)
            ?: LocalContentColor.current
        if (vector == null) {
            val warn = LocalCardIconWarning.current
            LaunchedEffect(name) { warn() }
            Icon(Icons.Outlined.HelpOutline, stringResource(R.string.a2ui_icon_missing), modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val description = accessibility?.let {
                listOfNotNull(it.label?.takeIf(String::isNotBlank), it.description?.takeIf(String::isNotBlank))
                    .joinToString(", ").takeIf(String::isNotBlank)
            }
            Icon(vector, description, modifier.size(iconSize), tint = tint)
        }
    }
}
