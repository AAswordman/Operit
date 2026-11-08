package com.ai.assistance.operit.ui.common.a2ui

import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.a2ui.compose.runtime.*
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import java.net.URI
import kotlinx.coroutines.flow.drop

internal val cardAdvancedNames = listOf(
    "Segmented", "Tabs", "Carousel", "Conditional", "Chart", "RichText",
    "Image", "Audio", "Video", "Link", "File", "Product", "Place", "Map", "Draft",
)

internal class AdvancedCardComponent(override val name: String) : A2uiComponent {
    private val label = A2uiProperty.dynamicString("label")
    private val text = A2uiProperty.dynamicString("text")
    private val value = A2uiProperty.dynamicString("value")
    private val url = A2uiProperty.dynamicString("url")
    private val imageUrl = A2uiProperty.dynamicString("imageUrl")
    private val price = A2uiProperty.dynamicString("price")
    private val address = A2uiProperty.dynamicString("address")
    private val type = A2uiProperty.dynamicString("type")
    private val labels = A2uiProperty.dynamicStringList("labels")
    private val options = A2uiProperty.dynamicStringList("options")
    private val children = A2uiProperty.childList("children")
    private val visible = A2uiProperty.dynamicBoolean("visible")
    private val series = A2uiProperty.dynamicValue("series")
    private val runs = A2uiProperty.dynamicValue("runs")
    private val markers = A2uiProperty.dynamicValue("markers")
    private val route = A2uiProperty.dynamicBoolean("route")
    private val disabled = A2uiProperty.dynamicBoolean("disabled")
    override val description = "Operit native $name. Data-bound, with explicit user actions only."
    override val properties = listOf(label, text, value, url, imageUrl, price, address, type, labels, options, children, visible, series, runs, markers, route, disabled, A2uiProperty.any("onChange"))

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val title = properties.bind(label).orEmpty()
        val interactive = LocalCardInteractive.current && properties.bind(disabled) != true
        val readOnly = LocalCardReadOnly.current
        val browse = LocalCardBrowse.current && properties.bind(disabled) != true
        val change = cardChangeHandler(properties, "value")
        val mutationGuard = LocalCardMutationGuard.current
        val link = properties.bind(url).orEmpty()
        fun open(link: String) {
            if (A2uiProtocol.safeUrl(link)) dispatchAction(mapOf("event" to mapOf("name" to "open_url", "context" to mapOf("url" to link))))
        }
        when (name) {
            "Image", "Video", "Audio" -> CardMedia(name, link, title, modifier)
            "RichText" -> CardRichText(properties.bind(runs) as? List<*> ?: emptyList<Any>(), modifier) { if (browse) open(it) }
            "Chart" -> {
                val raw = properties.bind(series)
                val chartType = properties.bind(type) ?: "line"
                val chartLabels = properties.bind(labels).orEmpty()
                val data = remember(raw, chartType, chartLabels) { runCatching { cardChartSeries(raw, chartLabels, chartType) } }
                if (data.isSuccess) CardChart(chartType, title, data.getOrThrow(), modifier)
                else Text(stringResource(R.string.a2ui_bad_chart), modifier, color = MaterialTheme.colorScheme.error)
            }
            "Map" -> CardMap(title, properties.bind(markers), properties.bind(route) == true, modifier) { if (browse) open(it) }
            "Draft" -> {
                val current = properties.bind(value).orEmpty()
                val update = properties.bindUpdater(value)
                Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (readOnly) Text(current, style = MaterialTheme.typography.bodyMedium)
                    else OutlinedTextField(current, { next -> if (interactive && update != null && next.length <= 8192) mutationGuard { update(next) } },
                        modifier = Modifier.fillMaxWidth(), enabled = interactive, readOnly = update == null, minLines = 4, maxLines = 12)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!readOnly) OutlinedButton(onClick = { dispatchAction(mapOf("event" to mapOf("name" to "local_action", "context" to mapOf("op" to "save")))) }, enabled = interactive) {
                            Text(stringResource(R.string.a2ui_save_draft))
                        }
                        TextButton(onClick = { dispatchAction(mapOf("event" to mapOf("name" to "copy", "context" to mapOf("text" to current)))) }, enabled = browse) {
                            Text(stringResource(R.string.a2ui_copy))
                        }
                    }
                }
            }
            "Segmented" -> {
                val current = properties.bind(value).orEmpty()
                val update = properties.bindUpdater(value)
                val choices = properties.bind(options).orEmpty().take(32)
                Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        choices.forEach { choice ->
                            FilterChip(selected = current == choice, onClick = { if (interactive && update != null) change(current, choice, choices.indexOf(choice)) { update(choice) } },
                                label = { Text(choice) }, enabled = update != null && interactive)
                        }
                    }
                }
            }
            "Tabs", "Carousel", "Conditional" -> {
                val refs = properties.bindChildReferences(children).orEmpty()
                val titles = properties.bind(labels).orEmpty()
                val bound = properties.bind(value)
                var local by rememberSaveable(readOnly, bound) { mutableStateOf(bound.orEmpty()) }
                val update = properties.bindUpdater(value)
                val current = if (readOnly) local else bound ?: local
                val selected = refs.indexOfFirst { it.id == current }.coerceAtLeast(0)
                val canNavigate = browse && (readOnly || bound == null || update != null)
                fun select(index: Int) { refs.getOrNull(index)?.let { ref ->
                    if (update != null && !readOnly) change(current, ref.id, index) { update(ref.id) } else local = ref.id
                } }
                val latestSelect by rememberUpdatedState<(Int) -> Unit> { select(it) }
                val renderChild: @Composable (Int) -> Unit = { index ->
                    refs.getOrNull(index)?.let { ref ->
                        key(ref.id, ref.baseDataPath) {
                            when (val child = observeA2uiComponentState(ref)) {
                                is A2uiComponentState.Success -> CompositionLocalProvider(LocalCardRowItem provides false) {
                                    A2uiComponent(child.component, modifier = Modifier.fillMaxWidth())
                                }
                                is A2uiComponentState.Error -> Text(stringResource(R.string.a2ui_partial_error), color = MaterialTheme.colorScheme.error)
                                A2uiComponentState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                val show = name != "Conditional" || properties.bind(visible) == true
                if (show) Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleMedium)
                    if (name == "Tabs") Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        refs.forEachIndexed { i, _ ->
                            FilterChip(selected == i, onClick = { select(i) }, label = { Text(titles.getOrNull(i) ?: "${i + 1}") }, enabled = canNavigate)
                        }
                    }
                    if (name == "Carousel" && refs.isNotEmpty()) {
                        val pager = rememberPagerState(initialPage = selected, pageCount = { refs.size })
                        LaunchedEffect(current, refs.map { it.id }) {
                            if (!pager.isScrollInProgress && pager.currentPage != selected) pager.scrollToPage(selected)
                        }
                        LaunchedEffect(pager, canNavigate) {
                            snapshotFlow { pager.settledPage }.drop(1).collect { page ->
                                if (canNavigate) latestSelect(page)
                            }
                        }
                        HorizontalPager(pager, modifier = Modifier.fillMaxWidth(), pageSpacing = 12.dp,
                            userScrollEnabled = canNavigate, key = { refs[it].id }) { page -> renderChild(page) }
                    } else if (name == "Conditional") refs.indices.forEach { renderChild(it) }
                    else renderChild(selected)
                    if (name == "Carousel" && refs.isNotEmpty()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { select(selected - 1) }, enabled = canNavigate && selected > 0) { Text(stringResource(R.string.a2ui_previous)) }
                        Text("${selected + 1} / ${refs.size}", style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { select(selected + 1) }, enabled = canNavigate && selected < refs.lastIndex) { Text(stringResource(R.string.a2ui_next)) }
                    }
                }
            }
            else -> {
                val description = properties.bind(text).orEmpty()
                val image = properties.bind(imageUrl).orEmpty()
                val amount = properties.bind(price).orEmpty()
                val location = properties.bind(address).orEmpty()
                Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (image.isNotBlank()) CardMedia("Image", image, title)
                    if (amount.isNotBlank()) Text(amount, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    if (location.isNotBlank()) Text(location, style = MaterialTheme.typography.bodyMedium)
                    if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium)
                    if (A2uiProtocol.safeUrl(link)) {
                        Text(remember(link) { URI(link).host }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { open(link) }, enabled = browse) {
                            Text(stringResource(if (name == "File") R.string.a2ui_open_file else R.string.a2ui_open_link))
                        }
                    } else if (link.isNotBlank()) Text(stringResource(R.string.a2ui_bad_url), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
