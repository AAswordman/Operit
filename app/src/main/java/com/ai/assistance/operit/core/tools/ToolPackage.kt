package com.ai.assistance.operit.core.tools
 
 import android.content.Context
 import android.os.Build
 import com.ai.assistance.operit.core.tools.javascript.JsToolManager
 import com.ai.assistance.operit.core.tools.packTool.PackageManager
 import com.ai.assistance.operit.data.model.AITool
 import com.ai.assistance.operit.data.model.ToolResult
 import com.ai.assistance.operit.data.model.ToolValidationResult
 import kotlinx.coroutines.flow.Flow
 import kotlinx.serialization.builtins.MapSerializer
 import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
 import kotlinx.serialization.json.buildJsonObject
 import kotlinx.serialization.json.jsonPrimitive
 import kotlinx.serialization.json.put
 import kotlinx.coroutines.runBlocking
 import kotlinx.coroutines.flow.last
 import java.util.Locale
 
 /**
  * Represents a package of tools that can be imported by the AI
  */
 @Serializable(with = LocalizedTextSerializer::class)
 data class LocalizedText(
     val values: Map<String, String>
 ) {
 
     fun resolve(context: Context): String {
         val locale = try {
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                 context.resources.configuration.locales.get(0)
             } else {
                 @Suppress("DEPRECATION")
                 context.resources.configuration.locale
             }
         } catch (_: Exception) {
             Locale.getDefault()
         }
 
         val languageTag = try {
             locale.toLanguageTag()
         } catch (_: Exception) {
             ""
         }
 
         val language = try {
             locale.language
         } catch (_: Exception) {
             ""
         }
 
         val preferredKeys = mutableListOf<String>().apply {
             if (languageTag.isNotBlank()) {
                 add(languageTag)
                 add(languageTag.lowercase())
             }
             if (language.isNotBlank()) {
                 add(language)
                 add(language.lowercase())
             }
             add("default")
             add("en")
             add("zh")
         }
 
         for (key in preferredKeys) {
             val value = values[key] ?: values[key.lowercase()]
             if (value != null) return value
         }
 
         return values.values.firstOrNull().orEmpty()
     }

     fun resolve(preferredLanguage: String): String {
         val normalized = preferredLanguage.trim()
         if (normalized.isEmpty()) {
             return values["default"] ?: values.values.firstOrNull().orEmpty()
         }

         val lower = normalized.lowercase()
         val languageOnlyLower = lower.substringBefore('-')
         val languageOnlyOriginal = normalized.substringBefore('-')

         val preferredKeys = mutableListOf<String>().apply {
             add(normalized)
             add(lower)
             if (languageOnlyOriginal.isNotBlank() && languageOnlyOriginal != normalized) {
                 add(languageOnlyOriginal)
             }
             if (languageOnlyLower.isNotBlank() && languageOnlyLower != lower) {
                 add(languageOnlyLower)
             }
             add("default")
             add("en")
             add("zh")
         }

         for (key in preferredKeys) {
             val value = values[key] ?: values[key.lowercase()]
             if (value != null) return value
         }

         return values.values.firstOrNull().orEmpty()
     }
 
     companion object {
         fun of(value: String): LocalizedText {
             return LocalizedText(mapOf("default" to value))
         }
     }
 }
 
object LocalizedTextSerializer : KSerializer<LocalizedText> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("LocalizedText") {
            element<String>("default", isOptional = true)
        }

    override fun deserialize(decoder: Decoder): LocalizedText {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return LocalizedText.of(decoder.decodeString())

        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> LocalizedText.of(element.content)
            is JsonObject -> {
                val map = element.entries
                    .mapNotNull { (k, v) ->
                        val value =
                            (v as? JsonPrimitive)?.content
                                ?: runCatching { v.jsonPrimitive.content }.getOrNull()
                        if (value == null) null else k to value
                    }
                    .toMap()
                LocalizedText(map)
            }
            else -> LocalizedText.of(element.toString())
        }
    }

    override fun serialize(encoder: Encoder, value: LocalizedText) {
        val onlyDefault = value.values.size == 1 && value.values.containsKey("default")
        if (onlyDefault) {
            encoder.encodeString(value.values.getValue("default"))
            return
        }

        val entries = value.values.entries
        val mapSerializer = MapSerializer(String.serializer(), String.serializer())
        encoder.encodeSerializableValue(mapSerializer, entries.associate { it.key to it.value })
    }
}

