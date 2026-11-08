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

package androidx.a2ui.compose.ui.catalog

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentReference
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.compose.runtime.ChildListA2uiProperty
import androidx.a2ui.compose.runtime.DynamicA2uiProperty
import androidx.a2ui.compose.runtime.StaticA2uiProperty
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.model.catalog.A2uiFunction
import androidx.a2ui.model.protocol.A2uiException
import androidx.a2ui.model.protocol.A2uiException.A2uiRuntimeException
import androidx.a2ui.model.schema.A2uiAnySchema
import androidx.a2ui.model.schema.A2uiArraySchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchema
import androidx.a2ui.model.schema.A2uiSchemaKeyword
import androidx.a2ui.model.schema.A2uiStringSchema
import androidx.a2ui.model.schema.commontypes.A2uiAccessibilityAttributesSchema
import androidx.a2ui.model.schema.commontypes.A2uiCheckRuleSchema
import androidx.a2ui.model.schema.commontypes.A2uiDataBindingSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicStringSchema
import androidx.collection.mutableScatterSetOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastMap
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Defines the API contract and schemas for the A2UI Basic Catalog V1 following the specification in
 * https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json.
 *
 * This class is intended to support the Basic Catalog specification 1.0 defined in
 * https://a2ui.org/specification/v1_0/catalogs/basic/catalog.json, support for it is planned.
 *
 * This class acts as the API contract for any design system (e.g. Material 3, Jetpack Compose
 * Glimmer, TV Compose) wishing to implement the basic catalog specification.
 *
 * @property text the [Text] component implementation
 * @property image the [Image] component implementation
 * @property icon the [Icon] component implementation
 * @property video the [Video] component implementation
 * @property audioPlayer the [AudioPlayer] component implementation
 * @property row the [Row] component implementation
 * @property column the [Column] component implementation
 * @property list the [List] component implementation
 * @property card the [Card] component implementation
 * @property tabs the [Tabs] component implementation
 * @property modal the [Modal] component implementation
 * @property divider the [Divider] component implementation
 * @property button the [Button] component implementation
 * @property textField the [TextField] component implementation
 * @property checkBox the [CheckBox] component implementation
 * @property choicePicker the [ChoicePicker] component implementation
 * @property slider the [Slider] component implementation
 * @property dateTimeInput the [DateTimeInput] component implementation
 * @property functions the list of [A2uiFunction]s supported by this catalog, recommended default is
 *   to create the function list using
 *   [androidx.a2ui.model.catalog.basiccatalog.createBasicCatalogFunctions]
 */
