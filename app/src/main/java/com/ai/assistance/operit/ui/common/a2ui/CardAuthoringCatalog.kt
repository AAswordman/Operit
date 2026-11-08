package com.ai.assistance.operit.ui.common.a2ui

/** Public authoring contract; shared by the compiler and the on-demand model guide.
 * Named arguments avoid depending on the ordering of AndroidX property lists.
 */
internal object CardAuthoringCatalog {
    data class Field(val name: String, val type: String, val required: Boolean)
    private fun fields(value: String) = value.split(' ').filter(String::isNotBlank).map {
        val (key, type) = it.split(':')
        Field(key.removeSuffix("?"), type, !key.endsWith("?"))
    }
    val changeComponents = setOf("CheckBox", "TextField", "TextArea", "Switch", "RadioGroup",
        "Select", "ChoicePicker", "Slider", "DatePicker", "Segmented", "Tabs", "Carousel", "Expandable", "Stepper")
    val components = linkedMapOf(
        "Text" to "text:string variant?:string textAlign?:string lineClamp?:number tone?:string",
        "Icon" to "name:string tone?:string size?:number",
        "Html" to "label:string html:string css?:string maxHeight?:number",
        "Row" to "children:children gap?:string align?:string justify?:string wrap?:boolean weights?:array minItemWidth?:number",
        "Column" to "children:children gap?:string align?:string",
        "Panel" to "child:child padding?:string radius?:string background?:string border?:boolean maxWidth?:number",
        "Scroll" to "child:child maxHeight:number",
        "Frame" to "child:child minWidth?:number maxWidth?:number minHeight?:number maxHeight?:number aspectRatio?:number",
        "Popover" to "label:string child:child",
        "Tooltip" to "label:string text:string",
        "Stepper" to "label:string labels:array current:number",
        "Formula" to "label:string text:string",
        "Flow" to "label:string steps:array direction?:string",
        "Vector" to "label:string width:number height:number shapes:array",
        "List" to "children:children", "Card" to "child:child", "Divider" to "",
        "Repeat" to "items:array key:string child:child maxItems?:number",
        "Button" to "child:child action:action variant?:string checks?:array loading?:boolean",
        "Pressable" to "child:child label:string action:action disabled?:boolean selected?:boolean",
        "CheckBox" to "label:string value:boolean",
        "Heading" to "text:string", "Display" to "text:string label?:string align?:string",
        "Header" to "text:string label?:string status?:string tone?:string",
        "InfoRow" to "label:string text:string tone?:string",
        "Badge" to "text:string tone?:string", "Code" to "text:string",
        "Progress" to "number:number label?:string",
        "Table" to "columns:array rows:array columnWidths?:array maxHeight?:number",
        "Grid" to "children:children columnCount?:number columnSpans?:array minItemWidth?:number",
        "Form" to "label:string children:children",
        "Expandable" to "label:string children:children expanded:boolean",
        "TextField" to "label:string value:string inputType?:string error?:string mask?:string",
        "TextArea" to "label:string value:string inputType?:string error?:string minLines?:number maxLines?:number",
        "Switch" to "label:string checked:boolean",
        "RadioGroup" to "label:string options:array value:string",
        "Select" to "label:string options:array value:string",
        "ChoicePicker" to "label:string options:array value:string",
        "Slider" to "label:string min:number max:number number:number steps?:number",
        "DatePicker" to "label:string value:string clearable?:boolean minDate?:string maxDate?:string",
        "Segmented" to "label:string options:array value:string",
        "Tabs" to "label:string labels:array children:children value:string",
        "Carousel" to "label:string children:children value:string",
        "Conditional" to "visible:boolean children:children",
        "RichText" to "runs:array",
        "Chart" to "label:string type:string series:array labels?:array",
        "Image" to "label:string url:string", "Audio" to "label:string url:string",
        "Video" to "label:string url:string",
        "Link" to "label:string text:string url:string", "File" to "label:string text:string url:string",
        "Product" to "label:string text:string price:string url:string imageUrl?:string",
        "Place" to "label:string text:string address:string url:string imageUrl?:string",
        "Map" to "label:string markers:array route:boolean",
        "Draft" to "label:string value:string",
    ).mapValues { (name, value) -> fields(value) + if (name in changeComponents)
        fields("onChange?:action disabled?:boolean") else emptyList() }