object StringOrStringListSerializer : KSerializer<List<String>> {
    private val delegateSerializer = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor = delegateSerializer.descriptor

    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return listOf(decoder.decodeString().trim()).filter { it.isNotBlank() }

        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> listOf(element.content.trim()).filter { it.isNotBlank() }
            is JsonArray -> element.mapNotNull { item ->
                (item as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() }
            }
            else -> throw IllegalArgumentException("Expected string or string array")
        }
    }

    override fun serialize(encoder: Encoder, value: List<String>) {
        val normalized = value.map { it.trim() }.filter { it.isNotBlank() }
        when (normalized.size) {
            0 -> encoder.encodeSerializableValue(delegateSerializer, emptyList())
            1 -> encoder.encodeString(normalized.first())
            else -> encoder.encodeSerializableValue(delegateSerializer, normalized)
        }
    }
}
 
 /**
  * Represents an environment variable declaration for a package
  */
 @Serializable(with = EnvVarSerializer::class)
 data class EnvVar(
     val name: String,
     val description: LocalizedText,
     val required: Boolean = true,
     val defaultValue: String? = null
 )
 
 /**
  * Custom serializer for EnvVar that handles both old format (string) and new format (object)
  * Old format: "GITHUB_TOKEN"
  * New format: { "name": "GITHUB_TOKEN", "description": "...", "required": true, "defaultValue": "..." }
  */
 object EnvVarSerializer : KSerializer<EnvVar> {
     private val delegateSerializer = JsonObject.serializer()
     
     override val descriptor: SerialDescriptor = delegateSerializer.descriptor
     
     override fun deserialize(decoder: Decoder): EnvVar {
         val jsonDecoder = decoder as? JsonDecoder
             ?: throw IllegalArgumentException("EnvVarSerializer can only be used with JSON")
         
         val element = jsonDecoder.decodeJsonElement()
         
         // Handle old format: simple string
         if (element is JsonPrimitive) {
             return EnvVar(
                 name = element.content,
                 description = LocalizedText.of(""),
                 required = true,
                 defaultValue = null
             )
         }
         
         // Handle new format: object
         if (element is JsonObject) {
             val name = element["name"]?.jsonPrimitive?.content
                 ?: throw IllegalArgumentException("EnvVar must have a 'name' field")
             
             val descriptionElement = element["description"]
             val description = if (descriptionElement != null) {
                 val json = Json { ignoreUnknownKeys = true }
                 json.decodeFromString(LocalizedTextSerializer, descriptionElement.toString())
             } else {
                 LocalizedText.of("")
             }
             
             val requiredElement = element["required"]
             val required = if (requiredElement != null) {
                 when (requiredElement) {
                     is JsonPrimitive -> {
                         if (requiredElement.isString) {
                             // Handle string boolean values
                             requiredElement.content.toBooleanStrictOrNull() ?: true
                         } else {
                             // Handle boolean values directly
                             try {
                                 requiredElement.content.toBooleanStrictOrNull() ?: true
                             } catch (e: Exception) {
                                 true
                             }
                         }
                     }
                     else -> true
                 }
             } else {
                 true
             }
             
             val defaultValue = element["defaultValue"]?.jsonPrimitive?.content
             
             return EnvVar(
                 name = name,
                 description = description,
                 required = required,
                 defaultValue = defaultValue
             )
         }
         
         throw IllegalArgumentException("EnvVar must be a string or an object")
     }
     
     override fun serialize(encoder: Encoder, value: EnvVar) {
         // Always serialize in new format
         val jsonObject = buildJsonObject {
             put("name", value.name)
             put("description", Json.encodeToString(LocalizedTextSerializer, value.description).let {
                 Json.parseToJsonElement(it)
             })
             put("required", value.required)
             if (value.defaultValue != null) {
                 put("defaultValue", value.defaultValue)
             }
         }
         encoder.encodeSerializableValue(JsonObject.serializer(), jsonObject)
     }
 }
 