public class A2uiBasicCatalogV1(
    public val text: Text,
    public val image: Image,
    public val icon: Icon,
    public val video: Video,
    public val audioPlayer: AudioPlayer,
    public val row: Row,
    public val column: Column,
    public val list: List,
    public val card: Card,
    public val tabs: Tabs,
    public val modal: Modal,
    public val divider: Divider,
    public val button: Button,
    public val textField: TextField,
    public val checkBox: CheckBox,
    public val choicePicker: ChoicePicker,
    public val slider: Slider,
    public val dateTimeInput: DateTimeInput,
    public val functions: kotlin.collections.List<A2uiFunction>,
) {

    /** The unique identifier for this catalog. */
    public val catalogId: String = CatalogId

    /** The [A2uiSchema] defining the dynamic theme schema for this catalog. */
    public val themeSchema: A2uiSchema = ThemeSchema

    /** The list of [A2uiComponent]s supported by this catalog. */
    public val components: kotlin.collections.List<A2uiComponent> =
        listOf(
            text,
            image,
            icon,
            video,
            audioPlayer,
            row,
            column,
            list,
            card,
            tabs,
            modal,
            divider,
            button,
            textField,
            checkBox,
            choicePicker,
            slider,
            dateTimeInput,
        )

    public companion object {
        /** The catalog ID for the A2UI Basic Catalog V1. */
        // TODO(b/547900174): update to v1.0 once the support for it is implemented.
        public const val CatalogId: String =
            "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json"

        /** The standard theme schema for the A2UI Basic Catalog V1. */
        public val ThemeSchema: A2uiSchema =
            A2uiObjectSchema(
                properties =
                    mapOf(
                        "primaryColor" to
                            A2uiStringSchema(
                                description =
                                    "The primary brand color used for highlights (e.g., primary " +
                                        "buttons, active borders). Renderers may generate " +
                                        "variants of this color for different contexts. Format: " +
                                        "Hexadecimal code (e.g., '#00BFFF')."
                            ),
                        "iconUrl" to
                            A2uiStringSchema(
                                description =
                                    "A URL for an image that identifies the agent or tool " +
                                        "associated with the surface."
                            ),
                        "agentDisplayName" to
                            A2uiStringSchema(
                                description =
                                    "Text to be displayed next to the surface to identify the " +
                                        "agent or tool that created it."
                            ),
                    )
            )

        /**
         * The relative weight property for [A2uiBasicCatalogV1] components.
         *
         * Defined at the catalog level because it is supported across all [A2uiBasicCatalogV1]
         * components rather than belonging to a single component type.
         *
         * Expected usage:
         * * **Components**: Included in the [A2uiComponent.properties] list of all basic catalog
         *   components.
         * * **Parent components**: Container components (such as [Row] and [Column]) access this
         *   property from child component properties to apply layout weights to their direct
         *   children.
         */
        public val WeightProperty: StaticA2uiProperty<Number> =
            A2uiProperty.number(
                key = "weight",
                description =
                    "The relative weight of the component within a Row or Column. " +
                        "Note: this may ONLY be set when the component is a direct descendant of a " +
                        "Row or Column.",
            )

        /**
         * Holds accessibility attributes for a component in the A2UI Basic Catalog V1.
         *
         * This property is part of the basic catalog infrastructure and is shared across all
         * components rather than belonging to a single component type. It must be included in the
         * [A2uiComponent.properties] list of all basic catalog components.
         */
        internal val AccessibilityProperty: DynamicA2uiProperty<AccessibilityAttributes> =
            A2uiProperty.dynamicCustom(
                key = "accessibility",
                schema = A2uiAccessibilityAttributesSchema.DEFAULT_INSTANCE,
                safeCast = { value ->
                    when (value) {
                        is AccessibilityAttributes -> value
                        is Map<*, *> ->
                            AccessibilityAttributes(
                                label = value["label"]?.toString(),
                                description = value["description"]?.toString(),
                            )
                        else -> null
                    }
                },
            )

        /**
         * Holds client-side check validation rules for a component in the A2UI Basic Catalog V1.
         *
         * This property is part of the basic catalog infrastructure and is shared across checkable
         * components rather than belonging to a single component type. It must be included in the
         * [A2uiComponent.properties] list of all basic catalog components that support checks. See
         * [CheckRule].
         */
        internal val ChecksProperty: DynamicA2uiProperty<kotlin.collections.List<CheckRule>> =
            A2uiProperty.dynamicCustom(
                key = "checks",
                schema =
                    A2uiArraySchema(
                        items = A2uiCheckRuleSchema.DEFAULT_INSTANCE,
                        description =
                            "A list of checks to perform. These are function calls that must " +
                                "return a boolean indicating validity.",
                    ),
                safeCast = { value ->
                    when (value) {
                        is kotlin.collections.List<*> ->
                            buildList(value.size) {
                                for (i in value.indices) {
                                    when (val item = value[i]) {
                                        is CheckRule -> add(item)
                                        is Map<*, *> -> {
                                            val condition =
                                                item["condition"] as? Boolean
                                                    ?: return@dynamicCustom null
                                            val message =
                                                item["message"]?.toString()
                                                    ?: return@dynamicCustom null
                                            add(CheckRule(condition = condition, message = message))
                                        }
                                        else -> return@dynamicCustom null
                                    }
                                }
                            }
                        else -> null
                    }
                },
            )

        /**
         * Binds the [ChecksProperty] from component [properties].
         *
         * @param properties component properties to extract checks from
         * @return list of evaluated [CheckRule]s, an empty list if [ChecksProperty] is not declared
         *   in [properties], or `null` if dynamic conditions are still being evaluated
         */
        @Composable
        internal fun A2uiComponentScope.bindChecks(
            properties: A2uiComponentProperties
        ): kotlin.collections.List<CheckRule>? =
            if (ChecksProperty !in properties) {
                emptyList()
            } else {
                properties.bind(ChecksProperty)
            }
    }

    /**
     * Attributes to enhance accessibility of the A2UI Basic Catalog components when using assistive
     * technologies like screen readers.
     *
     * @property label a short string, typically 1 to 3 words, used by assistive technologies to
     *   convey the purpose or intent of an element. For example, an input field might have an
     *   accessible label of 'User ID' or a button might be labeled 'Submit'. When `null`, no custom
     *   accessibility label is set, and assistive technologies fall back to the component's default
     *   text or intrinsic content.
     * @property description additional information provided by assistive technologies about an
     *   element such as instructions, format requirements, or result of an action. For example, a
     *   mute button might have a label of 'Mute' and a description of 'Silences notifications about
     *   this conversation'. When `null`, no supplemental description is provided beyond the
     *   element's primary label.
     */
    @Immutable
    public class AccessibilityAttributes(
        public val label: String? = null,
        public val description: String? = null,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AccessibilityAttributes) return false

            if (label != other.label) return false
            if (description != other.description) return false

            return true
        }

        override fun hashCode(): Int {
            var result = label.hashCode()
            result = 31 * result + description.hashCode()
            return result
        }

        override fun toString(): String =
            "AccessibilityAttributes(label=$label, description=$description)"
    }

    /**
     * Holds the evaluated result of a client-side validation rule.
     *
     * Check rules define client-side constraints evaluated against dynamic data or user input.
     * Components receive evaluated rules in `TypedContent` to show validation feedback or guard
     * actions.
     *
     * **Common Use Cases:**
     * * **Input validation feedback**: Input components (e.g., [TextField], [DateTimeInput],
     *   [Slider], [CheckBox], [ChoicePicker]) inspect [condition]. When `false`, the component
     *   enters an error state (such as an error outline or icon) and displays [message] in
     *   supporting or helper text.
     * * **Action gating and submit prevention**: Action components (e.g., [Button]) inspect
     *   [condition] across form fields. When any rule fails, the button disables action dispatch or
     *   shows [message] explaining why the action cannot run.
     * * **Cross-component dependency validation**: Check conditions observe shared data model paths
     *   to enforce relational constraints (e.g., verifying an end date succeeds a start date, or
     *   requiring a checkbox to be checked before submitting).
     *
     * When all checks have [condition] equal to `true` (or the list is empty), the component is
     * valid and renders normally. If dynamic check expressions are still resolving, the component
     * waits in a loading state until all bound paths resolve.
     *
     * @property condition true if the check passed; false if the validation rule failed
     * @property message error message to display when [condition] is false
     */
    @Immutable
    public class CheckRule(
        @get:Suppress("GetterSetterNames") public val condition: Boolean,
        public val message: String,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CheckRule) return false

            if (condition != other.condition) return false
            if (message != other.message) return false

            return true
        }

        override fun hashCode(): Int {
            var result = condition.hashCode()
            result = 31 * result + message.hashCode()
            return result
        }

        override fun toString(): String = "CheckRule(condition=$condition, message='$message')"
    }

    /**
     * The A2UI `"Text"` component for displaying text.
     *
     * **Schema Properties:**
     * * `accessibility` (Dynamic Custom, optional): Accessibility attributes for the text.
     * * `weight` (Number, optional): The relative weight of the component within a Row or Column.
     *   May only be set when the component is a direct descendant of a Row or Column. See
     *   [WeightProperty].
     * * `text` (Dynamic String, required): The text content to display. Accepts either a static
     *   string literal or a dynamic data binding.
     * * `variant` (String Enum, optional): A hint for the base text style. This is a static
     *   configuration and does not support dynamic data bindings. Valid options: `"h1"`, `"h2"`,
     *   `"h3"`, `"h4"`, `"h5"`, `"caption"`, `"body"`. Defaults to `"body"`.
     */
    public interface Text : A2uiComponent {
        override val name: String
            get() = "Text"

        override val description: String
            get() = "Displays dynamic text."

        /** Typography style variant for a [Text]. */
        public enum class Variant(public val value: String) {
            H1("h1"),
            H2("h2"),
            H3("h3"),
            H4("h4"),
            H5("h5"),
            Caption("caption"),
            Body("body");

            public companion object {
                /** The default [Variant] when unspecified in the component payload. */
                public val Default: Variant = Body

                /** Returns the [Variant] matching [value], or [Default] if unknown. */
                public fun fromValue(value: String): Variant =
                    entries.fastFirstOrNull { it.value == value } ?: Default
            }
        }

        public companion object {
            /** The [A2uiProperty] for the `"accessibility"` property of a [Text]. */
            public val AccessibilityProperty: DynamicA2uiProperty<AccessibilityAttributes> =
                A2uiBasicCatalogV1.AccessibilityProperty

            /** The [A2uiProperty] for the `"text"` property of a [Text]. */
            public val TextProperty: DynamicA2uiProperty<String> =
                A2uiProperty.dynamicString(
                    key = "text",
                    required = true,
                    description =
                        "The text content to display. While simple Markdown formatting " +
                            "is supported (i.e. without HTML, images, or links), utilizing dedicated " +
                            "UI components is generally preferred for a richer and more structured " +
                            "presentation.",
                )

            /** The [A2uiProperty] for the `"variant"` property of a [Text]. */
            public val VariantProperty: StaticA2uiProperty<Variant> =
                A2uiProperty.enum(
                    key = "variant",
                    enumValues = Variant.entries,
                    mapToString = { it.value },
                    convertFromString = Variant::fromValue,
                    defaultValue = Variant.Default,
                    description = "A hint for the base text style.",
                )

            internal val ComponentProperties: kotlin.collections.List<A2uiProperty<*>> =
                listOf(AccessibilityProperty, WeightProperty, TextProperty, VariantProperty)
        }

        override val properties: kotlin.collections.List<A2uiProperty<*>>
            get() = ComponentProperties

        @Composable
        override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
            properties.bind(TextProperty) != null

        @Composable
        override fun A2uiComponentScope.Content(
            properties: A2uiComponentProperties,
            modifier: Modifier,
        ) {
            val textValue =
                checkNotNull(properties.bind(TextProperty)) {
                    "Required property '${TextProperty.key}' is missing."
                }
            val variant = properties[VariantProperty] ?: Variant.Default
            val accessibility = properties.bind(AccessibilityProperty)
            TypedContent(
                text = textValue,
                variant = variant,
                accessibility = accessibility,
                modifier = modifier,
            )
        }

        /**
         * Renders the [Text] with its resolved property values.
         *
         * @param text the text string to display
         * @param variant [Variant] typography style of the text
         * @param accessibility accessibility attributes for the text
         * @param modifier [Modifier] to apply to the layout
         */
        @Composable
        public fun A2uiComponentScope.TypedContent(
            text: String,
            variant: Variant,
            accessibility: AccessibilityAttributes?,
            modifier: Modifier,
        )
    }

    /**
     * The A2UI `"Image"` component for displaying an image from a URL.
     *
     * **Schema Properties:**
     * * `accessibility` (Dynamic Custom, optional): Accessibility attributes for the image.
     * * `weight` (Number, optional): The relative weight of the component within a Row or Column.
     *   May only be set when the component is a direct descendant of a Row or Column. See
     *   [WeightProperty].
     * * `url` (Dynamic String, required): The URL of the image to display.
     * * `description` (Dynamic String, optional): Accessibility text for the image.
     * * `fit` (String Enum, optional): Specifies how the image should be resized to fit its
     *   container. This corresponds to the CSS 'object-fit' property. Valid options: `"contain"`,
     *   `"cover"`, `"fill"`, `"none"`, `"scaleDown"`. Defaults to `"fill"`.
     * * `variant` (String Enum, optional): A hint for the image size and style. Valid options:
     *   `"icon"`, `"avatar"`, `"smallFeature"`, `"mediumFeature"`, `"largeFeature"`, `"header"`.
     *   Defaults to `"mediumFeature"`.
     */
    public interface Image : A2uiComponent {
        override val name: String
            get() = "Image"

        override val description: String
            get() = "Displays an image from a URL."

        /** Resizing behavior for an [Image] within its container. */
        public enum class Fit(public val value: String) {
            Contain("contain"),
            Cover("cover"),
            Fill("fill"),
            None("none"),
            ScaleDown("scaleDown");

            public companion object {
                /** The default [Fit] when unspecified in the component payload. */
                public val Default: Fit = Fill

                /** Returns the [Fit] matching [value], or [Default] if unknown. */
                public fun fromValue(value: String): Fit =
                    entries.fastFirstOrNull { it.value == value } ?: Default
            }
        }

        /** Size and style variant for an [Image]. */
        public enum class Variant(public val value: String) {
            Icon("icon"),
            Avatar("avatar"),
            SmallFeature("smallFeature"),
            MediumFeature("mediumFeature"),
            LargeFeature("largeFeature"),
            Header("header");

            public companion object {
                /** The default [Variant] when unspecified in the component payload. */
                public val Default: Variant = MediumFeature

                /** Returns the [Variant] matching [value], or [Default] if unknown. */
                public fun fromValue(value: String): Variant =
                    entries.fastFirstOrNull { it.value == value } ?: Default
            }
        }

        public companion object {
            /** The [A2uiProperty] for the `"accessibility"` property of an [Image]. */
            public val AccessibilityProperty: DynamicA2uiProperty<AccessibilityAttributes> =
                A2uiBasicCatalogV1.AccessibilityProperty

            /** The [A2uiProperty] for the `"url"` property of an [Image]. */
            public val UrlProperty: DynamicA2uiProperty<String> =
                A2uiProperty.dynamicString(
                    key = "url",
                    required = true,
                    description = "The URL of the image to display.",
                )

            /** The [A2uiProperty] for the `"description"` property of an [Image]. */
            public val DescriptionProperty: DynamicA2uiProperty<String> =
                A2uiProperty.dynamicString(
                    key = "description",
                    required = false,
                    description = "Accessibility text for the image.",
                )

            /** The [A2uiProperty] for the `"fit"` property of an [Image]. */
            public val FitProperty: StaticA2uiProperty<Fit> =
                A2uiProperty.enum(
                    key = "fit",
                    enumValues = Fit.entries,
                    mapToString = { it.value },
                    convertFromString = Fit::fromValue,
                    defaultValue = Fit.Default,
                    description =
                        "Specifies how the image should be resized to fit its container. " +
                            "This corresponds to the CSS 'object-fit' property.",
                )

            /** The [A2uiProperty] for the `"variant"` property of an [Image]. */
            public val VariantProperty: StaticA2uiProperty<Variant> =
                A2uiProperty.enum(
                    key = "variant",
                    enumValues = Variant.entries,
                    mapToString = { it.value },
                    convertFromString = Variant::fromValue,
                    defaultValue = Variant.Default,
                    description = "A hint for the image size and style.",
                )

            internal val ComponentProperties: kotlin.collections.List<A2uiProperty<*>> =
                listOf(
                    AccessibilityProperty,
                    WeightProperty,
                    UrlProperty,
                    DescriptionProperty,
                    FitProperty,
                    VariantProperty,
                )
        }

        override val properties: kotlin.collections.List<A2uiProperty<*>>
            get() = ComponentProperties

        @Composable
        override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
            properties.bind(UrlProperty) != null

        @Composable
        override fun A2uiComponentScope.Content(
            properties: A2uiComponentProperties,
            modifier: Modifier,
        ) {
            val url =
                checkNotNull(properties.bind(UrlProperty)) {
                    "Required property '${UrlProperty.key}' is missing."
                }
            val description = properties.bind(DescriptionProperty)
            val fit = properties[FitProperty] ?: Fit.Default
            val variant = properties[VariantProperty] ?: Variant.Default
            val accessibility = properties.bind(AccessibilityProperty)

            TypedContent(
                url = url,
                description = description,
                fit = fit,
                variant = variant,
                accessibility = accessibility,
                modifier = modifier,
            )
        }

        /**
         * Renders the [Image] with its resolved property values.
         *
         * @param url the URL of the image to display
         * @param description accessibility text for the image
         * @param fit [Fit] resizing behavior of the image within its container
         * @param variant [Variant] size and style of the image
         * @param accessibility accessibility attributes for the image
         * @param modifier [Modifier] to apply to the layout
         */
        @Composable
        public fun A2uiComponentScope.TypedContent(
            url: String,
            description: String?,
            fit: Fit,
            variant: Variant,
            accessibility: AccessibilityAttributes?,
            modifier: Modifier,
        )
    }

    /**
     * The A2UI `"Icon"` component for displaying an icon.
     *
     * **Schema Properties:**
     * * `accessibility` (Dynamic Custom, optional): Accessibility attributes for the icon.
     * * `weight` (Number, optional): The relative weight of the component within a Row or Column.
     *   May only be set when the component is a direct descendant of a Row or Column. See
     *   [WeightProperty].
     * * `name` (Dynamic Custom, required): The name of the icon to display. Accepts either a static
     *   string literal from the predefined list, an object with an `svgPath` string, or a dynamic
     *   data binding.
     */
    public interface Icon : A2uiComponent {
        override val name: String
            get() = "Icon"

        override val description: String
            get() = "Displays an icon from a predefined set of icons or an SVG path."

        /** The visual source for the [Icon]. */
        public sealed interface Source

        /**
         * An [Icon] drawn from a client-provided SVG path rather than a [BuiltIn] token.
         *
         * @property svgPath the SVG path data to draw
         */
        @Immutable
        public class SvgPath(public val svgPath: String) : Source {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is SvgPath) return false
                return svgPath == other.svgPath
            }

            override fun hashCode(): Int = svgPath.hashCode()

            override fun toString(): String = "SvgPath(svgPath='$svgPath')"
        }

        /** Built-in icon token for an [Icon]. */
        public enum class BuiltIn(public val value: String) : Source {
            AccountCircle("accountCircle"),
            Add("add"),
            ArrowBack("arrowBack"),
            ArrowForward("arrowForward"),
            AttachFile("attachFile"),
            CalendarToday("calendarToday"),
            Call("call"),
            Camera("camera"),
            Check("check"),
            Close("close"),
            Delete("delete"),
            Download("download"),
            Edit("edit"),
            Error("error"),
            Event("event"),
            FastForward("fastForward"),
            Favorite("favorite"),
            FavoriteOff("favoriteOff"),
            Folder("folder"),
            Help("help"),
            Home("home"),
            Info("info"),
            LocationOn("locationOn"),
            Lock("lock"),
            LockOpen("lockOpen"),
            Mail("mail"),
            Menu("menu"),
            MoreHoriz("moreHoriz"),
            MoreVert("moreVert"),
            Notifications("notifications"),
            NotificationsOff("notificationsOff"),
            Pause("pause"),
            Payment("payment"),
            Person("person"),
            Phone("phone"),
            Photo("photo"),
            Play("play"),
            Print("print"),
            Refresh("refresh"),
            Rewind("rewind"),
            Search("search"),
            Send("send"),
            Settings("settings"),
            Share("share"),
            ShoppingCart("shoppingCart"),
            SkipNext("skipNext"),
            SkipPrevious("skipPrevious"),
            Star("star"),
            StarHalf("starHalf"),
            StarOff("starOff"),
            Stop("stop"),
            Upload("upload"),
            Visibility("visibility"),
            VisibilityOff("visibilityOff"),
            VolumeDown("volumeDown"),
            VolumeMute("volumeMute"),
            VolumeOff("volumeOff"),
            VolumeUp("volumeUp"),
            Warning("warning");

            public companion object {
                /** Returns the [BuiltIn] matching [value], or `null` if unknown. */
                public fun fromValue(value: String): BuiltIn? = entries.fastFirstOrNull {
                    it.value == value
                }
            }
        }

        /**
         * An [Icon] named by a string that matches no predefined [BuiltIn] token.
         *
         * @property name the unrecognized icon name from the component payload
         */
        @Immutable
        public class Unrecognized(public val name: String) : Source {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Unrecognized) return false
                return name == other.name
            }

            override fun hashCode(): Int = name.hashCode()

            override fun toString(): String = "Unrecognized(name='$name')"
        }

        public companion object {
            /** The [A2uiProperty] for the `"accessibility"` property of an [Icon]. */
            public val AccessibilityProperty: DynamicA2uiProperty<AccessibilityAttributes> =
                A2uiBasicCatalogV1.AccessibilityProperty

            private val nameSchema: A2uiSchema =
                A2uiAnySchema(
                    description = "The name of the icon to display.",
                    keywords =
                        listOf(
                            A2uiSchemaKeyword.OneOf(
                                listOf(
                                    A2uiStringSchema(
                                        keywords =
                                            listOf(
                                                A2uiSchemaKeyword.Enum(
                                                    BuiltIn.entries.fastMap { it.value }
                                                )
                                            )
                                    ),
                                    A2uiObjectSchema(
                                        properties = mapOf("svgPath" to A2uiStringSchema.INSTANCE),
                                        required = setOf("svgPath"),
                                        isAdditionalPropertiesAllowed = false,
                                    ),
                                    A2uiDataBindingSchema.DEFAULT_INSTANCE,
                                )
                            )
                        ),
                )

            /** The [A2uiProperty] for the `"name"` property of an [Icon]. */
            public val NameProperty: DynamicA2uiProperty<Source> =
                A2uiProperty.dynamicCustom(
                    key = "name",
                    required = true,
                    schema = nameSchema,
                    safeCast = { value ->
                        when (value) {
                            is String -> BuiltIn.fromValue(value) ?: Unrecognized(value)
                            is Map<*, *> -> (value["svgPath"] as? String)?.let { SvgPath(it) }
                            else -> null
                        }
                    },
                )

            internal val ComponentProperties: kotlin.collections.List<A2uiProperty<*>> =
                listOf(AccessibilityProperty, WeightProperty, NameProperty)
        }

        override val properties: kotlin.collections.List<A2uiProperty<*>>
            get() = ComponentProperties

        @Composable
        override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
            properties.bind(NameProperty) != null

        @Composable
        override fun A2uiComponentScope.Content(
            properties: A2uiComponentProperties,
            modifier: Modifier,
        ) {
            val nameValue =
                checkNotNull(properties.bind(NameProperty)) {
                    "Required property '${NameProperty.key}' is missing."
                }
            val accessibility = properties.bind(AccessibilityProperty)
            TypedContent(source = nameValue, accessibility = accessibility, modifier = modifier)
        }

        /**
         * Renders the [Icon] with its resolved property values.
         *
         * @param source the resolved [Source] identifying the visual to draw
         * @param accessibility accessibility attributes for the icon
         * @param modifier [Modifier] to apply to the layout
         */
        @Composable
        public fun A2uiComponentScope.TypedContent(
            source: Source,
            accessibility: AccessibilityAttributes?,
            modifier: Modifier,
        )
    }

    /**
     * The A2UI `"Video"` component for displaying a video from a URL.
     *
     * **Schema Properties:**
     * * `accessibility` (Dynamic Custom, optional): Accessibility attributes for the video.
     * * `weight` (Number, optional): The relative weight of the component within a Row or Column.
     *   May only be set when the component is a direct descendant of a Row or Column. See
     *   [WeightProperty].
     * * `url` (Dynamic String, required): The URL of the video to display.
     */
    public interface Video : A2uiComponent {
        override val name: String
            get() = "Video"

        override val description: String
            get() = "Displays a video from a URL."

        public companion object {
            /** The [A2uiProperty] for the `"accessibility"` property of a [Video]. */
            public val AccessibilityProperty: DynamicA2uiProperty<AccessibilityAttributes> =
                A2uiBasicCatalogV1.AccessibilityProperty

            /** The [A2uiProperty] for the `"url"` property of a [Video]. */
            public val UrlProperty: DynamicA2uiProperty<String> =
                A2uiProperty.dynamicString(
                    key = "url",
                    required = true,
                    description = "The URL of the video to display.",
                )

            internal val ComponentProperties: kotlin.collections.List<A2uiProperty<*>> =
                listOf(AccessibilityProperty, WeightProperty, UrlProperty)
        }

        override val properties: kotlin.collections.List<A2uiProperty<*>>
            get() = ComponentProperties

        @Composable
        override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
            properties.bind(UrlProperty) != null

        @Composable
        override fun A2uiComponentScope.Content(
            properties: A2uiComponentProperties,
            modifier: Modifier,
        ) {
            val url =
                checkNotNull(properties.bind(UrlProperty)) {
                    "Required property '${UrlProperty.key}' is missing."
                }
            val accessibility = properties.bind(AccessibilityProperty)

            TypedContent(url = url, accessibility = accessibility, modifier = modifier)
        }

        /**
         * Renders the [Video] with its resolved property values.
         *
         * @param url the URL of the video to display
         * @param accessibility accessibility attributes for the video
         * @param modifier [Modifier] to apply to the layout
         */
        @Composable
        public fun A2uiComponentScope.TypedContent(
            url: String,
            accessibility: AccessibilityAttributes?,
            modifier: Modifier,
        )
    }

    /**
     * The A2UI `"AudioPlayer"` component for playing audio content from a URL.
     *
     * **Schema Properties:**
     * * `accessibility` (Dynamic Custom, optional): Accessibility attributes for the audio player.
     * * `weight` (Number, optional): The relative weight of the component within a Row or Column.
     *   May only be set when the component is a direct descendant of a Row or Column. See
     *   [WeightProperty].
     * * `url` (Dynamic String, required): The URL of the audio to be played.
     * * `description` (Dynamic String, optional): A description of the audio, such as a title or
     *   summary.
     */
    public interface AudioPlayer : A2uiComponent {
        override val name: String
            get() = "AudioPlayer"

        override val description: String
            get() = "A player for audio content from a URL."

        public companion object {
            /** The [A2uiProperty] for the `"accessibility"` property of an [AudioPlayer]. */
            public val AccessibilityProperty: DynamicA2uiProperty<AccessibilityAttributes> =
                A2uiBasicCatalogV1.AccessibilityProperty

            /** The [A2uiProperty] for the `"url"` property of an [AudioPlayer]. */
            public val UrlProperty: DynamicA2uiProperty<String> =
                A2uiProperty.dynamicString(
                    key = "url",
                    required = true,
                    description = "The URL of the audio to be played.",
                )

            /** The [A2uiProperty] for the `"description"` property of an [AudioPlayer]. */
            public val DescriptionProperty: DynamicA2uiProperty<String> =
                A2uiProperty.dynamicString(
                    key = "description",
                    required = false,
                    description = "A description of the audio, such as a title or summary.",
                )

            internal val ComponentProperties: kotlin.collections.List<A2uiProperty<*>> =
                listOf(AccessibilityProperty, WeightProperty, UrlProperty, DescriptionProperty)
        }

        override val properties: kotlin.collections.List<A2uiProperty<*>>
            get() = ComponentProperties

        @Composable
        override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
            properties.bind(UrlProperty) != null

        @Composable
        override fun A2uiComponentScope.Content(
            properties: A2uiComponentProperties,
            modifier: Modifier,
        ) {
            val url =
                checkNotNull(properties.bind(UrlProperty)) {
                    "Required property '${UrlProperty.key}' is missing."
                }
            val description = properties.bind(DescriptionProperty)
            val accessibility = properties.bind(AccessibilityProperty)

            TypedContent(
                url = url,
                description = description,
                accessibility = accessibility,
                modifier = modifier,
            )
        }

        /**
         * Renders the [AudioPlayer] with its resolved property values.
         *
         * @param url the URL of the audio to be played
         * @param description a description of the audio, such as a title or summary
         * @param accessibility accessibility attributes for the audio player
         * @param modifier [Modifier] to apply to the layout
         */
        @Composable
        public fun A2uiComponentScope.TypedContent(
            url: String,
            description: String?,
            accessibility: AccessibilityAttributes?,
            modifier: Modifier,
        )
    }

    /**
     * The A2UI `"Row"` component for displaying content in a horizontal layout.
     *
     * **Schema Properties:**
     * * `accessibility` (Dynamic Custom, optional): Accessibility attributes for the row.
     * * `weight` (Number, optional): The relative weight of the component within a Row or Column.
     *   May only be set when the component is a direct descendant of a Row or Column. See
     *   [WeightProperty].
     * * `children` (ChildList, required): Defines the children, accepting either an array of
     *   strings for a fixed set of children, or a template object to generate children from a data
     *   list.
     * * `justify` (String Enum, optional): Defines the arrangement of children along the main axis
     *   (horizontally). Valid options: `"center"`, `"end"`, `"spaceAround"`, `"spaceBetween"`,
     *   `"spaceEvenly"`, `"start"`, `"stretch"`. Defaults to `"start"`.
     * * `align` (String Enum, optional): Defines the alignment of children along the cross axis
     *   (vertically). Valid options: `"start"`, `"center"`, `"end"`, `"stretch"`. Defaults to
     *   `"start"`.
     */
    public interface Row : A2uiComponent {
        override val name: String
            get() = "Row"

        override val description: String
            get() =
                "A layout component that arranges its children horizontally. To create a grid " +
                    "layout, nest Columns within this Row."

        /** Arrangement of children along the main axis of a [Row]. */
        public enum class Justify(public val value: String) {
            Center("center"),
            End("end"),
            SpaceAround("spaceAround"),
            SpaceBetween("spaceBetween"),
            SpaceEvenly("spaceEvenly"),
            Start("start"),
            Stretch("stretch");

            public companion object {
                /** The default [Justify] when unspecified in the component payload. */
                public val Default: Justify = Start

                /** Returns the [Justify] matching [value], or [Default] if unknown. */
                public fun fromValue(value: String): Justify =
                    entries.fastFirstOrNull { it.value == value } ?: Default
            }
        }

        /** Alignment of children along the cross axis of a [Row]. */
        public enum class Align(public val value: String) {
            Start("start"),
            Center("center"),
            End("end"),
            Stretch("stretch");

            public companion object {
                /** The default [Align] when unspecified in the component payload. */
                public val Default: Align = Start

                /** Returns the [Align] matching [value], or [Default] if unknown. */
                public fun fromValue(value: String): Align =
                    entries.fastFirstOrNull { it.value == value } ?: Default
            }
        }

        public companion object {
            /** The [A2uiProperty] for the `"accessibility"` property of a [Row]. */
            public val AccessibilityProperty: DynamicA2uiProperty<AccessibilityAttributes> =
                A2uiBasicCatalogV1.AccessibilityProperty

            /** The [A2uiProperty] for the `"children"` property of a [Row]. */
            public val ChildrenProperty: ChildListA2uiProperty =
                A2uiProperty.childList(
                    key = "children",
                    required = true,
                    description =
                        "Defines the children. Use an array of strings for a fixed set of " +
                            "children, or a template object to generate children from a data " +
                            "list. Children cannot be defined inline, they must be referred to " +
                            "by ID.",
                )

            /** The [A2uiProperty] for the `"justify"` property of a [Row]. */
            public val JustifyProperty: StaticA2uiProperty<Justify> =
                A2uiProperty.enum(
                    key = "justify",
                    enumValues = Justify.entries,
                    mapToString = { it.value },
                    convertFromString = Justify::fromValue,
                    defaultValue = Justify.Default,
                    description =
                        "Defines the arrangement of children along the main axis (horizontally). " +
                            "Use 'spaceBetween' to push items to the edges, or " +
                            "'start'/'end'/'center' to pack them together.",
                )

            /** The [A2uiProperty] for the `"align"` property of a [Row]. */
            public val AlignProperty: StaticA2uiProperty<Align> =
                A2uiProperty.enum(
                    key = "align",
                    enumValues = Align.entries,
                    mapToString = { it.value },
                    convertFromString = Align::fromValue,
                    defaultValue = Align.Default,
                    description =
                        "Defines the alignment of children along the cross axis (vertically). " +
                            "This is similar to the CSS 'align-items' property, but uses " +
                            "camelCase values (e.g., 'start').",
                )

            internal val ComponentProperties: kotlin.collections.List<A2uiProperty<*>> =
                listOf(
                    AccessibilityProperty,
                    WeightProperty,
                    ChildrenProperty,
                    JustifyProperty,
                    AlignProperty,
                )
        }

        override val properties: kotlin.collections.List<A2uiProperty<*>>
            get() = ComponentProperties

        @Composable
        override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
            properties.bindChildReferences(ChildrenProperty) != null

        @Composable
        override fun A2uiComponentScope.Content(
            properties: A2uiComponentProperties,
            modifier: Modifier,
        ) {
            val children =
                checkNotNull(properties.bindChildReferences(ChildrenProperty)) {
                    "Required property '${ChildrenProperty.key}' is missing or could not be " +
                        "resolved."
                }
            val justify = properties[JustifyProperty] ?: Justify.Default
            val align = properties[AlignProperty] ?: Align.Default
            val accessibility = properties.bind(AccessibilityProperty)

            TypedContent(
                children = children,
                justify = justify,
                align = align,
                accessibility = accessibility,
                modifier = modifier,
            )
        }

        /**
         * Renders the [Row] with its resolved property values.
         *
         * @param children list of child [A2uiComponentReference]s to render in this row
         * @param justify [Justify] arrangement of children along the horizontal main axis
         * @param align [Align] alignment of children along the vertical cross axis
         * @param accessibility accessibility attributes for the row
         * @param modifier [Modifier] to apply to the layout
         */
        @Composable
        public fun A2uiComponentScope.TypedContent(
            children: kotlin.collections.List<A2uiComponentReference>,
            justify: Justify,
            align: Align,
            accessibility: AccessibilityAttributes?,
            modifier: Modifier,
        )
    }

    /**
     * The A2UI `"Column"` component for arranging children vertically.
     *
     * **Schema Properties:**
     * * `accessibility` (Dynamic Custom, optional): Accessibility attributes for the column.
     * * `weight` (Number, optional): The relative weight of the component within a Row or Column.
     *   May only be set when the component is a direct descendant of a Row or Column. See
     *   [WeightProperty].
     * * `children` (ChildList, required): Defines the children, accepting either an array of
     *   strings for a fixed set of children, or a template object to generate children from a data
     *   list.
     * * `justify` (String Enum, optional): Defines the arrangement of children along the main axis
     *   (vertically). Valid options: `"start"`, `"center"`, `"end"`, `"spaceBetween"`,
     *   `"spaceAround"`, `"spaceEvenly"`, `"stretch"`. Defaults to `"start"`.
     * * `align` (String Enum, optional): Defines the alignment of children along the cross axis
     *   (horizontally). Valid options: `"center"`, `"end"`, `"start"`, `"stretch"`. Defaults to
     *   `"start"`.
     */
    public interface Column : A2uiComponent {
        override val name: String
            get() = "Column"

        override val description: String
            get() =
                "A layout component that arranges its children vertically. To create a grid " +
                    "layout, nest Rows within this Column."

        /** Arrangement of children along the main axis of a [Column]. */
        public enum class Justify(public val value: String) {
            Start("start"),
            Center("center"),
            End("end"),
            SpaceBetween("spaceBetween"),
            SpaceAround("spaceAround"),
            SpaceEvenly("spaceEvenly"),
            Stretch("stretch");

            public companion object {
                /** The default [Justify] when unspecified in the component payload. */
                public val Default: Justify = Start

                /** Returns the [Justify] matching [value], or [Default] if unknown. */
                public fun fromValue(value: String): Justify =
                    entries.fastFirstOrNull { it.value == value } ?: Default
            }
        }

        /** Alignment of children along the cross axis of a [Column]. */
        public enum class Align(public val value: String) {
            Center("center"),
            End("end"),
            Start("start"),
            Stretch("stretch");

            public companion object {
                /** The default [Align] when unspecified in the component payload. */
                public val Default: Align = Start

                /** Returns the [Align] matching [value], or [Default] if unknown. */
                public fun fromValue(value: String): Align =
                    entries.fastFirstOrNull { it.value == value } ?: Default
            }
        }

        public companion object {
            /** The [A2uiProperty] for the `"accessibility"` property of a [Column]. */
            public val AccessibilityProperty: DynamicA2uiProperty<AccessibilityAttributes> =
                A2uiBasicCatalogV1.AccessibilityProperty

            /** The [A2uiProperty] for the `"children"` property of a [Column]. */
            public val ChildrenProperty: ChildListA2uiProperty =
                A2uiProperty.childList(
                    key = "children",
                    required = true,
                    description =
                        "Defines the children. Use an array of strings for a fixed set of " +
                            "children, or a template object to generate children from a data " +
                            "list. Children cannot be defined inline, they must be referred to " +
                            "by ID.",
                )

            /** The [A2uiProperty] for the `"justify"` property of a [Column]. */
            public val JustifyProperty: StaticA2uiProperty<Justify> =
                A2uiProperty.enum(
                    key = "justify",
                    enumValues = Justify.entries,
                    mapToString = { it.value },
                    convertFromString = Justify::fromValue,
                    defaultValue = Justify.Default,
                    description =
                        "Defines the arrangement of children along the main axis (vertically). " +
                            "Use 'spaceBetween' to push items to the edges (e.g. header at top, " +
                            "footer at bottom), or 'start'/'end'/'center' to pack them together.",
                )

            /** The [A2uiProperty] for the `"align"` property of a [Column]. */
            public val AlignProperty: StaticA2uiProperty<Align> =
                A2uiProperty.enum(
                    key = "align",
                    enumValues = Align.entries,
                    mapToString = { it.value },
                    convertFromString = Align::fromValue,
                    defaultValue = Align.Default,
                    description =
                        "Defines the alignment of children along the cross axis (horizontally). " +
                            "This is similar to the CSS 'align-items' property.",
                )

            internal val ComponentProperties: kotlin.collections.List<A2uiProperty<*>> =
                listOf(
                    AccessibilityProperty,
                    WeightProperty,
                    ChildrenProperty,
                    JustifyProperty,
                    AlignProperty,
                )
        }

        override val properties: kotlin.collections.List<A2uiProperty<*>>
            get() = ComponentProperties

        @Composable
        override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
            properties.bindChildReferences(ChildrenProperty) != null

        @Composable
        override fun A2uiComponentScope.Content(
            properties: A2uiComponentProperties,
            modifier: Modifier,
        ) {
            val children =
                checkNotNull(properties.bindChildReferences(ChildrenProperty)) {
                    "Required property '${ChildrenProperty.key}' is missing or could not be " +
                        "resolved."
                }
            val justify = properties[JustifyProperty] ?: Justify.Default
            val align = properties[AlignProperty] ?: Align.Default
            val accessibility = properties.bind(AccessibilityProperty)

            TypedContent(
                children = children,
                justify = justify,
                align = align,
                accessibility = accessibility,
                modifier = modifier,
            )
        }

        /**
         * Renders the [Column] with its resolved property values.
         *
         * @param children list of child [A2uiComponentReference]s to render in this column
         * @param justify [Justify] arrangement of children along the vertical main axis
         * @param align [Align] alignment of children along the horizontal cross axis
         * @param accessibility accessibility attributes for the column
         * @param modifier [Modifier] to apply to the layout
         */
        @Composable
        public fun A2uiComponentScope.TypedContent(
            children: kotlin.collections.List<A2uiComponentReference>,
            justify: Justify,
            align: Align,
            accessibility: AccessibilityAttributes?,
            modifier: Modifier,
        )
    }

    /**
     * The A2UI `"List"` component for displaying a scrollable list of components.
     *
     * **Schema Properties:**
     * * `accessibility` (Dynamic Custom, optional): Accessibility attributes for the list.
     * * `weight` (Number, optional): The relative weight of the component within a Row or Column.
     *   May only be set when the component is a direct descendant of a Row or Column. See
     *   [WeightProperty].
     * * `children` (ChildList, required): Defines the children. Use an array of strings for a fixed
     *   set of children, or a template object to generate children from a data list.
     * * `direction` (String Enum, optional): The direction in which the list items are laid out.
     *   Valid options: `"vertical"`, `"horizontal"`. Defaults to `"vertical"`.
     * * `align` (String Enum, optional): Defines the alignment of children along the cross axis.
     *   Valid options: `"start"`, `"center"`, `"end"`, `"stretch"`. Defaults to `"stretch"`.
     */
    public interface List : A2uiComponent {
        override val name: String
            get() = "List"

        override val description: String
            get() = "A scrollable list of components laid out vertically or horizontally."

        /** Layout direction of the items in a [List]. */
        public enum class Direction(public val value: String) {
            Vertical("vertical"),
            Horizontal("horizontal");

            public companion object {
                /** The default [Direction] when unspecified in the component payload. */
                public val Default: Direction = Vertical

                /** Returns the [Direction] matching [value], or [Default] if unknown. */
                public fun fromValue(value: String): Direction =
                    entries.fastFirstOrNull { it.value == value } ?: Default
            }
        }

        /** Alignment of children along the cross axis of a [List]. */
        public enum class Align(public val value: String) {
            Start("start"),
            Center("center"),
            End("end"),
            Stretch("stretch");

            public companion object {
                /** The default [Align] when unspecified in the component payload. */
                public val Default: Align = Stretch

                /** Returns the [Align] matching [value], or [Default] if unknown. */
                public fun fromValue(value: String): Align =
                    entries.fastFirstOrNull { it.value == value } ?: Default
            }
        }

        public companion object {
            /** The [A2uiProperty] for the `"accessibility"` property of a [List]. */
            public val AccessibilityProperty: DynamicA2uiProperty<AccessibilityAttributes> =
                A2uiBasicCatalogV1.AccessibilityProperty

            /** The [A2uiProperty] for the `"children"` property of a [List]. */
            public val ChildrenProperty: ChildListA2uiProperty =
                A2uiProperty.childList(
                    key = "children",
                    required = true,
                    description =
                        "Defines the children. Use an array of strings for a fixed set of " +
                            "children, or a template object to generate children from a data list.",
                )

            /** The [A2uiProperty] for the `"direction"` property of a [List]. */
            public val DirectionProperty: StaticA2uiProperty<Direction> =
                A2uiProperty.enum(
                    key = "direction",
                    enumValues = Direction.entries,
                    mapToString = { it.value },
                    convertFromString = Direction::fromValue,
                    defaultValue = Direction.Default,
                    description = "The direction in which the list items are laid out.",
                )

            /** The [A2uiProperty] for the `"align"` property of a [List]. */
            public val AlignProperty: StaticA2uiProperty<Align> =
                A2uiProperty.enum(
                    key = "align",
                    enumValues = Align.entries,
                    mapToString = { it.value },
                    convertFromString = Align::fromValue,
                    defaultValue = Align.Default,
                    description = "Defines the alignment of children along the cross axis.",
                )

            internal val ComponentProperties: kotlin.collections.List<A2uiProperty<*>> =
                listOf(
                    AccessibilityProperty,
                    WeightProperty,
                    ChildrenProperty,
                    DirectionProperty,
                    AlignProperty,
                )
        }

        override val properties: kotlin.collections.List<A2uiProperty<*>>
            get() = ComponentProperties

        @Composable
        override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
            properties.bindChildReferences(ChildrenProperty) != null

        @Composable
        override fun A2uiComponentScope.Content(
            properties: A2uiComponentProperties,
            modifier: Modifier,
        ) {
            val children =
                checkNotNull(properties.bindChildReferences(ChildrenProperty)) {
                    "Required property '${ChildrenProperty.key}' is missing or could not be " +
                        "resolved."
                }
            val direction = properties[DirectionProperty] ?: Direction.Default
            val align = properties[AlignProperty] ?: Align.Default
            val accessibility = properties.bind(AccessibilityProperty)

            TypedContent(
                children = children,
                direction = direction,
                align = align,
                accessibility = accessibility,
                modifier = modifier,
            )
        }

        /**
         * Renders the [List] with its resolved property values.
         *
         * @param children list of child [A2uiComponentReference]s to render in this list
         * @param direction [Direction] layout direction of the list items
         * @param align [Align] alignment of children along the cross axis
         * @param accessibility accessibility attributes for the list
         * @param modifier [Modifier] to apply to the layout
         */
        @Composable
        public fun A2uiComponentScope.TypedContent(
            children: kotlin.collections.List<A2uiComponentReference>,
            direction: Direction,
            align: Align,
            accessibility: AccessibilityAttributes?,
            modifier: Modifier,
        )
    }

    /**
     * The A2UI `"Card"` component for displaying content in a styled container.
     *
     * **Schema Properties:**
     * * `accessibility` (Dynamic Custom, optional): Accessibility attributes for the card.
     * * `weight` (Number, optional): The relative weight of the component within a Row or Column.
     *   May only be set when the component is a direct descendant of a Row or Column. See
     *   [WeightProperty].
     * * `child` (Component ID String, required): The ID of the child component to be rendered
     *   inside the card. Multiple elements must be wrapped in a layout container (e.g. Row or
     *   Column).
     */
    public interface Card : A2uiComponent {
        override val name: String
            get() = "Card"

        override val description: String
            get() = "A layout component that wraps its child content in a styled card container."

        public companion object {
            /** The [A2uiProperty] for the `"accessibility"` property of a [Card]. */
            public val AccessibilityProperty: DynamicA2uiProperty<AccessibilityAttributes> =
                A2uiBasicCatalogV1.AccessibilityProperty

            /** The [A2uiProperty] for the `"child"` property of a [Card]. */
            public val ChildProperty: StaticA2uiProperty<String> =
                A2uiProperty.componentId(
                    key = "child",
                    required = true,
                    description =
                        "The ID of the single child component to be rendered inside the card. To " +
                            "display multiple elements, you MUST wrap them in a layout component " +
                            "(like Column or Row) and pass that container's ID here. Do NOT pass " +
                            "multiple IDs or a non-existent ID.",
                )
            internal val ComponentProperties: kotlin.collections.List<A2uiProperty<*>> =
                listOf(AccessibilityProperty, WeightProperty, ChildProperty)
        }

        override val properties: kotlin.collections.List<A2uiProperty<*>>
            get() = ComponentProperties

        @Composable
        override fun A2uiComponentScope.Content(
            properties: A2uiComponentProperties,
            modifier: Modifier,
        ) {
            val childId =
                checkNotNull(properties[ChildProperty]) {
                    "Required property '${ChildProperty.key}' is missing."
                }
            val accessibility = properties.bind(AccessibilityProperty)
            TypedContent(childId = childId, accessibility = accessibility, modifier = modifier)
        }

        /**
         * Renders the [Card] with its resolved property values.
         *
         * @param childId the ID of the child component inside the card
         * @param accessibility accessibility attributes for the card
         * @param modifier [Modifier] to apply to the layout
         */
        @Composable
        public fun A2uiComponentScope.TypedContent(
            childId: String,
            accessibility: AccessibilityAttributes?,
            modifier: Modifier,
        )
    }

    /**
     * The A2UI `"Tabs"` component for displaying a set of tabs.
     *
     * **Schema Properties:**
     * * `accessibility` (Dynamic Custom, optional): Accessibility attributes for the tabs
     *   container.
     * * `weight` (Number, optional): The relative weight of the component within a Row or Column.
     *   May only be set when the component is a direct descendant of a Row or Column. See
     *   [WeightProperty].
     * * `tabs` (NestedList, required): An array of objects, where each object defines a tab with a
     *   `title` (Dynamic String) and a `child` (ComponentId) component ID.
     */
    public interface Tabs : A2uiComponent {
        override val name: String
            get() = "Tabs"

        override val description: String
            get() = "A set of tabs, each with a title and a corresponding child component."

        /**
         * A resolved tab within a [Tabs] component.
         *
         * @property title the evaluated title shown on the tab
         * @property childId the ID of the component to show while this tab is selected
         */
        @Immutable
        public class Tab(public val title: String, public val childId: String) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Tab) return false
                if (title != other.title) return false
                if (childId != other.childId) return false
                return true
            }

            override fun hashCode(): Int {
                var result = title.hashCode()
                result = 31 * result + childId.hashCode()
                return result
            }

            override fun toString(): String {
                return "Tab(title='$title', childId='$childId')"
            }
        }

        public companion object {
            /** The [A2uiProperty] for the `"accessibility"` property of a [Tabs]. */
            public val AccessibilityProperty: DynamicA2uiProperty<AccessibilityAttributes> =
                A2uiBasicCatalogV1.AccessibilityProperty

            /** The [A2uiProperty] for the `"title"` property of a [Tab]. */
            public val TitleProperty: DynamicA2uiProperty<String> =
                A2uiProperty.dynamicString(
                    key = "title",
                    required = true,
                    description = "The tab title.",
                )

            /** The [A2uiProperty] for the `"child"` property of a [Tab]. */
            public val ChildProperty: StaticA2uiProperty<String> =
                A2uiProperty.componentId(
                    key = "child",
                    required = true,
                    description = "The ID of the child component.",
                )

            /** The [A2uiProperty] for the `"tabs"` property of a [Tabs]. */
            public val TabsProperty:
                StaticA2uiProperty<kotlin.collections.List<A2uiComponentProperties>> =
                A2uiProperty.nestedList(
                    key = "tabs",
                    properties = listOf(TitleProperty, ChildProperty),
                    required = true,
                    description =
                        "An array of objects, where each object defines a tab with a title and a " +
                            "child component.",
                    minItems = 1,
                    isAdditionalPropertiesAllowed = false,
                )

            internal val ComponentProperties: kotlin.collections.List<A2uiProperty<*>> =
                listOf(AccessibilityProperty, WeightProperty, TabsProperty)
        }

        override val properties: kotlin.collections.List<A2uiProperty<*>>
            get() = ComponentProperties

        @Composable
        override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean {
            val tabsList =
                checkNotNull(properties[TabsProperty]) {
                    "Required property '${TabsProperty.key}' is missing."
                }
            return tabsList.fastAll { tabProps -> tabProps.bind(TitleProperty) != null }
        }

        @Composable
        override fun A2uiComponentScope.Content(
            properties: A2uiComponentProperties,
            modifier: Modifier,
        ) {
            val tabsList =
                checkNotNull(properties[TabsProperty]) {
                    "Required property '${TabsProperty.key}' is missing."
                }

            val resolvedTabs = ArrayList<Tab>(tabsList.size)
            for (i in tabsList.indices) {
                val tabProps = tabsList[i]
                val title =
                    checkNotNull(tabProps.bind(TitleProperty)) {
                        "Required property '${TitleProperty.key}' is missing."
                    }
                val childId =
                    checkNotNull(tabProps[ChildProperty]) {
                        "Required property '${ChildProperty.key}' is missing."
                    }
                resolvedTabs.add(Tab(title, childId))
            }
            val accessibility = properties.bind(AccessibilityProperty)

            TypedContent(tabs = resolvedTabs, accessibility = accessibility, modifier = modifier)
        }

        /**
         * Renders the [Tabs] with its resolved property values.
         *
         * @param tabs list of [Tab] objects to render
         * @param accessibility accessibility attributes for the tabs container
         * @param modifier [Modifier] to apply to the layout
         */
        @Composable
        public fun A2uiComponentScope.TypedContent(
            tabs: kotlin.collections.List<Tab>,
            accessibility: AccessibilityAttributes?,
            modifier: Modifier,
        )
    }

    /**
     * The A2UI `"Modal"` component for displaying a dialog window.
     *
     * **Schema Properties:**
     * * `accessibility` (Dynamic Custom, optional): Accessibility attributes for the modal.
     * * `weight` (Number, optional): The relative weight of the component within a Row or Column.
     *   May only be set when the component is a direct descendant of a Row or Column. See
     *   [WeightProperty].
     * * `trigger` (Component ID String, required): The ID of the component that opens the modal
     *   when interacted with.
     * * `content` (Component ID String, required): The ID of the component to be displayed inside
     *   the modal.
     */
    public interface Modal : A2uiComponent {
        override val name: String
            get() = "Modal"

        override val description: String
            get() = "A dialog window."

        public companion object {
            /** The [A2uiProperty] for the `"accessibility"` property of a [Modal]. */
            public val AccessibilityProperty: DynamicA2uiProperty<AccessibilityAttributes> =
                A2uiBasicCatalogV1.AccessibilityProperty

            /** The [A2uiProperty] for the `"trigger"` property of a [Modal]. */
            public val TriggerProperty: StaticA2uiProperty<String> =
                A2uiProperty.componentId(
                    key = "trigger",
                    required = true,
                    description =
                        "The ID of the component that opens the modal when interacted " +
                            "with (e.g., a button).",
                )

            /** The [A2uiProperty] for the `"content"` property of a [Modal]. */
            public val ContentProperty: StaticA2uiProperty<String> =
                A2uiProperty.componentId(
                    key = "content",
                    required = true,
                    description = "The ID of the component to be displayed inside the modal.",
                )

            internal val ComponentProperties: kotlin.collections.List<A2uiProperty<*>> =
                listOf(AccessibilityProperty, WeightProperty, TriggerProperty, ContentProperty)
        }

        override val properties: kotlin.collections.List<A2uiProperty<*>>
            get() = ComponentProperties

        @Composable
        override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean = true

        @Composable
        override fun A2uiComponentScope.Content(
            properties: A2uiComponentProperties,
            modifier: Modifier,
        ) {
            val triggerId =
                checkNotNull(properties[TriggerProperty]) {
                    "Required property '${TriggerProperty.key}' is missing."
                }
            val contentId =
                checkNotNull(properties[ContentProperty]) {
                    "Required property '${ContentProperty.key}' is missing."
                }
            val accessibility = properties.bind(AccessibilityProperty)

            TypedContent(
                triggerId = triggerId,
                contentId = contentId,
                accessibility = accessibility,
                modifier = modifier,
            )
        }

        /**
         * Renders the [Modal] with its resolved property values.
         *
         * @param triggerId the ID of the component that opens the modal when interacted with
         * @param contentId the ID of the component to be displayed inside the modal
         * @param accessibility accessibility attributes for the modal
         * @param modifier [Modifier] to apply to the layout
         */
        @Composable
        public fun A2uiComponentScope.TypedContent(
            triggerId: String,
            contentId: String,
            accessibility: AccessibilityAttributes?,
            modifier: Modifier,
        )
    }

    /**
     * The A2UI `"Divider"` component for displaying a horizontal or vertical dividing line.
     *
     * **Schema Properties:**
     * * `accessibility` (Dynamic Custom, optional): Accessibility attributes for the divider.
     * * `weight` (Number, optional): The relative weight of the component within a Row or Column.
     *   May only be set when the component is a direct descendant of a Row or Column. See
     *   [WeightProperty].
     * * `axis` (String Enum, optional): The orientation of the divider. Valid options:
     *   `"horizontal"`, `"vertical"`. Defaults to `"horizontal"`.
     */
    public interface Divider : A2uiComponent {
        override val name: String
            get() = "Divider"

        override val description: String
            get() = "A horizontal or vertical dividing line."

        /** Orientation of a [Divider]. */
        public enum class Axis(public val value: String) {
            Horizontal("horizontal"),
            Vertical("vertical");

            public companion object {
                /** The default [Axis] when unspecified in the component payload. */
                public val Default: Axis = Horizontal

                /** Returns the [Axis] matching [value], or [Default] if unknown. */
                public fun fromValue(value: String): Axis =
                    entries.fastFirstOrNull { it.value == value } ?: Default
            }
        }

        public companion object {
            /** The [A2uiProperty] for the `"accessibility"` property of a [Divider]. */
            public val AccessibilityProperty: DynamicA2uiProperty<AccessibilityAttributes> =
                A2uiBasicCatalogV1.AccessibilityProperty

            /** The [A2uiProperty] for the `"axis"` property of a [Divider]. */
            public val AxisProperty: StaticA2uiProperty<Axis> =
                A2uiProperty.enum(
                    key = "axis",
                    enumValues = Axis.entries,
                    mapToString = { it.value },
                    convertFromString = Axis::fromValue,
                    defaultValue = Axis.Default,
                    description = "The orientation of the divider.",
                )

            internal val ComponentProperties: kotlin.collections.List<A2uiProperty<*>> =
                listOf(AccessibilityProperty, WeightProperty, AxisProperty)
        }

        override val properties: kotlin.collections.List<A2uiProperty<*>>
            get() = ComponentProperties

        @Composable
        override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean = true

        @Composable
        override fun A2uiComponentScope.Content(
            properties: A2uiComponentProperties,
            modifier: Modifier,
        ) {
            val axis = properties[AxisProperty] ?: Axis.Default
            val accessibility = properties.bind(AccessibilityProperty)
            TypedContent(axis = axis, accessibility = accessibility, modifier = modifier)
        }

        /**
         * Renders the [Divider] with its resolved property values.
         *
         * @param axis [Axis] orientation of the divider
         * @param accessibility accessibility attributes for the divider
         * @param modifier [Modifier] to apply to the layout
         */
        @Composable
        public fun A2uiComponentScope.TypedContent(
            axis: Axis,
            accessibility: AccessibilityAttributes?,
            modifier: Modifier,
        )
    }

    /**
     * The A2UI `"Button"` component for clickable buttons that dispatch actions.
     *
     * **Schema Properties:**
     * * `accessibility` (Dynamic Custom, optional): Accessibility attributes for the button.
     * * `weight` (Number, optional): The relative weight of the component within a Row or Column.
     *   May only be set when the component is a direct descendant of a Row or Column. See
     *   [WeightProperty].
     * * `checks` (Dynamic Custom, optional): Client-side check validation rules for the button. See
     *   [CheckRule].
     * * `child` (Component ID String, required): The ID of the child component inside the button
     *   (e.g., a `"Text"` or `"Icon"` component).
     * * `variant` (String Enum, optional): A hint for the button style. This is a static
     *   configuration and does not support dynamic data bindings. Valid options: `"default"`,
     *   `"primary"`, `"borderless"`. Defaults to `"default"`.
     * * `action` (Action Object, required): The action payload dispatched when the button is
     *   clicked.
     */
    public interface Button : A2uiComponent {
        override val name: String
            get() = "Button"

        override val description: String
            get() = "A clickable button that dispatches an action."

        /** Visual style variant for a [Button]. */
        public enum class Variant(public val value: String) {
            Secondary("default"),
            Primary("primary"),
            Borderless("borderless");

            public companion object {
                /** The default [Variant] when unspecified in the component payload. */
                public val Default: Variant = Secondary

                /** Returns the [Variant] matching [value], or [Default] if unknown. */
                public fun fromValue(value: String): Variant =
                    entries.fastFirstOrNull { it.value == value } ?: Default
            }
        }

        public companion object {
            /** The [A2uiProperty] for the `"accessibility"` property of a [Button]. */
            public val AccessibilityProperty: DynamicA2uiProperty<AccessibilityAttributes> =
                A2uiBasicCatalogV1.AccessibilityProperty

            /** The [A2uiProperty] for the `"checks"` property of a [Button]. See [CheckRule]. */
            public val ChecksProperty: DynamicA2uiProperty<kotlin.collections.List<CheckRule>> =
                A2uiBasicCatalogV1.ChecksProperty

            /** The [A2uiProperty] for the `"child"` property of a [Button]. */
            public val ChildProperty: StaticA2uiProperty<String> =
                A2uiProperty.componentId(
                    key = "child",
                    required = true,
                    description =
                        "The ID of the child component. Use a 'Text' component for a labeled " +
                            "button. Only use an 'Icon' if the requirements explicitly ask for " +
                            "an icon-only button.",
                )

            /** The [A2uiProperty] for the `"variant"` property of a [Button]. */
            public val VariantProperty: StaticA2uiProperty<Variant> =
                A2uiProperty.enum(
                    key = "variant",
                    enumValues = Variant.entries,
                    mapToString = { it.value },
                    convertFromString = Variant::fromValue,
                    defaultValue = Variant.Default,
                    description =
                        "A hint for the button style. If omitted, a default button style is " +
                            "used. 'primary' indicates this is the main call-to-action button. " +
                            "'borderless' means the button has no visual border or background, " +
                            "making its child content appear like a clickable link.",
                )

            /** The [A2uiProperty] for the `"action"` property of a [Button]. */
            public val ActionProperty: StaticA2uiProperty<Map<String, Any?>> =
                A2uiProperty.action(key = "action", required = true)

            internal val ComponentProperties: kotlin.collections.List<A2uiProperty<*>> =
                listOf(
                    AccessibilityProperty,
                    WeightProperty,
                    ChecksProperty,
                    ChildProperty,
                    VariantProperty,
                    ActionProperty,
                )
        }

        override val properties: kotlin.collections.List<A2uiProperty<*>>
            get() = ComponentProperties

        @Composable
        override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
            bindChecks(properties) != null

        @Composable
        override fun A2uiComponentScope.Content(
            properties: A2uiComponentProperties,
            modifier: Modifier,
        ) {
            val childId =
                checkNotNull(properties[ChildProperty]) {
                    "Required property '${ChildProperty.key}' is missing."
                }
            val variant = properties[VariantProperty] ?: Variant.Default
            val action =
                checkNotNull(properties[ActionProperty]) {
                    "Required property '${ActionProperty.key}' is missing."
                }
            val accessibility = properties.bind(AccessibilityProperty)
            val checks =
                checkNotNull(bindChecks(properties)) {
                    "Property '${ChecksProperty.key}' could not be resolved."
                }

            TypedContent(
                childId = childId,
                variant = variant,
                action = action,
                accessibility = accessibility,
                checks = checks,
                modifier = modifier,
            )
        }

        /**
         * Renders the [Button] with its resolved property values.
         *
         * @param childId ID of the child component to render inside this button
         * @param variant [Variant] visual style variant of the button
         * @param action action payload [Map] dispatched when clicked
         * @param accessibility accessibility attributes for the button
         * @param checks validation rules for this button. When any [CheckRule.condition] is false,
         *   the button implementation should indicate an error state or disable action dispatch.
         *   See [CheckRule].
         * @param modifier [Modifier] to apply to the layout
         */
        @Composable
        public fun A2uiComponentScope.TypedContent(
            childId: String,
            variant: Variant,
            action: Map<String, Any?>,
            accessibility: AccessibilityAttributes?,
            checks: kotlin.collections.List<CheckRule>,
            modifier: Modifier,
        )
    }

    /**
     * The A2UI `"TextField"` component for user text input.
     *
     * **Schema Properties:**
     * * `accessibility` (Dynamic Custom, optional): Accessibility attributes for the text field.
     * * `weight` (Number, optional): The relative weight of the component within a Row or Column.
     *   May only be set when the component is a direct descendant of a Row or Column. See
     *   [WeightProperty].
     * * `checks` (Dynamic Custom, optional): Client-side check validation rules for the text field.
     *   See [CheckRule].
     * * `label` (Dynamic String, required): The text label for the input field.
     * * `value` (Dynamic String, optional): The value of the text field.
     * * `variant` (String Enum, optional): The type of input field to display. Valid options:
     *   `"longText"`, `"number"`, `"shortText"`, `"obscured"`. Defaults to `"shortText"`.
     * * `validationRegexp` (String, optional): A regular expression used for client-side validation
     *   of the input.
     */
    public interface TextField : A2uiComponent {
        override val name: String
            get() = "TextField"

        override val description: String
            get() = "A field for user text input."

        /** Input style variant for a [TextField]. */
        public enum class Variant(public val value: String) {
            LongText("longText"),
            Number("number"),
            ShortText("shortText"),
            Obscured("obscured");

            public companion object {
                /** The default [Variant] when unspecified in the component payload. */
                public val Default: Variant = ShortText

                /** Returns the [Variant] matching [value], or [Default] if unknown. */
                public fun fromValue(value: String): Variant =
                    entries.fastFirstOrNull { it.value == value } ?: Default
            }
        }

        public companion object {
            /** The [A2uiProperty] for the `"accessibility"` property of a [TextField]. */
            public val AccessibilityProperty: DynamicA2uiProperty<AccessibilityAttributes> =
                A2uiBasicCatalogV1.AccessibilityProperty

            /** The [A2uiProperty] for the `"checks"` property of a [TextField]. See [CheckRule]. */
            public val ChecksProperty: DynamicA2uiProperty<kotlin.collections.List<CheckRule>> =
                A2uiBasicCatalogV1.ChecksProperty

            /** The [A2uiProperty] for the `"label"` property of a [TextField]. */
            public val LabelProperty: DynamicA2uiProperty<String> =
                A2uiProperty.dynamicString(
                    key = "label",
                    required = true,
                    description = "The text label for the input field.",
                )

            /** The [A2uiProperty] for the `"value"` property of a [TextField]. */
            public val ValueProperty: DynamicA2uiProperty<String> =
                A2uiProperty.dynamicString(
                    key = "value",
                    required = false,
                    description = "The value of the text field.",
                )

            /** The [A2uiProperty] for the `"variant"` property of a [TextField]. */
            public val VariantProperty: StaticA2uiProperty<Variant> =
                A2uiProperty.enum(
                    key = "variant",
                    enumValues = Variant.entries,
                    mapToString = { it.value },
                    convertFromString = Variant::fromValue,
                    defaultValue = Variant.Default,
                    description = "The type of input field to display.",
                )

            /** The [A2uiProperty] for the `"validationRegexp"` property of a [TextField]. */
            public val ValidationRegexpProperty: StaticA2uiProperty<String> =
                A2uiProperty.string(
                    key = "validationRegexp",
                    required = false,
                    description =
                        "A regular expression used for client-side validation of the input.",
                )

            internal val ComponentProperties: kotlin.collections.List<A2uiProperty<*>> =
                listOf(
                    AccessibilityProperty,
                    WeightProperty,
                    ChecksProperty,
                    LabelProperty,
                    ValueProperty,
                    VariantProperty,
                    ValidationRegexpProperty,
                )
        }

        override val properties: kotlin.collections.List<A2uiProperty<*>>
            get() = ComponentProperties

        @Composable
        override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
            properties.bind(LabelProperty) != null && bindChecks(properties) != null

        @Composable
        override fun A2uiComponentScope.Content(
            properties: A2uiComponentProperties,
            modifier: Modifier,
        ) {
            val label =
                checkNotNull(properties.bind(LabelProperty)) {
                    "Required property '${LabelProperty.key}' is missing."
                }
            val value = properties.bind(ValueProperty)
            val variant = properties[VariantProperty] ?: Variant.Default
            val validationRegexp = properties[ValidationRegexpProperty]
            val onValueChange = properties.bindUpdater(ValueProperty)
            val isEnabled = onValueChange != null
            val accessibility = properties.bind(AccessibilityProperty)
            val checks =
                checkNotNull(bindChecks(properties)) {
                    "Property '${ChecksProperty.key}' could not be resolved."
                }

            TypedContent(
                label = label,
                value = value,
                variant = variant,
                validationRegexp = validationRegexp,
                onValueChange = onValueChange ?: {},
                enabled = isEnabled,
                accessibility = accessibility,
                checks = checks,
                modifier = modifier,
            )
        }

        /**
         * Renders the [TextField] with its resolved property values.
         *
         * @param label the text label for the input field
         * @param value the current text value of the field, or `null` if not set
         * @param variant [Variant] input style of the text field
         * @param validationRegexp an optional regex pattern for client-side input validation, or
         *   `null` if none
         * @param onValueChange callback invoked when the user updates the text in the field
         * @param enabled controls the enabled state of the text field. When `false`, this component
         *   will not respond to user input.
         * @param accessibility accessibility attributes for the text field
         * @param checks validation rules for this text field. When any [CheckRule.condition] is
         *   false, the text field should display an error state and show the failing
         *   [CheckRule.message]. See [CheckRule].
         * @param modifier [Modifier] to apply to the layout
         */
        @Composable
        public fun A2uiComponentScope.TypedContent(
            label: String,
            value: String?,
            variant: Variant,
            validationRegexp: String?,
            onValueChange: (String) -> Unit,
            enabled: Boolean,
            accessibility: AccessibilityAttributes?,
            checks: kotlin.collections.List<CheckRule>,
            modifier: Modifier,
        )
    }

    /**
     * The A2UI `"CheckBox"` component for displaying a checkable control with an associated label.
     *
     * **Schema Properties:**
     * * `accessibility` (Dynamic Custom, optional): Accessibility attributes for the checkbox.
     * * `weight` (Number, optional): The relative weight of the component within a Row or Column.
     *   May only be set when the component is a direct descendant of a Row or Column. See
     *   [WeightProperty].
     * * `checks` (Dynamic Custom, optional): Client-side check validation rules for the checkbox.
     *   See [CheckRule].
     * * `label` (Dynamic String, required): The text to display next to the checkbox.
     * * `value` (Dynamic Boolean, required): The current state of the checkbox (true for checked,
     *   false for unchecked).
     */
    public interface CheckBox : A2uiComponent {
        override val name: String
            get() = "CheckBox"

        override val description: String
            get() = "A checkbox with a label and a boolean value."

        public companion object {
            /** The [A2uiProperty] for the `"accessibility"` property of a [CheckBox]. */
            public val AccessibilityProperty: DynamicA2uiProperty<AccessibilityAttributes> =
                A2uiBasicCatalogV1.AccessibilityProperty

            /** The [A2uiProperty] for the `"checks"` property of a [CheckBox]. See [CheckRule]. */
            public val ChecksProperty: DynamicA2uiProperty<kotlin.collections.List<CheckRule>> =
                A2uiBasicCatalogV1.ChecksProperty

            /** The [A2uiProperty] for the `"label"` property of a [CheckBox]. */
            public val LabelProperty: DynamicA2uiProperty<String> =
                A2uiProperty.dynamicString(
                    key = "label",
                    required = true,
                    description = "The text to display next to the checkbox.",
                )

            /** The [A2uiProperty] for the `"value"` property of a [CheckBox]. */
            public val ValueProperty: DynamicA2uiProperty<Boolean> =
                A2uiProperty.dynamicBoolean(
                    key = "value",
                    required = true,
                    description =
                        "The current state of the checkbox (true for checked, false for unchecked).",
                )

            internal val ComponentProperties: kotlin.collections.List<A2uiProperty<*>> =
                listOf(
                    AccessibilityProperty,
                    WeightProperty,
                    ChecksProperty,
                    LabelProperty,
                    ValueProperty,
                )
        }

        override val properties: kotlin.collections.List<A2uiProperty<*>>
            get() = ComponentProperties

        @Composable
        override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
            properties.bind(LabelProperty) != null &&
                properties.bind(ValueProperty) != null &&
                bindChecks(properties) != null

        @Composable
        override fun A2uiComponentScope.Content(
            properties: A2uiComponentProperties,
            modifier: Modifier,
        ) {
            val label =
                checkNotNull(properties.bind(LabelProperty)) {
                    "Required property '${LabelProperty.key}' is missing."
                }
            val value =
                checkNotNull(properties.bind(ValueProperty)) {
                    "Required property '${ValueProperty.key}' is missing."
                }
            val onValueChange = properties.bindUpdater(ValueProperty)
            val isEnabled = onValueChange != null
            val accessibility = properties.bind(AccessibilityProperty)
            val checks =
                checkNotNull(bindChecks(properties)) {
                    "Property '${ChecksProperty.key}' could not be resolved."
                }

            TypedContent(
                label = label,
                value = value,
                onValueChange = { newValue -> onValueChange?.invoke(newValue) },
                enabled = isEnabled,
                accessibility = accessibility,
                checks = checks,
                modifier = modifier,
            )
        }

        /**
         * Renders the [CheckBox] with its resolved property values.
         *
         * @param label the text to display next to the checkbox
         * @param value the current state of the checkbox
         * @param onValueChange callback invoked when the user toggles the checkbox
         * @param enabled controls the enabled state of the checkbox. When `false`, this component
         *   will not respond to user input.
         * @param accessibility accessibility attributes for the checkbox
         * @param checks validation rules for this checkbox. When any [CheckRule.condition] is
         *   false, the checkbox should indicate an error state. See [CheckRule].
         * @param modifier [Modifier] to apply to the layout
         */
        @Composable
        public fun A2uiComponentScope.TypedContent(
            label: String,
            value: Boolean,
            onValueChange: (Boolean) -> Unit,
            enabled: Boolean,
            accessibility: AccessibilityAttributes?,
            checks: kotlin.collections.List<CheckRule>,
            modifier: Modifier,
        )
    }

    /**
     * The A2UI `"ChoicePicker"` component for selecting one or more options from a list.
     *
     * **Schema Properties:**
     * * `accessibility` (Dynamic Custom, optional): Accessibility attributes for the choice picker.
     * * `weight` (Number, optional): The relative weight of the component within a Row or Column.
     *   May only be set when the component is a direct descendant of a Row or Column. See
     *   [WeightProperty].
     * * `checks` (Dynamic Custom, optional): Client-side check validation rules for the choice
     *   picker. See [CheckRule].
     * * `label` (Dynamic String, optional): The label for the group of options.
     * * `variant` (String Enum, optional): A hint for how the choice picker should be displayed and
     *   behave. Valid options: `"mutuallyExclusive"`, `"multipleSelection"`. Defaults to
     *   `"mutuallyExclusive"`.
     * * `options` (NestedList, required): The list of available options to choose from. Each item
     *   defines `label` (Dynamic String, required) and `value` (String, required).
     * * `value` (Dynamic String List, required): The list of currently selected values. This should
     *   be bound to a string array in the data model.
     * * `displayStyle` (String Enum, optional): The display style of the component. Valid options:
     *   `"checkbox"`, `"chips"`. Defaults to `"checkbox"`.
     * * `filterable` (Boolean, optional): If true, displays a search input to filter the options.
     *   Defaults to `false`.
     */
    public interface ChoicePicker : A2uiComponent {
        override val name: String
            get() = "ChoicePicker"

        override val description: String
            get() = "A component that allows selecting one or more options from a list."

        /** Selection behavior variant for a [ChoicePicker]. */
        public enum class Variant(public val value: String) {
            MutuallyExclusive("mutuallyExclusive"),
            MultipleSelection("multipleSelection");

            public companion object {
                /** The default [Variant] when unspecified in the component payload. */
                public val Default: Variant = MutuallyExclusive

                /** Returns the [Variant] matching [value], or [Default] if unknown. */
                public fun fromValue(value: String): Variant =
                    entries.fastFirstOrNull { it.value == value } ?: Default
            }
        }

        /** Display style for a [ChoicePicker]. */
        public enum class DisplayStyle(public val value: String) {
            Checkbox("checkbox"),
            Chips("chips");

            public companion object {
                /** The default [DisplayStyle] when unspecified in the component payload. */
                public val Default: DisplayStyle = Checkbox

                /** Returns the [DisplayStyle] matching [value], or [Default] if unknown. */
                public fun fromValue(value: String): DisplayStyle =
                    entries.fastFirstOrNull { it.value == value } ?: Default
            }
        }

        /**
         * A selectable option within a [ChoicePicker] component.
         *
         * @property label the evaluated text shown for the option
         * @property value the stable value reported when the option is selected
         */
        @Immutable
        public class Option(public val label: String, public val value: String) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Option) return false
                if (label != other.label) return false
                if (value != other.value) return false
                return true
            }

            override fun hashCode(): Int {
                var result = label.hashCode()
                result = 31 * result + value.hashCode()
                return result
            }

            override fun toString(): String {
                return "Option(label='$label', value='$value')"
            }
        }

        public companion object {
            /** The [A2uiProperty] for the `"accessibility"` property of a [ChoicePicker]. */
            public val AccessibilityProperty: DynamicA2uiProperty<AccessibilityAttributes> =
                A2uiBasicCatalogV1.AccessibilityProperty

            /**
             * The [A2uiProperty] for the `"checks"` property of a [ChoicePicker]. See [CheckRule].
             */
            public val ChecksProperty: DynamicA2uiProperty<kotlin.collections.List<CheckRule>> =
                A2uiBasicCatalogV1.ChecksProperty

            /** The [A2uiProperty] for the `"label"` property of a [ChoicePicker]. */
            public val LabelProperty: DynamicA2uiProperty<String> =
                A2uiProperty.dynamicString(
                    key = "label",
                    required = false,
                    description = "The label for the group of options.",
                )

            /** The [A2uiProperty] for the `"variant"` property of a [ChoicePicker]. */
            public val VariantProperty: StaticA2uiProperty<Variant> =
                A2uiProperty.enum(
                    key = "variant",
                    enumValues = Variant.entries,
                    mapToString = { it.value },
                    convertFromString = Variant::fromValue,
                    defaultValue = Variant.Default,
                    description =
                        "A hint for how the choice picker should be displayed and behave.",
                )

            /** The [A2uiProperty] for the `"options"` property of a [ChoicePicker]. */
            public val OptionsProperty: DynamicA2uiProperty<kotlin.collections.List<Option>> =
                A2uiProperty.dynamicCustom(
                    key = "options",
                    required = true,
                    schema =
                        A2uiArraySchema(
                            items =
                                A2uiObjectSchema(
                                    properties =
                                        mapOf(
                                            "label" to
                                                A2uiDynamicStringSchema(
                                                    description =
                                                        "The text to display for this option."
                                                ),
                                            "value" to
                                                A2uiStringSchema(
                                                    description =
                                                        "The stable value associated with " +
                                                            "this option."
                                                ),
                                        ),
                                    required = setOf("label", "value"),
                                    isAdditionalPropertiesAllowed = false,
                                ),
                            description = "The list of available options to choose from.",
                        ),
                    safeCast = { value ->
                        val list = value as? kotlin.collections.List<*> ?: return@dynamicCustom null
                        buildList(list.size) {
                            for (i in list.indices) {
                                when (val item = list[i]) {
                                    is Option -> add(item)
                                    is Map<*, *> -> {
                                        val label =
                                            item["label"]?.toString() ?: return@dynamicCustom null
                                        val optionValue =
                                            item["value"]?.toString() ?: return@dynamicCustom null
                                        add(Option(label = label, value = optionValue))
                                    }
                                    else -> return@dynamicCustom null
                                }
                            }
                        }
                    },
                )

            /** The [A2uiProperty] for the `"value"` property of a [ChoicePicker]. */
            public val ValueProperty: DynamicA2uiProperty<kotlin.collections.List<String>> =
                A2uiProperty.dynamicStringList(
                    key = "value",
                    required = true,
                    description =
                        "The list of currently selected values. This should be bound to a " +
                            "string array in the data model.",
                )

            /** The [A2uiProperty] for the `"displayStyle"` property of a [ChoicePicker]. */
            public val DisplayStyleProperty: StaticA2uiProperty<DisplayStyle> =
                A2uiProperty.enum(
                    key = "displayStyle",
                    enumValues = DisplayStyle.entries,
                    mapToString = { it.value },
                    convertFromString = DisplayStyle::fromValue,
                    defaultValue = DisplayStyle.Default,
                    description = "The display style of the component.",
                )

            /** The [A2uiProperty] for the `"filterable"` property of a [ChoicePicker]. */
            public val FilterableProperty: StaticA2uiProperty<Boolean> =
                A2uiProperty.booleanWithDefault(
                    key = "filterable",
                    defaultValue = false,
                    description = "If true, displays a search input to filter the options.",
                )

            internal val ComponentProperties: kotlin.collections.List<A2uiProperty<*>> =
                listOf(
                    AccessibilityProperty,
                    WeightProperty,
                    ChecksProperty,
                    LabelProperty,
                    VariantProperty,
                    OptionsProperty,
                    ValueProperty,
                    DisplayStyleProperty,
                    FilterableProperty,
                )
        }

        override val properties: kotlin.collections.List<A2uiProperty<*>>
            get() = ComponentProperties

        @Composable
        override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
            properties.bind(OptionsProperty) != null &&
                properties.bind(ValueProperty) != null &&
                bindChecks(properties) != null

        @Composable
        override fun A2uiComponentScope.Content(
            properties: A2uiComponentProperties,
            modifier: Modifier,
        ) {
            val options =
                checkNotNull(properties.bind(OptionsProperty)) {
                    "Required property '${OptionsProperty.key}' is missing."
                }
            val value =
                checkNotNull(properties.bind(ValueProperty)) {
                    "Required property '${ValueProperty.key}' is missing."
                }
            val onValueChange = properties.bindUpdater(ValueProperty)
            val isEnabled = onValueChange != null

            val label = properties.bind(LabelProperty)
            val variant = properties[VariantProperty] ?: Variant.Default
            val displayStyle = properties[DisplayStyleProperty] ?: DisplayStyle.Default
            val filterable = properties[FilterableProperty] ?: false
            val accessibility = properties.bind(AccessibilityProperty)
            val checks =
                checkNotNull(bindChecks(properties)) {
                    "Property '${ChecksProperty.key}' could not be resolved."
                }

            ReportDuplicateOptions(options)

            TypedContent(
                label = label,
                options = options,
                value = value,
                variant = variant,
                displayStyle = displayStyle,
                filterable = filterable,
                onValueChange = { newValue -> onValueChange?.invoke(newValue) },
                enabled = isEnabled,
                accessibility = accessibility,
                checks = checks,
                modifier = modifier,
            )
        }

        /**
         * Renders the [ChoicePicker] with its resolved property values.
         *
         * @param label the label for the group of options
         * @param options the list of available options to choose from
         * @param value the list of currently selected values
         * @param variant [Variant] selection behavior of the choice picker
         * @param displayStyle [DisplayStyle] display style of the choice picker
         * @param filterable if true, displays a search input to filter the options
         * @param onValueChange callback invoked when user selects or deselects options
         * @param enabled controls the enabled state of the choice picker. When `false`, this
         *   component will not respond to user input.
         * @param accessibility accessibility attributes for the choice picker
         * @param checks validation rules for this choice picker. When any [CheckRule.condition] is
         *   false, the choice picker should indicate an error state. See [CheckRule].
         * @param modifier [Modifier] to apply to the layout
         */
        @Composable
        public fun A2uiComponentScope.TypedContent(
            label: String?,
            options: kotlin.collections.List<Option>,
            value: kotlin.collections.List<String>,
            variant: Variant,
            displayStyle: DisplayStyle,
            filterable: Boolean,
            onValueChange: (kotlin.collections.List<String>) -> Unit,
            enabled: Boolean,
            accessibility: AccessibilityAttributes?,
            checks: kotlin.collections.List<CheckRule>,
            modifier: Modifier,
        )

        @Composable
        private fun A2uiComponentScope.ReportDuplicateOptions(
            options: kotlin.collections.List<Option>
        ) {
            val duplicates =
                remember(options) {
                    val seen = mutableScatterSetOf<String>()
                    val dups = mutableScatterSetOf<String>()
                    for (i in options.indices) {
                        val option = options[i]
                        if (!seen.add(option.value)) {
                            dups.add(option.value)
                        }
                    }
                    dups
                }

            if (duplicates.isNotEmpty()) {
                LaunchedEffect(duplicates) {
                    val duplicatesString = buildString {
                        var first = true
                        duplicates.forEach { duplicate ->
                            if (!first) append(", ")
                            append("'$duplicate'")
                            first = false
                        }
                    }
                    reportError(
                        A2uiRuntimeException(
                            "Duplicate option values [$duplicatesString] found in " +
                                "ChoicePicker options."
                        )
                    )
                }
            }
        }
    }

    /**
     * The A2UI `"Slider"` component for selecting a numeric value within a range.
     *
     * **Schema Properties:**
     * * `accessibility` (Dynamic Custom, optional): Accessibility attributes for the slider.
     * * `weight` (Number, optional): The relative weight of the component within a Row or Column.
     *   May only be set when the component is a direct descendant of a Row or Column. See
     *   [WeightProperty].
     * * `checks` (Dynamic Custom, optional): Client-side check validation rules for the slider. See
     *   [CheckRule].
     * * `label` (Dynamic String, optional): The label for the slider.
     * * `min` (Number, optional): The minimum value of the slider. Defaults to `0`.
     * * `max` (Number, required): The maximum value of the slider.
     * * `value` (Dynamic Number, required): The current value of the slider.
     */
    public interface Slider : A2uiComponent {
        override val name: String
            get() = "Slider"

        override val description: String
            get() = "A slider for selecting a numeric value within a range."

        public companion object {
            /** The [A2uiProperty] for the `"accessibility"` property of a [Slider]. */
            public val AccessibilityProperty: DynamicA2uiProperty<AccessibilityAttributes> =
                A2uiBasicCatalogV1.AccessibilityProperty

            /** The [A2uiProperty] for the `"checks"` property of a [Slider]. See [CheckRule]. */
            public val ChecksProperty: DynamicA2uiProperty<kotlin.collections.List<CheckRule>> =
                A2uiBasicCatalogV1.ChecksProperty

            /** The [A2uiProperty] for the `"label"` property of a [Slider]. */
            public val LabelProperty: DynamicA2uiProperty<String> =
                A2uiProperty.dynamicString(
                    key = "label",
                    required = false,
                    description = "The label for the slider.",
                )

            /** The [A2uiProperty] for the `"min"` property of a [Slider]. */
            public val MinProperty: StaticA2uiProperty<Number> =
                A2uiProperty.number(
                    key = "min",
                    defaultValue = 0,
                    description = "The minimum value of the slider.",
                )

            /** The [A2uiProperty] for the `"max"` property of a [Slider]. */
            public val MaxProperty: StaticA2uiProperty<Number> =
                A2uiProperty.number(
                    key = "max",
                    required = true,
                    description = "The maximum value of the slider.",
                )

            /** The [A2uiProperty] for the `"value"` property of a [Slider]. */
            public val ValueProperty: DynamicA2uiProperty<Number> =
                A2uiProperty.dynamicNumber(
                    key = "value",
                    required = true,
                    description = "The current value of the slider.",
                )

            internal val ComponentProperties: kotlin.collections.List<A2uiProperty<*>> =
                listOf(
                    AccessibilityProperty,
                    WeightProperty,
                    ChecksProperty,
                    LabelProperty,
                    MinProperty,
                    MaxProperty,
                    ValueProperty,
                )
        }

        override val properties: kotlin.collections.List<A2uiProperty<*>>
            get() = ComponentProperties

        @Composable
        override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
            properties.bind(ValueProperty) != null && bindChecks(properties) != null

        @Composable
        override fun A2uiComponentScope.Content(
            properties: A2uiComponentProperties,
            modifier: Modifier,
        ) {
            val label = properties.bind(LabelProperty)
            val min = properties[MinProperty]?.toFloat() ?: 0f
            val max =
                checkNotNull(properties[MaxProperty]?.toFloat()) {
                    "Required property '${MaxProperty.key}' is missing."
                }
            val value =
                checkNotNull(properties.bind(ValueProperty)?.toFloat()) {
                    "Required property '${ValueProperty.key}' is missing."
                }
            val onValueChange = properties.bindUpdater(ValueProperty)
            val isEnabled = onValueChange != null
            val accessibility = properties.bind(AccessibilityProperty)
            val checks =
                checkNotNull(bindChecks(properties)) {
                    "Property '${ChecksProperty.key}' could not be resolved."
                }

            TypedContent(
                label = label,
                min = min,
                max = max,
                value = value,
                onValueChange = { newValue -> onValueChange?.invoke(newValue) },
                enabled = isEnabled,
                accessibility = accessibility,
                checks = checks,
                modifier = modifier,
            )
        }

        /**
         * Renders the [Slider] with its resolved property values.
         *
         * @param label the text label for the slider
         * @param min the minimum value of the slider
         * @param max the maximum value of the slider
         * @param value the current value of the slider
         * @param onValueChange callback invoked when the user interacts with the slider
         * @param enabled controls the enabled state of the slider. When `false`, this component
         *   will not respond to user input.
         * @param accessibility accessibility attributes for the slider
         * @param checks validation rules for this slider. When any [CheckRule.condition] is false,
         *   the slider should indicate an error state. See [CheckRule].
         * @param modifier [Modifier] to apply to the layout
         */
        @Composable
        public fun A2uiComponentScope.TypedContent(
            label: String?,
            min: Float,
            max: Float,
            value: Float,
            onValueChange: (Float) -> Unit,
            enabled: Boolean,
            accessibility: AccessibilityAttributes?,
            checks: kotlin.collections.List<CheckRule>,
            modifier: Modifier,
        )
    }

    /**
     * The A2UI `"DateTimeInput"` component for selecting date and/or time.
     *
     * **Schema Properties:**
     * * `accessibility` (Dynamic Custom, optional): Accessibility attributes for the date/time
     *   input.
     * * `weight` (Number, optional): The relative weight of the component within a Row or Column.
     *   May only be set when the component is a direct descendant of a Row or Column. See
     *   [WeightProperty].
     * * `checks` (Dynamic Custom, optional): Client-side check validation rules for the date/time
     *   input. See [CheckRule].
     * * `value` (Dynamic String, required): The selected date and/or time value in ISO 8601 format.
     *   If not yet set, initialize with an empty string.
     * * `enableDate` (Boolean, optional): If true, allows the user to select a date. Defaults to
     *   `false`.
     * * `enableTime` (Boolean, optional): If true, allows the user to select a time. Defaults to
     *   `false`.
     * * `min` (Dynamic String, optional): The minimum allowed date/time in ISO 8601 format.
     * * `max` (Dynamic String, optional): The maximum allowed date/time in ISO 8601 format.
     * * `label` (Dynamic String, optional): The text label for the component.
     */
    public interface DateTimeInput : A2uiComponent {
        override val name: String
            get() = "DateTimeInput"

        override val description: String
            get() = "Allows the user to select a date and/or time."

        public companion object {
            /** The [A2uiProperty] for the `"accessibility"` property of a [DateTimeInput]. */
            public val AccessibilityProperty: DynamicA2uiProperty<AccessibilityAttributes> =
                A2uiBasicCatalogV1.AccessibilityProperty

            /**
             * The [A2uiProperty] for the `"checks"` property of a [DateTimeInput]. See [CheckRule].
             */
            public val ChecksProperty: DynamicA2uiProperty<kotlin.collections.List<CheckRule>> =
                A2uiBasicCatalogV1.ChecksProperty

            private val DateTimeFormatConstraintSchema =
                A2uiAnySchema(
                    keywords =
                        listOf(
                            A2uiSchemaKeyword.IfThen(
                                ifSchema = A2uiStringSchema.INSTANCE,
                                thenSchema =
                                    A2uiAnySchema(
                                        keywords =
                                            listOf(
                                                A2uiSchemaKeyword.OneOf(
                                                    listOf(
                                                        A2uiAnySchema(
                                                            keywords =
                                                                listOf(
                                                                    A2uiSchemaKeyword.Format("date")
                                                                )
                                                        ),
                                                        A2uiAnySchema(
                                                            keywords =
                                                                listOf(
                                                                    A2uiSchemaKeyword.Format("time")
                                                                )
                                                        ),
                                                        A2uiAnySchema(
                                                            keywords =
                                                                listOf(
                                                                    A2uiSchemaKeyword.Format(
                                                                        "date-time"
                                                                    )
                                                                )
                                                        ),
                                                    )
                                                )
                                            )
                                    ),
                            )
                        )
                )

            /** The [A2uiProperty] for the `"value"` property of a [DateTimeInput]. */
            public val ValueProperty: DynamicA2uiProperty<String> =
                A2uiProperty.dynamicString(
                    key = "value",
                    required = true,
                    description =
                        "The selected date and/or time value in ISO 8601 format. " +
                            "If not yet set, initialize with an empty string.",
                )

            /** The [A2uiProperty] for the `"enableDate"` property of a [DateTimeInput]. */
            public val EnableDateProperty: StaticA2uiProperty<Boolean> =
                A2uiProperty.booleanWithDefault(
                    key = "enableDate",
                    defaultValue = false,
                    description = "If true, allows the user to select a date.",
                )

            /** The [A2uiProperty] for the `"enableTime"` property of a [DateTimeInput]. */
            public val EnableTimeProperty: StaticA2uiProperty<Boolean> =
                A2uiProperty.booleanWithDefault(
                    key = "enableTime",
                    defaultValue = false,
                    description = "If true, allows the user to select a time.",
                )

            /** The [A2uiProperty] for the `"min"` property of a [DateTimeInput]. */
            public val MinProperty: DynamicA2uiProperty<String> =
                A2uiProperty.dynamicCustom(
                    key = "min",
                    schema =
                        A2uiAnySchema(
                            description = "The minimum allowed date/time in ISO 8601 format.",
                            keywords =
                                listOf(
                                    A2uiSchemaKeyword.AllOf(
                                        listOf(
                                            A2uiDynamicStringSchema.DEFAULT_INSTANCE,
                                            DateTimeFormatConstraintSchema,
                                        )
                                    )
                                ),
                        ),
                    safeCast = { it as? String },
                    required = false,
                )

            /** The [A2uiProperty] for the `"max"` property of a [DateTimeInput]. */
            public val MaxProperty: DynamicA2uiProperty<String> =
                A2uiProperty.dynamicCustom(
                    key = "max",
                    schema =
                        A2uiAnySchema(
                            description = "The maximum allowed date/time in ISO 8601 format.",
                            keywords =
                                listOf(
                                    A2uiSchemaKeyword.AllOf(
                                        listOf(
                                            A2uiDynamicStringSchema.DEFAULT_INSTANCE,
                                            DateTimeFormatConstraintSchema,
                                        )
                                    )
                                ),
                        ),
                    safeCast = { it as? String },
                    required = false,
                )

            /** The [A2uiProperty] for the `"label"` property of a [DateTimeInput]. */
            public val LabelProperty: DynamicA2uiProperty<String> =
                A2uiProperty.dynamicString(
                    key = "label",
                    required = false,
                    description = "The text label for the component.",
                )

            internal val ComponentProperties: kotlin.collections.List<A2uiProperty<*>> =
                listOf(
                    AccessibilityProperty,
                    WeightProperty,
                    ChecksProperty,
                    ValueProperty,
                    EnableDateProperty,
                    EnableTimeProperty,
                    MinProperty,
                    MaxProperty,
                    LabelProperty,
                )
        }

        override val properties: kotlin.collections.List<A2uiProperty<*>>
            get() = ComponentProperties

        @Composable
        override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean {
            if (bindChecks(properties) == null) return false
            val value = properties.bind(ValueProperty) ?: return false
            if (value.isEmpty()) return true
            val valueMillis = parseIsoDateTimeToUtcMillis(value)
            if (valueMillis == null) {
                // 当前 Compose 用带键协程在提交后报告错误，避免重组重复发送。
                LaunchedEffect(value) {
                    reportError(
                        A2uiException.A2uiRuntimeException("Invalid date-time format: $value")
                    )
                }
                return false
            }
            return true
        }

        @Composable
        override fun A2uiComponentScope.Content(
            properties: A2uiComponentProperties,
            modifier: Modifier,
        ) {
            val value =
                checkNotNull(properties.bind(ValueProperty)) {
                    "Required property '${ValueProperty.key}' is missing."
                }
            val valueMillis =
                if (value.isNotEmpty()) {
                    checkNotNull(remember(value) { parseIsoDateTimeToUtcMillis(value) }) {
                        "Required property '${ValueProperty.key}' could not be parsed: '$value'."
                    }
                } else {
                    null
                }

            val onValueChange = properties.bindUpdater(ValueProperty)
            val enableDate = properties[EnableDateProperty] ?: false
            val enableTime = properties[EnableTimeProperty] ?: false
            val min = properties.bind(MinProperty)
            val max = properties.bind(MaxProperty)
            val label = properties.bind(LabelProperty)

            val minMillis = remember(min) { parseIsoDateTimeToUtcMillis(min) }
            val maxMillis = remember(max) { parseIsoDateTimeToUtcMillis(max) }

            val onValueChangeMillis: ((Long?) -> Unit)? =
                if (onValueChange != null) {
                    { newMillis ->
                        val formatted = formatUtcMillisToIso(newMillis, enableDate, enableTime)
                        onValueChange(formatted)
                    }
                } else null
            val accessibility = properties.bind(AccessibilityProperty)
            val checks =
                checkNotNull(bindChecks(properties)) {
                    "Property '${ChecksProperty.key}' could not be resolved."
                }

            TypedContent(
                value = valueMillis,
                onValueChange = onValueChangeMillis,
                enableDate = enableDate,
                enableTime = enableTime,
                min = minMillis,
                max = maxMillis,
                label = label,
                accessibility = accessibility,
                checks = checks,
                modifier = modifier,
            )
        }

        /**
         * Renders the [DateTimeInput] with its resolved property values.
         *
         * @param value selected date and/or time in UTC epoch milliseconds, or `null` if not yet
         *   set
         * @param onValueChange callback invoked when the user selects a date or time in UTC epoch
         *   milliseconds, or `null` if the input is read-only
         * @param enableDate whether date selection is enabled
         * @param enableTime whether time selection is enabled
         * @param min minimum allowed date/time in UTC epoch milliseconds, or `null` if unbounded
         * @param max maximum allowed date/time in UTC epoch milliseconds, or `null` if unbounded
         * @param label text label describing the input, or `null` if none
         * @param accessibility accessibility attributes for the date/time input
         * @param checks validation rules for this date/time input. When any [CheckRule.condition]
         *   is false, the input should display an error state and show the failing
         *   [CheckRule.message]. See [CheckRule].
         * @param modifier [Modifier] to apply to the layout
         */
        @Composable
        public fun A2uiComponentScope.TypedContent(
            @Suppress("AutoBoxing") value: Long?,
            onValueChange: ((Long?) -> Unit)?,
            enableDate: Boolean,
            enableTime: Boolean,
            @Suppress("AutoBoxing") min: Long?,
            @Suppress("AutoBoxing") max: Long?,
            label: String?,
            accessibility: AccessibilityAttributes?,
            checks: kotlin.collections.List<CheckRule>,
            modifier: Modifier,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiBasicCatalogV1) return false
        return catalogId == other.catalogId &&
            components == other.components &&
            functions == other.functions &&
            themeSchema == other.themeSchema
    }

    override fun hashCode(): Int {
        var result = catalogId.hashCode()
        result = 31 * result + components.hashCode()
        result = 31 * result + functions.hashCode()
        result = 31 * result + themeSchema.hashCode()
        return result
    }

    override fun toString(): String {
        return "A2uiBasicCatalogV1(" +
            "catalogId=$catalogId, " +
            "components=$components, " +
            "functions=$functions, " +
            "themeSchema=$themeSchema" +
            ")"
    }
}