    val staticFields = setOf("variant", "min", "max", "steps", "columnCount", "columnSpans", "minItemWidth",
        "type", "align", "gap", "justify", "wrap", "padding", "radius", "background", "border", "maxWidth", "direction",
        "maxHeight", "minWidth", "minHeight", "aspectRatio", "textAlign", "lineClamp", "width", "height", "key", "maxItems",
        "columnWidths", "minLines", "maxLines", "mask", "weights", "size")
    val enums = mapOf(
        "Text.variant" to setOf("h1", "h2", "h3", "h4", "h5", "body", "caption"),
        "Button.variant" to setOf("primary", "default", "borderless"),
        "Chart.type" to setOf("line", "bar", "pie", "scatter"),
        "TextField.inputType" to setOf("text", "number"),
        "TextArea.inputType" to setOf("text", "number"),
        "TextField.mask" to setOf("none", "password", "digits"),
        "Display.align" to setOf("start", "end"),
        "Flow.direction" to setOf("auto", "horizontal", "vertical"),
        "Header.tone" to setOf("neutral", "success", "info", "warning", "error"),
        "InfoRow.tone" to setOf("neutral", "success", "info", "warning", "error"),
        "Badge.tone" to setOf("neutral", "success", "info", "warning", "error"),
        "Text.tone" to setOf("neutral", "success", "info", "warning", "error"),
        "Icon.tone" to setOf("neutral", "accent", "success", "info", "warning", "error"),
        "Text.textAlign" to setOf("start", "center", "end"),
        "Row.gap" to setOf("none", "xs", "sm", "md", "lg"),
        "Column.gap" to setOf("none", "xs", "sm", "md", "lg"),
        "Row.align" to setOf("start", "center", "end"),
        "Column.align" to setOf("start", "center", "end"),
        "Row.justify" to setOf("start", "center", "end", "between"),
        "Panel.padding" to setOf("none", "xs", "sm", "md", "lg"),
        "Panel.radius" to setOf("none", "sm", "md", "lg"),
        "Panel.background" to setOf("transparent", "surface", "inset", "accentMuted",
            "light", "dark", "blue", "green", "orange", "red", "info", "success", "warning", "error"),
    )
    val ranges = mapOf(
        "Html.maxHeight" to 120..900,
        "Icon.size" to 12..96,
        "Text.lineClamp" to 1..8, "Panel.maxWidth" to 120..960, "Scroll.maxHeight" to 80..480,
        "Grid.minItemWidth" to 80..320, "Row.minItemWidth" to 80..320,
        "Vector.width" to 1..1000, "Vector.height" to 1..1000,
        "Repeat.maxItems" to 1..32,
        "Table.maxHeight" to 80..480, "TextArea.minLines" to 1..12, "TextArea.maxLines" to 1..12,
        "Frame.minWidth" to 0..960, "Frame.maxWidth" to 1..960,
        "Frame.minHeight" to 0..480, "Frame.maxHeight" to 1..480,
    )
    // Flow.steps is content data (may come from state or a Repeat item); Slider.steps is a literal count.
    fun isStatic(component: String, field: String) =
        field in staticFields && !(component == "Flow" && field == "steps") || component == "Table" && field == "columns" ||
            component == "Html" && field in setOf("html", "css")

    // These are the actual snippets sent to models; renderer contract tests compile
    // them through the same protocol and schema checks as a2ui_prepare.
    val authoringExamples = linkedMapOf(
        "dynamic_table_cells" to """
            $/minutes = 40
            root = Table(columns=["Topic","Minutes"],rows=[["GPU",formatNumber(value=$/minutes,decimals=0)]])
        """.trimIndent(),
        "structured_event_context" to """
            $/plan = {title:"GPU",done:true}
            root = Button(child=Text(text="Reset"),action=Event(name="local_action",context={op:"set",path:"/plan",value:{title:"GPU",done:false}}))
        """.trimIndent(),
    )