@Serializable
data class ToolPackage(
    val name: String,
    val description: LocalizedText,
    val tools: List<PackageTool>,
    val states: List<ToolPackageState> = emptyList(),
     /**
      * Optional list of environment variable declarations for this package.
      * Each environment variable can be marked as required or optional.
      * If required variables are missing, PackageManager will validate that
      * these variables exist before activating the package.
      *
      * Example format:
      * env: [
      *   {
      *     name: "GITHUB_API_BASE_URL",
      *     description: "GitHub API base URL",
      *     required: true
      *   },
      *   {
      *     name: "MAX_RETRIES",
      *     description: "Maximum number of retry attempts",
      *     required: false,
      *     defaultValue: "3"
      *   }
      * ]
      */
    val env: List<EnvVar> = emptyList(),
    val isBuiltIn: Boolean = false,
    @JsonNames("enabled_by_default")
    val enabledByDefault: Boolean = false,
    @JsonNames("display_name")
    val displayName: LocalizedText = LocalizedText.of(""),
    val category: String = "Other",
    @Serializable(with = StringOrStringListSerializer::class)
    val author: List<String> = emptyList(),
    val version: String = ""
)
 
 @Serializable
 data class ToolPackageState(
     val id: String,
     val condition: String = "true",
     val inheritTools: Boolean = false,
     val excludeTools: List<String> = emptyList(),
     val tools: List<PackageTool> = emptyList()
 )
 
 /**
  * Represents a tool within a package
  */
 @Serializable
 data class PackageTool(
     val name: String,
     val description: LocalizedText,
     val parameters: List<PackageToolParameter>,
     val script: String, // JavaScript or compatible script that defines this tool's behavior (formerly operScript)
     val advice: Boolean = false
 )
 
 /**
  * Represents a parameter for a tool in a package
  */
 @Serializable
 data class PackageToolParameter(
     val name: String,
     val description: LocalizedText,
     val type: String, // e.g., "string", "number", "boolean"
     val required: Boolean = true
 )
 
 /**
  * Executor for package tools
  */
 class PackageToolExecutor(
     private val toolPackage: ToolPackage,
     private val context: Context,
     private val packageManager: PackageManager
 ) : ToolExecutor {
 
     private val jsToolManager = JsToolManager.getInstance(context, packageManager)
 
     override fun invoke(tool: AITool): ToolResult {
         // Parse packageName:toolName pattern
         val parts = tool.name.split(":")
         if (parts.size != 2) {
             return ToolResult(
                 toolName = tool.name,
                 success = false,
                 result = StringResultData(""),
                 error = "Invalid package tool format. Expected 'packageName:toolName'"
             )
         }
 
         val packageName = parts[0]
         val toolName = parts[1]
 
         // Verify this executor is for the right package
         if (packageName != toolPackage.name) {
             return ToolResult(
                 toolName = tool.name,
                 success = false,
                 result = StringResultData(""),
                 error = "Package mismatch: expected ${toolPackage.name}, got $packageName"
             )
         }
 
         // Find the tool in the package
         val packageTool = toolPackage.tools.find { it.name == toolName }
             ?: return ToolResult(
                 toolName = tool.name,
                 success = false,
                 result = StringResultData(""),
                 error = "Tool '$toolName' not found in package '${toolPackage.name}'"
             )
 
         // Execute the script using runBlocking since we can't make this a suspending function
         // without changing the interface. We collect the last result for single-result compatibility.
         return runBlocking {
             jsToolManager.executeScript(packageTool.script, tool).last()
         }
     }
 
     override fun invokeAndStream(tool: AITool): Flow<ToolResult> {
         // Find the tool in the package
         val packageTool = toolPackage.tools.find { it.name.endsWith(tool.name.split(":").last()) }
             ?: error("Tool not found in package for streaming") // Should be validated before
 
         return jsToolManager.executeScript(packageTool.script, tool)
     }
 
     override fun validateParameters(tool: AITool): ToolValidationResult {
         // Parse packageName:toolName pattern
         val parts = tool.name.split(":")
         if (parts.size != 2) {
             return ToolValidationResult(
                 valid = false,
                 errorMessage = "Invalid package tool format. Expected 'packageName:toolName'"
             )
         }
 
         val packageName = parts[0]
         val toolName = parts[1]
 
         // Verify this executor is for the right package
         if (packageName != toolPackage.name) {
             return ToolValidationResult(
                 valid = false,
                 errorMessage = "Package mismatch: expected ${toolPackage.name}, got $packageName"
             )
         }
 
         // Find the tool in the package
         val packageTool = toolPackage.tools.find { it.name == toolName }
             ?: return ToolValidationResult(
                 valid = false,
                 errorMessage = "Tool '$toolName' not found in package '${toolPackage.name}'"
             )
 
         // Validate that all required parameters are present
         val missingParams = packageTool.parameters
             .filter { it.required }
             .map { it.name }
             .filter { paramName -> tool.parameters.none { it.name == paramName } }
 
         if (missingParams.isNotEmpty()) {
             return ToolValidationResult(
                 valid = false,
                 errorMessage = "Missing required parameters: ${missingParams.joinToString(", ")}"
             )
         }
 
         return ToolValidationResult(valid = true)
     }
 
     /**
      * Returns information about the tools available in this package
      */
     fun describePackage(): String {
         val sb = StringBuilder()
         sb.appendLine("Package: ${toolPackage.name}")
         sb.appendLine("Description: ${toolPackage.description.resolve(context)}")
         sb.appendLine("Tools:")
 
         toolPackage.tools.forEach { tool ->
             sb.appendLine("  - ${tool.name}: ${tool.description.resolve(context)}")
             if (tool.parameters.isNotEmpty()) {
                 sb.appendLine("    Parameters:")
                 tool.parameters.forEach { param ->
                     val required = if (param.required) " (required)" else " (optional)"
                     sb.appendLine("      - ${param.name}: ${param.description.resolve(context)} [${param.type}]$required")
                 }
             }
         }
 
         return sb.toString()
     }
 }