/**
 * Converts this basic catalog into an [A2uiCatalog] holding the catalog definitions.
 *
 * @param isInline whether this catalog's schema should be serialized inline as part of the
 *   capabilities advertisement sent to the agent
 * @return a fully initialized [A2uiCatalog]
 */
public fun A2uiBasicCatalogV1.toA2uiCatalog(isInline: Boolean = false): A2uiCatalog =
    A2uiCatalog(
        catalogId = catalogId,
        components = components,
        functions = functions,
        themeSchema = themeSchema,
        isInline = isInline,
    )

private val dateTimePatterns =
    arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd",
        "HH:mm:ss.SSSX",
        "HH:mm:ss.SSS",
        "HH:mm:ssX",
        "HH:mm:ss",
        "HH:mm",
    )

internal fun parseIsoDateTimeToUtcMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    val trimmed = value.trim()
    val utcZone = TimeZone.getTimeZone("UTC")

    for (pattern in dateTimePatterns) {
        try {
            val parser =
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = utcZone
                    isLenient = false
                }
            val date = parser.parse(trimmed) ?: continue
            return date.time
        } catch (_: ParseException) {
            // Ignore and try the next pattern
        }
    }
    return null
}

internal fun formatUtcMillisToIso(
    millis: Long?,
    enableDate: Boolean,
    enableTime: Boolean,
): String? {
    if (millis == null) return null
    val utcZone = TimeZone.getTimeZone("UTC")
    val date = Date(millis)
    val pattern =
        when {
            enableDate && !enableTime -> "yyyy-MM-dd"
            !enableDate && enableTime -> "HH:mm:ss"
            else -> "yyyy-MM-dd'T'HH:mm:ss'Z'"
        }
    val formatter = SimpleDateFormat(pattern, Locale.US).apply { timeZone = utcZone }
    return formatter.format(date)
}