    fun guide(): String = buildString {
        appendLine("a2ui_prepare compiles and queues a card. Operit automatically displays queued cards when this turn completes. You may optionally output the returned reference in an a2ui-ref fence to position it earlier. Do not rewrite JSON, print a URL, or copy historical card summaries as a display instruction. References belong only to the current turn.")
        appendLine("For a form that must be answered inside this turn, call a2ui_prepare(source=...,wait_timeout_seconds=180). Range: integer 0..600; default 0 preserves ordinary queued delivery. A positive timeout displays the card immediately, then suspends the tool until the user confirms an ai_submit/tool_action, cancels, or the timeout expires. The tool returns status=submitted with structured data, status=timed_out, or status=cancelled. Do not output another message, poll, or display the reference again while waiting. Use native form inputs and Event actions; Html authored scripts cannot submit data. Live mode may be combined with waiting.")
        appendLine("Minimal valid source: root=Text(text=\"Hello\"). Put each declaration on its own line. Declare root; initialize every $/path before binding it. A named declaration must be reachable from root. This is a declarative language, not JavaScript.")
        appendLine("Chart(label=\"Sales\",type=\"bar\",labels=[\"A\",\"B\"],series=[{label:\"Revenue\",values:[12,18]}]). Series fields are label? and values, not name or data. Scatter uses {label:...,points:[{x:1,y:2,label:\"A\"}]}. Values must be finite numbers; labels and value counts must match. Maximum 4 series (pie:1), 128 points each. New Express rejects unknown series/point fields.")
        appendLine("TextField/TextArea value is an initialized string path, e.g. $/form={amount:\"12\"} then root=TextField(label=\"Amount\",value=$/form/amount,inputType=\"number\"). A direct path writes user input automatically; onChange is optional for extra local actions. inputType selects the keyboard only; mask=\"digits\" accepts ASCII digits. A computed value is read-only.")
        appendLine("Slider(label=\"Amount\",min=1000,max=3000,number=$/amount,steps=19) requires initialized numeric state. min/max are finite literal numbers within -1000000..1000000 with max>min; steps is a literal integer0..999. Ordinary cards are interactive; live=true only enables model updates during this turn and requires $/agent as an object.")
        appendLine("Use named arguments for every component and function. Inline child components or reference named declarations.")
        appendLine("Icon(name=\"lucide:orbit\",tone=\"accent\",size=32) uses Lucide 0.456.0. Use a2ui_icons(query=\"English keyword\") to discover names; do not guess or load the entire catalog. Legacy names retain their artwork; new bare names also work when unambiguous. Icon tone supports neutral/accent/success/info/warning/error and bindings, size is literal12..96dp (default24), constrained by its parent.")
        appendLine("Object entries use key:value; component/function arguments use key=value. A single unambiguous punctuation mistake may be repaired locally and reported as repaired=true; values, functions and validation limits are never rewritten.")
        appendLine("Local calculations allow at most 128 calls across the complete card document. There is no separate 6-level function nesting limit; overall source and JSON structure limits still apply.")
        appendLine("List lays out its static children vertically at natural height and scrolls with the chat. For a separately scrolling area, wrap it in Scroll(child=..., maxHeight=80..480).")
        appendLine("Layout values express intent; actual available width and font size take priority. Row wraps by default; wrap=false prefers a single line but stacks when it cannot fit. Optional literal weights has one integer0..12 per child, at least one positive: 0 keeps natural width, positive values share remaining width. Example weights=[0,1] for a logo and title/description Column, [1,1] for equal regions. Weighted rows fill available width and stack when a flexible region gets less than minItemWidth (80..320dp, default120, scaled with font size), even with wrap=false. Panel inside a Row hugs its content. A row's Vector uses viewport width as preferred dp width; standalone Vector expands. Wrap graphics in Frame for explicit bounds. Prefer auto Grid for feature tiles, not fixed heights for text.")
        appendLine("Repeat(items=arrayBindingOrCalculation,key=\"id\",child=template,maxItems=20) renders object items with stable unique nonempty string keys. Maximum 32 rows per list and 512 rendered nodes across all nested lists. Nested Repeat uses items=item(field=\"children\"); item/item_state refer to the nearest row. Use item_state(field=\"done\",default=false) for independent scalar row state. Defaults must agree; state follows Repeat ID and all ancestor keys across filters, reorder and restore, including hidden rows within the 32K quota. Absolute bindings remain shared. Empty arrays render no rows.")
        appendLine("Pressable(label=...,child=...,action=...,selected=...,disabled=...) makes display-only content clickable; nested interactive descendants are rejected. Completed nodes should remain clickable. Input onChange supports only local_action. event_value(field=\"value\"|\"previous\"|\"index\") is restricted to its context; index is -1 when not indexed. Initialization, restore and programmatic updates do not fire changes.")
        appendLine("Vector shape arrays, types and point structures are literal; coordinates, tone, stroke, filled and text can bind to state. arrow(x1,y1,x2,y2) draws a terminal arrowhead. Runtime invalid geometry displays a recoverable local error. row_set uses {op:\"row_set\",repeat:\"listId\",key:\"itemKey\",field:\"done\",value:true,parents:[\"outerKey\"]}; parents is optional for top-level lists and lists every ancestor key from outermost inward. Only declared fields of currently present rows can change.")
        appendLine("DatePicker minDate/maxDate are ISO dates in 1900..2100, clearable writes empty string. Bounds do not rewrite existing data. TextField mask=none|password|digits; password masks only display, digits accepts ASCII digits without reformatting. TextArea minLines/maxLines default3/8. error is a displayed string, not automatic validation. Button loading disables actions while preserving its label.")
        appendLine("Table columnWidths is one literal64..480dp width per column; maxHeight=80..480 bounds the scrolling body below a fixed header. RichText runs accept strike/highlight/tabular booleans and tone=primary|muted|success|info|warning|error. Tooltip opens on tap/long-press and closes like Popover. Stepper current is a bound zero-based integer; previous steps remain selectable, onChange stays local.")
        appendLine("Progress.number uses a ratio from 0 to 1. For 0..100 values, bind divide(a=$/agent/progress,b=100). Live cards must initialize $/agent as an object; a2ui_patch_data replaces that object, while user controls use other paths. For live updates before turn completion, display the initial a2ui-ref once before patches; do not repeat it after updates.")
        appendLine("Formula text supports a bounded TeX subset: 1024 chars, 64 commands, 12 brace levels; frac/sqrt/sum/prod/int/lim/infty/left/right, common Greek, arithmetic relations, sin/cos/tan/log/ln/exp/text/mathrm/mathbf/overline/vec/hat. No macros, environments, files or size commands; invalid dynamic formulas show a local error.")
        appendLine("arraySort uses same-type scalar values, optional field and direction asc/desc, stable order for ties. arrayFind returns the first equal item or explicit fallback. arrayUpdateByKey requires unique scalar keys, cannot patch the key, returns a new array and leaves unmatched data unchanged. None writes state directly.")
        appendLine("Frame applies bounded size constraints to one child; available screen constraints always win. aspectRatio is width/height from 0.25 to 4. Avoid fixed height for text/forms with large fonts. Vector also supports quadratic(x1,y1,cx,cy,x2,y2) and cubic(x1,y1,cx1,cy1,cx2,cy2,x2,y2) shape objects; all coordinates stay in the viewport.")
        components.forEach { (name, fields) ->
            appendLine("$name(${fields.joinToString {
                "${it.name}${if (it.required) "" else "?"}: ${enums["$name.${it.name}"]?.joinToString("|") ?: ranges["$name.${it.name}"]?.let { r -> "${r.first}..${r.last}" } ?: it.type}${if (isStatic(name, it.name)) " [literal]" else ""}"
            }})")
        }
        appendLine("Pure local functions:")
        CardFunctions.specs.forEach { (name, spec) ->
            appendLine("$name(${(spec.required.map { "$it: value" } + spec.optional.map { "$it?: value" }).joinToString()})")
        }
        appendLine("Table rows supports direct bindings and calculations inside cells, as well as a binding/calculation for whole rows. Evaluated rows must be up to 32 arrays of scalar cells, each matching the number of columns.")
        appendLine("Event(name: string, context?: object) describes a Button/Pressable action or a local-only onChange. Context values may contain nested objects, arrays, bindings and local calculations. They are evaluated when dispatched; compiling does not execute actions. A local_action/set value may directly be an object literal.")
        authoringExamples.forEach { (name, source) ->
            appendLine("Complete example ($name):")
            appendLine(source)
        }
    }
}
