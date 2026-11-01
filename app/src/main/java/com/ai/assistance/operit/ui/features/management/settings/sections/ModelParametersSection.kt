package com.ai.assistance.operit.ui.features.management.settings.sections

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.ai.assistance.operit.ui.features.management.settings.model.ModelWorkspaceMenu
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterCategory
import com.ai.assistance.operit.data.model.ParameterValueType
import com.ai.assistance.operit.data.model.StandardModelParameters
import com.ai.assistance.operit.ui.features.management.settings.ModelConfigSaveCoordinator
import com.ai.assistance.operit.ui.features.management.settings.RegisterModelConfigSaveAction
import com.ai.assistance.operit.ui.features.management.settings.model.ModelWorkspaceSection
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun ModelParametersSection(
        config: ModelConfigData,
        configManager: ModelConfigManager,
        saveCoordinator: ModelConfigSaveCoordinator,
        showNotification: (String, OKToast.Kind) -> Unit,
        thinkingContent: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showAddParameterDialog by remember { mutableStateOf(false) }

    // 参数状态
    var parameters by remember(config.id) { mutableStateOf<List<ModelParameter<*>>>(emptyList()) }
    var initialized by remember(config.id) { mutableStateOf(false) }
    val parameterSaveMutex = remember(config.id) { Mutex() }
    suspend fun persistParameters() {
        parameterSaveMutex.withLock {
            if (initialized) configManager.updateParameters(config.id, parameters)
        }
    }
    // 显式保存覆盖最新参数；切换页签时等待写入，避免组合协程取消导致最后一次修改丢失。
    RegisterModelConfigSaveAction(saveCoordinator, "parameters:${config.id}") { _ ->
        persistParameters()
    }

    // 参数错误状态
    val parameterErrors = remember { mutableStateMapOf<String, String>() }

    // 获取字符串资源
    val maxTokensName = stringResource(R.string.max_tokens_name)
    val maxTokensDescription = stringResource(R.string.max_tokens_description)
    val temperatureName = stringResource(R.string.temperature_name)
    val temperatureDescription = stringResource(R.string.temperature_description)
    val topPName = stringResource(R.string.top_p_name)
    val topPDescription = stringResource(R.string.top_p_description)
    val topKName = stringResource(R.string.top_k_name)
    val topKDescription = stringResource(R.string.top_k_description)
    val presencePenaltyName = stringResource(R.string.presence_penalty_name)
    val presencePenaltyDescription = stringResource(R.string.presence_penalty_description)
    val frequencyPenaltyName = stringResource(R.string.frequency_penalty_name)
    val frequencyPenaltyDescription = stringResource(R.string.frequency_penalty_description)
    val repetitionPenaltyName = stringResource(R.string.repetition_penalty_name)
    val repetitionPenaltyDescription = stringResource(R.string.repetition_penalty_description)
    val parametersSavedText = stringResource(R.string.parameters_saved)
    val parametersResetText = stringResource(R.string.parameters_reset)
    val resetParametersText = stringResource(R.string.reset_parameters)
    val saveParametersText = stringResource(R.string.save_parameters)
    val valueText = stringResource(R.string.value)
    val rangeFormatText = stringResource(R.string.range_format)

    // 自定义参数相关字符串资源
    val customParameterAddedText = stringResource(R.string.custom_parameter_added)
    val addCustomParameterFailedText = stringResource(R.string.add_custom_parameter_failed)
    val addCustomParameterText = stringResource(R.string.add_custom_parameter)
    val customParametersSectionText = stringResource(R.string.custom_parameters_section)
    val customParameterUpdatedText = stringResource(R.string.custom_parameter_updated)

    // 初始化参数
    LaunchedEffect(config.id) {
        val context = configManager.appContext

        val standardParams =
            StandardModelParameters.DEFINITIONS.map { definition ->
                val name =
                    if (definition.nameResId != 0) context.getString(definition.nameResId)
                    else definition.name
                val description =
                    if (definition.descriptionResId != 0) context.getString(definition.descriptionResId)
                    else definition.description

                val (currentValue, isEnabled) =
                    when (definition.id) {
                        "max_tokens" -> config.maxTokens to config.maxTokensEnabled
                        "temperature" -> config.temperature to config.temperatureEnabled
                        "top_p" -> config.topP to config.topPEnabled
                        "top_k" -> config.topK to config.topKEnabled
                        "presence_penalty" -> config.presencePenalty to config.presencePenaltyEnabled
                        "frequency_penalty" ->
                            config.frequencyPenalty to config.frequencyPenaltyEnabled
                        "repetition_penalty" ->
                            config.repetitionPenalty to config.repetitionPenaltyEnabled
                        else -> definition.defaultValue to false
                    }

                @Suppress("UNCHECKED_CAST")
                ModelParameter(
                    id = definition.id,
                    name = name,
                    apiName = definition.apiName,
                    description = description,
                    defaultValue = definition.defaultValue as Any,
                    currentValue = currentValue as Any,
                    isEnabled = isEnabled,
                    valueType = definition.valueType,
                    minValue = definition.minValue as Any?,
                    maxValue = definition.maxValue as Any?,
                    category = definition.category
                ) as ModelParameter<*>
            }

        val paramList = standardParams.toMutableList()

        // 添加自定义参数
        if (config.hasCustomParameters &&
            config.customParameters.isNotBlank() &&
            config.customParameters != "[]"
        ) {
            try {
                val json = Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
                val customParamsList =
                    json.decodeFromString<List<com.ai.assistance.operit.data.model.CustomParameterData>>(
                        config.customParameters
                    )
                for (customParam in customParamsList) {
                    val param =
                        when (ParameterValueType.valueOf(customParam.valueType)) {
                            ParameterValueType.INT -> {
                ModelParameter(
                                    id = customParam.id,
                                    name = customParam.name,
                                    apiName = customParam.apiName,
                                    description = customParam.description,
                                    defaultValue = customParam.defaultValue.toInt(),
                                    currentValue = customParam.currentValue.toInt(),
                                    isEnabled = customParam.isEnabled,
                        valueType = ParameterValueType.INT,
                                    minValue = customParam.minValue?.toInt(),
                                    maxValue = customParam.maxValue?.toInt(),
                                    category =
                                    ParameterCategory.valueOf(customParam.category),
                                    isCustom = true
                                )
                            }

                            ParameterValueType.FLOAT -> {
                ModelParameter(
                                    id = customParam.id,
                                    name = customParam.name,
                                    apiName = customParam.apiName,
                                    description = customParam.description,
                                    defaultValue = customParam.defaultValue.toFloat(),
                                    currentValue = customParam.currentValue.toFloat(),
                                    isEnabled = customParam.isEnabled,
                        valueType = ParameterValueType.FLOAT,
                                    minValue = customParam.minValue?.toFloat(),
                                    maxValue = customParam.maxValue?.toFloat(),
                                    category =
                                    ParameterCategory.valueOf(customParam.category),
                                    isCustom = true
                                )
                            }

                            ParameterValueType.STRING -> {
                ModelParameter(
                                    id = customParam.id,
                                    name = customParam.name,
                                    apiName = customParam.apiName,
                                    description = customParam.description,
                                    defaultValue = customParam.defaultValue,
                                    currentValue = customParam.currentValue,
                                    isEnabled = customParam.isEnabled,
                                    valueType = ParameterValueType.STRING,
                                    category =
                                    ParameterCategory.valueOf(customParam.category),
                                    isCustom = true
                                )
                            }

                            ParameterValueType.BOOLEAN -> {
                ModelParameter(
                                    id = customParam.id,
                                    name = customParam.name,
                                    apiName = customParam.apiName,
                                    description = customParam.description,
                                    defaultValue = customParam.defaultValue.toBoolean(),
                                    currentValue = customParam.currentValue.toBoolean(),
                                    isEnabled = customParam.isEnabled,
                                    valueType = ParameterValueType.BOOLEAN,
                                    category =
                                    ParameterCategory.valueOf(customParam.category),
                                    isCustom = true
                                )
                            }
                            ParameterValueType.OBJECT -> {
                ModelParameter(
                                    id = customParam.id,
                                    name = customParam.name,
                                    apiName = customParam.apiName,
                                    description = customParam.description,
                                    defaultValue = customParam.defaultValue,
                                    currentValue = customParam.currentValue,
                                    isEnabled = customParam.isEnabled,
                                    valueType = ParameterValueType.OBJECT,
                                    category =
                                    ParameterCategory.valueOf(customParam.category),
                                    isCustom = true
                                )
                            }
                        }
                    paramList.add(param)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 更新参数列表
        parameters = paramList
        initialized = true
    }

    // 更新参数值
    val updateParameterValue = { parameter: ModelParameter<*>, newValue: Any ->
        val newParameters =
                parameters.map { p ->
                    if (p.id == parameter.id) {
                        when (p.valueType) {
                            ParameterValueType.INT -> {
                                val intParam = p as ModelParameter<Int>
                                intParam.copy(currentValue = newValue as Int)
                            }

                            ParameterValueType.FLOAT -> {
                                val floatParam = p as ModelParameter<Float>
                                floatParam.copy(currentValue = newValue as Float)
                            }

                            ParameterValueType.STRING -> {
                                val stringParam = p as ModelParameter<String>
                                stringParam.copy(currentValue = newValue as String)
                            }

                            ParameterValueType.BOOLEAN -> {
                                val boolParam = p as ModelParameter<Boolean>
                                boolParam.copy(currentValue = newValue as Boolean)
                            }
                            ParameterValueType.OBJECT -> {
                                val objParam = p as ModelParameter<String>
                                objParam.copy(currentValue = newValue as String)
                            }
                        }
                    } else {
                        p
                    }
                }
        parameters = newParameters
        scope.launch { persistParameters() }
    }

    // 切换参数启用状态
    val toggleParameter = { parameter: ModelParameter<*>, isEnabled: Boolean ->
        val newParameters =
                parameters.map { p ->
                    if (p.id == parameter.id) {
                        when (p.valueType) {
                            ParameterValueType.INT -> {
                                val intParam = p as ModelParameter<Int>
                                intParam.copy(isEnabled = isEnabled)
                            }

                            ParameterValueType.FLOAT -> {
                                val floatParam = p as ModelParameter<Float>
                                floatParam.copy(isEnabled = isEnabled)
                            }

                            ParameterValueType.STRING -> {
                                val stringParam = p as ModelParameter<String>
                                stringParam.copy(isEnabled = isEnabled)
                            }

                            ParameterValueType.BOOLEAN -> {
                                val boolParam = p as ModelParameter<Boolean>
                                boolParam.copy(isEnabled = isEnabled)
                            }
                            ParameterValueType.OBJECT -> {
                                val objParam = p as ModelParameter<String>
                                objParam.copy(isEnabled = isEnabled)
                            }
                        }
                    } else {
                        p
                    }
                }
        parameters = newParameters
        scope.launch { persistParameters() }
    }

    // 重置所有参数
    val resetParameters = {
        scope.launch {
            try {
                // 重置所有参数为默认值
                val resetParams =
                        parameters.map { param ->
                            when (param.valueType) {
                                ParameterValueType.INT -> {
                                    val intParam = param as ModelParameter<Int>
                                    intParam.copy(
                                            currentValue = intParam.defaultValue,
                                            isEnabled = false
                                    )
                                }

                                ParameterValueType.FLOAT -> {
                                    val floatParam = param as ModelParameter<Float>
                                    floatParam.copy(
                                            currentValue = floatParam.defaultValue,
                                            isEnabled = false
                                    )
                                }

                                ParameterValueType.STRING -> {
                                    val stringParam = param as ModelParameter<String>
                                    stringParam.copy(
                                            currentValue = stringParam.defaultValue,
                                            isEnabled = false
                                    )
                                }

                                ParameterValueType.BOOLEAN -> {
                                    val boolParam = param as ModelParameter<Boolean>
                                    boolParam.copy(
                                            currentValue = boolParam.defaultValue,
                                            isEnabled = false
                                    )
                                }
                                ParameterValueType.OBJECT -> {
                                    val objParam = param as ModelParameter<String>
                                    objParam.copy(
                                            currentValue = objParam.defaultValue,
                                            isEnabled = false
                                    )
                                }
                            }
                        }
                parameters = resetParams

                // 保存重置后的参数
                persistParameters()
                showNotification(parametersResetText, OKToast.Kind.Success)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 分类参数
    val generationParams = parameters.filter { it.category == ParameterCategory.GENERATION && !it.isCustom }
    val creativityParams = parameters.filter { it.category == ParameterCategory.CREATIVITY && !it.isCustom }
    val repetitionParams = parameters.filter { it.category == ParameterCategory.REPETITION && !it.isCustom }
    val customParams = parameters.filter { it.isCustom }

    // 状态
    var parameterToEdit by remember { mutableStateOf<com.ai.assistance.operit.data.model.CustomParameterData?>(null) }

    if (showAddParameterDialog || parameterToEdit != null) {
        AddCustomParameterDialog(
            existingParam = parameterToEdit,
            onDismiss = {
                showAddParameterDialog = false
                parameterToEdit = null
            },
            onSave = { newParamData ->
                scope.launch {
                    try {
                        val updatedParameters = if (parameterToEdit != null) {
                            // 更新现有参数
                            parameters.map {
                                if (it.id == newParamData.id) {
                                    convertCustomParameterDataToModelParameter(newParamData)
                                } else {
                                    it
                                }
                            }
                        } else {
                            // 添加新参数
                            val newModelParam = convertCustomParameterDataToModelParameter(newParamData)
                            parameters + newModelParam
                        }
                        parameters = updatedParameters

                        // 保存到存储
                        persistParameters()

                        showNotification(if (parameterToEdit != null) customParameterUpdatedText else customParameterAddedText, OKToast.Kind.Success)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showNotification(
                            addCustomParameterFailedText.format(
                                e.message ?: "Unknown error"
                            ), OKToast.Kind.Error
                        )
                    } finally {
                        showAddParameterDialog = false
                        parameterToEdit = null
                    }
                }
            }
        )
    }

    // 参数状态保留在折叠区外；各区只负责呈现，不改变现有参数数据格式。
    @Composable
    fun ParameterList(items: List<ModelParameter<*>>, emptyMessage: String) {
        if (items.isEmpty()) {
            ParametersEmptyState(message = emptyMessage)
        } else {
            items.forEach { parameter ->
                key(parameter.id) {
                    ParameterItem(
                        parameter = parameter,
                        onValueChange = { updateParameterValue(parameter, it) },
                        onToggle = { toggleParameter(parameter, it) },
                        onEditClick = {
                            if (parameter.isCustom) parameterToEdit = modelParameterToCustomParameterData(parameter)
                        },
                        onDeleteClick = {
                            if (parameter.isCustom) {
                                val updated = parameters.filterNot { it.id == parameter.id }
                                parameters = updated
                                scope.launch { persistParameters() }
                            }
                        },
                        error = parameterErrors[parameter.id],
                        onErrorChange = { error ->
                            if (error == null) parameterErrors.remove(parameter.id) else parameterErrors[parameter.id] = error
                        },
                    )
                    if (parameter.apiName == "temperature") TemperatureRecommendationRow()
                }
            }
        }
    }
    Column {
        ModelWorkspaceSection(
            title = stringResource(R.string.generation_parameters),
            subtitle = stringResource(R.string.model_workspace_generation_hint),
            stateKey = "generation:${config.id}",
        ) {
            ParameterList(generationParams, stringResource(R.string.parameters_empty_category))
        }
        thinkingContent()
        ModelWorkspaceSection(
            title = stringResource(R.string.model_workspace_sampling),
            subtitle = stringResource(R.string.model_workspace_sampling_hint),
            stateKey = "sampling:${config.id}",
        ) {
            ParameterList(creativityParams + repetitionParams, stringResource(R.string.parameters_empty_category))
        }
        ModelWorkspaceSection(
            title = customParametersSectionText,
            subtitle = stringResource(R.string.model_workspace_custom_hint),
            stateKey = "custom:${config.id}",
        ) {
            ParameterList(customParams, stringResource(R.string.parameters_empty_custom))
            TextButton(onClick = { showAddParameterDialog = true }) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(addCustomParameterText)
            }
        }
        TextButton(onClick = { resetParameters() }, modifier = Modifier.align(Alignment.End)) {
            Text(resetParametersText)
        }
    }
}
// 自定义参数与原数据模型间的转换继续沿用现有实现。
private fun convertCustomParameterDataToModelParameter(
    customParam: com.ai.assistance.operit.data.model.CustomParameterData
): ModelParameter<*> {
    return when (ParameterValueType.valueOf(customParam.valueType)) {
        ParameterValueType.INT -> {
            ModelParameter(
                id = customParam.id,
                name = customParam.name,
                apiName = customParam.apiName,
                description = customParam.description,
                defaultValue = customParam.defaultValue.toInt(),
                currentValue = customParam.currentValue.toInt(),
                isEnabled = customParam.isEnabled,
                valueType = ParameterValueType.INT,
                minValue = customParam.minValue?.toInt(),
                maxValue = customParam.maxValue?.toInt(),
                category = ParameterCategory.valueOf(customParam.category),
                isCustom = true
            )
        }

        ParameterValueType.FLOAT -> {
            ModelParameter(
                id = customParam.id,
                name = customParam.name,
                apiName = customParam.apiName,
                description = customParam.description,
                defaultValue = customParam.defaultValue.toFloat(),
                currentValue = customParam.currentValue.toFloat(),
                isEnabled = customParam.isEnabled,
                valueType = ParameterValueType.FLOAT,
                minValue = customParam.minValue?.toFloat(),
                maxValue = customParam.maxValue?.toFloat(),
                category = ParameterCategory.valueOf(customParam.category),
                isCustom = true
            )
        }

        ParameterValueType.STRING -> {
            ModelParameter(
                id = customParam.id,
                name = customParam.name,
                apiName = customParam.apiName,
                description = customParam.description,
                defaultValue = customParam.defaultValue,
                currentValue = customParam.currentValue,
                isEnabled = customParam.isEnabled,
                valueType = ParameterValueType.STRING,
                category = ParameterCategory.valueOf(customParam.category),
                isCustom = true
            )
        }

        ParameterValueType.BOOLEAN -> {
            ModelParameter(
                id = customParam.id,
                name = customParam.name,
                apiName = customParam.apiName,
                description = customParam.description,
                defaultValue = customParam.defaultValue.toBoolean(),
                currentValue = customParam.currentValue.toBoolean(),
                isEnabled = customParam.isEnabled,
                valueType = ParameterValueType.BOOLEAN,
                category = ParameterCategory.valueOf(customParam.category),
                isCustom = true
            )
        }

        ParameterValueType.OBJECT -> {
            ModelParameter(
                id = customParam.id,
                name = customParam.name,
                apiName = customParam.apiName,
                description = customParam.description,
                defaultValue = customParam.defaultValue,
                currentValue = customParam.currentValue,
                isEnabled = customParam.isEnabled,
                valueType = ParameterValueType.OBJECT,
                category = ParameterCategory.valueOf(customParam.category),
                isCustom = true
            )
        }
    }
}

// Helper function to convert ModelParameter<*> to CustomParameterData
private fun modelParameterToCustomParameterData(
    param: ModelParameter<*>
): com.ai.assistance.operit.data.model.CustomParameterData {
    return com.ai.assistance.operit.data.model.CustomParameterData(
        id = param.id,
        name = param.name,
        apiName = param.apiName,
        description = param.description,
        defaultValue = param.defaultValue.toString(),
        currentValue = param.currentValue.toString(),
        isEnabled = param.isEnabled,
        valueType = param.valueType.name,
        minValue = param.minValue?.toString(),
        maxValue = param.maxValue?.toString(),
        category = param.category.name
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomParameterDialog(
    onDismiss: () -> Unit,
    onSave: (com.ai.assistance.operit.data.model.CustomParameterData) -> Unit,
    existingParam: com.ai.assistance.operit.data.model.CustomParameterData? = null
) {
    val isEditing = existingParam != null

    var name by remember { mutableStateOf(existingParam?.name ?: "") }
    var apiName by remember { mutableStateOf(existingParam?.apiName ?: "") }
    var description by remember { mutableStateOf(existingParam?.description ?: "") }
    var valueType by remember {
        mutableStateOf(
            existingParam?.valueType?.let { ParameterValueType.valueOf(it) }
                ?: ParameterValueType.STRING
        )
    }
    var defaultValue by remember { mutableStateOf(existingParam?.defaultValue ?: "") }
    var minValue by remember { mutableStateOf(existingParam?.minValue ?: "") }
    var maxValue by remember { mutableStateOf(existingParam?.maxValue ?: "") }
    var category by remember {
        mutableStateOf(
            existingParam?.category?.let { ParameterCategory.valueOf(it) }
                ?: ParameterCategory.GENERATION
        )
    }

    var nameError by remember { mutableStateOf<String?>(null) }
    var defaultValueError by remember { mutableStateOf<String?>(null) }
    var minValueError by remember { mutableStateOf<String?>(null) }
    var maxValueError by remember { mutableStateOf<String?>(null) }

    // Dropdown states
    var valueTypeExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    // String resources
    val dialogTitle = if (isEditing) {
        stringResource(R.string.edit_custom_parameter)
    } else {
        stringResource(R.string.add_custom_parameter)
    }
    val parameterNameText = stringResource(R.string.parameter_name)
    val parameterApiNameText = stringResource(R.string.parameter_api_name)
    val parameterDescriptionText = stringResource(R.string.parameter_description)
    val parameterTypeText = stringResource(R.string.parameter_type)
    val parameterDefaultValueText = stringResource(R.string.parameter_default_value)
    val parameterMinValueText = stringResource(R.string.parameter_min_value)
    val parameterMaxValueText = stringResource(R.string.parameter_max_value)
    val parameterCategoryText = stringResource(R.string.parameter_category)

    val parameterNameRequiredText = stringResource(R.string.parameter_name_required)
    val saveText = stringResource(R.string.save)
    val cancelText = stringResource(R.string.cancel)
    val mustBeIntegerText = stringResource(R.string.must_be_integer)
    val mustBeFloatText = stringResource(R.string.must_be_float)
    val mustBeBooleanText = stringResource(R.string.must_be_boolean)
    val mustBeJsonText = stringResource(R.string.must_be_valid_json)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 参数名称 - 总是显示
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        // 如果是创建模式，自动设置 apiName
                        if (!isEditing) {
                            apiName = it.replace(" ", "_").lowercase()
                        }
                    },
                    label = { Text(parameterNameText) },
                    isError = nameError != null,
                    modifier = Modifier.fillMaxWidth()
                )
                nameError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Value Type Dropdown - 总是显示
                ExposedDropdownMenuBox(
                    expanded = valueTypeExpanded,
                    onExpandedChange = { valueTypeExpanded = !valueTypeExpanded }
                ) {
                    OutlinedTextField(
                        value = valueType.toDisplayString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(parameterTypeText) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = valueTypeExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ModelWorkspaceMenu(
                        expanded = valueTypeExpanded,
                        onDismissRequest = { valueTypeExpanded = false }
                    ) {
                        ParameterValueType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.toDisplayString()) },
                                onClick = {
                                    valueType = type
                                    valueTypeExpanded = false
                                    // Reset default value
                                    defaultValue = when (type) {
                                        ParameterValueType.INT -> "0"
                                        ParameterValueType.FLOAT -> "0.0"
                                        ParameterValueType.STRING -> ""
                                        ParameterValueType.BOOLEAN -> "true"
                                        ParameterValueType.OBJECT -> "{}"
                                    }
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Default Value - 总是显示
                OutlinedTextField(
                    value = defaultValue,
                    onValueChange = {
                        defaultValue = it
                        // 实时校验 JSON 格式（仅在对象类型时）
                        if (valueType == ParameterValueType.OBJECT) {
                            val isValid = try {
                                Json.parseToJsonElement(it)
                                true
                            } catch (e: Exception) {
                                false
                            }
                            defaultValueError = if (isValid) null else mustBeJsonText
                        } else {
                            defaultValueError = null
                        }
                    },
                    label = { Text(parameterDefaultValueText) },
                    isError = defaultValueError != null,
                    modifier = Modifier.fillMaxWidth()
                )
                defaultValueError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // 以下字段只在编辑模式下显示
                if (isEditing) {
                    OutlinedTextField(
                        value = apiName,
                        onValueChange = { apiName = it },
                        label = { Text(parameterApiNameText) },
                        placeholder = { Text(stringResource(R.string.api_name_placeholder)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(parameterDescriptionText) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Min/Max Value
                    Row(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = minValue,
                            onValueChange = { minValue = it },
                            label = { Text(parameterMinValueText) },
                            isError = minValueError != null,
                            modifier = Modifier.weight(1f),
                            enabled = valueType == ParameterValueType.INT || valueType == ParameterValueType.FLOAT
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = maxValue,
                            onValueChange = { maxValue = it },
                            label = { Text(parameterMaxValueText) },
                            isError = maxValueError != null,
                            modifier = Modifier.weight(1f),
                            enabled = valueType == ParameterValueType.INT || valueType == ParameterValueType.FLOAT
                        )
                    }
                    if (minValueError != null || maxValueError != null) {
                        Row(Modifier.fillMaxWidth()) {
                           Text(
                               text = minValueError ?: "",
                               color = MaterialTheme.colorScheme.error,
                               style = MaterialTheme.typography.bodySmall,
                               modifier = Modifier.weight(1f)
                           )
                           Spacer(modifier = Modifier.width(8.dp))
                           Text(
                               text = maxValueError ?: "",
                               color = MaterialTheme.colorScheme.error,
                               style = MaterialTheme.typography.bodySmall,
                               modifier = Modifier.weight(1f)
                           )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                }

                // Category Dropdown - 创建/编辑均显示
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = category.toDisplayString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(parameterCategoryText) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ModelWorkspaceMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        ParameterCategory.values().forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.toDisplayString()) },
                                onClick = {
                                    category = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    var hasError = false
                    // Reset errors
                    nameError = null
                    defaultValueError = null
                    minValueError = null
                    maxValueError = null

                    if (name.isBlank()) {
                        nameError = parameterNameRequiredText
                        hasError = true
                    }

                    // Value validation
                    when (valueType) {
                        ParameterValueType.INT -> {
                            if (defaultValue.toIntOrNull() == null) {
                                defaultValueError = mustBeIntegerText
                                hasError = true
                            }
                            if (minValue.isNotBlank() && minValue.toIntOrNull() == null) {
                                minValueError = mustBeIntegerText
                                hasError = true
                            }
                            if (maxValue.isNotBlank() && maxValue.toIntOrNull() == null) {
                                maxValueError = mustBeIntegerText
                                hasError = true
                            }
                        }
                        ParameterValueType.FLOAT -> {
                            if (defaultValue.toFloatOrNull() == null) {
                                defaultValueError = mustBeFloatText
                                hasError = true
                            }
                             if (minValue.isNotBlank() && minValue.toFloatOrNull() == null) {
                                minValueError = mustBeFloatText
                                hasError = true
                            }
                            if (maxValue.isNotBlank() && maxValue.toFloatOrNull() == null) {
                                maxValueError = mustBeFloatText
                                hasError = true
                            }
                        }
                        ParameterValueType.BOOLEAN -> {
                            if (
                                defaultValue.lowercase() != "true" &&
                                defaultValue.lowercase() != "false"
                            ) {
                                defaultValueError = mustBeBooleanText
                                hasError = true
                            }
                        }
                        ParameterValueType.STRING -> {
                            // No validation needed for string
                        }
                        ParameterValueType.OBJECT -> {
                            val isValid = try {
                                kotlinx.serialization.json.Json.parseToJsonElement(defaultValue)
                                true
                            } catch (e: Exception) {
                                false
                            }
                            if (!isValid) {
                                defaultValueError = mustBeJsonText
                                hasError = true
                            }
                        }
                    }

                    if (hasError) return@Button

                    val newParam =
                        com.ai.assistance.operit.data.model.CustomParameterData(
                            id = existingParam?.id ?: UUID.randomUUID().toString(),
                            name = name,
                            apiName = if (apiName.isBlank()) name.replace(" ", "_").lowercase() else apiName,
                            description = description,
                            defaultValue = defaultValue,
                            currentValue = existingParam?.currentValue ?: defaultValue,
                            isEnabled = existingParam?.isEnabled ?: true,
                            valueType = valueType.name,
                            minValue = minValue.ifBlank { null },
                            maxValue = maxValue.ifBlank { null },
                            category = category.name
                        )
                    onSave(newParam)
                }
            ) {
                Text(saveText)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelText) } }
    )
}

@Composable
private fun ParameterValueType.toDisplayString(): String {
    return when (this) {
        ParameterValueType.INT -> stringResource(R.string.value_type_int)
        ParameterValueType.FLOAT -> stringResource(R.string.value_type_float)
        ParameterValueType.STRING -> stringResource(R.string.value_type_string)
        ParameterValueType.BOOLEAN -> stringResource(R.string.value_type_boolean)
        ParameterValueType.OBJECT -> stringResource(R.string.value_type_object)
    }
}

@Composable
private fun ParameterCategory.toDisplayString(): String {
    return when (this) {
        ParameterCategory.GENERATION -> stringResource(R.string.generation_parameters)
        ParameterCategory.CREATIVITY -> stringResource(R.string.creativity_parameters)
        ParameterCategory.REPETITION -> stringResource(R.string.repetition_parameters)
        ParameterCategory.OTHER -> stringResource(R.string.other_parameters)
    }
}

@Composable
private fun ParametersEmptyState(message: String) {
    Surface(
            modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    ) {
        Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ParameterItem(
        parameter: ModelParameter<*>,
        onValueChange: (Any) -> Unit,
        onToggle: (Boolean) -> Unit,
        onEditClick: () -> Unit,
        onDeleteClick: () -> Unit,
        error: String? = null,
        onErrorChange: (String?) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    val valueText = stringResource(R.string.parameter_value)
    val rangeFormatText = stringResource(R.string.parameter_range_format)
    val mustBeIntegerText = stringResource(R.string.must_be_integer)
    val mustBeFloatText = stringResource(R.string.must_be_float)

    Surface(
            modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = parameter.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                    )

                    if (parameter.description.isNotEmpty()) {
                        Text(
                                text = parameter.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (parameter.isCustom) {
                        Text(
                                text = stringResource(R.string.model_custom_params),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        Text(
                                text = stringResource(R.string.api_name_label, parameter.apiName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                            checked = parameter.isEnabled,
                            onCheckedChange = { onToggle(it) },
                            colors =
                                    SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            checkedTrackColor =
                                                    MaterialTheme.colorScheme.primaryContainer
                                    )
                    )

                    if (parameter.isCustom) {
                        IconButton(onClick = onEditClick, modifier = Modifier.size(32.dp)) {
                            Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.edit_custom_parameter),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete_action),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                        Icon(
                                imageVector =
                                        if (expanded) Icons.Default.ExpandLess
                                        else Icons.Default.ExpandMore,
                                contentDescription =
                                        if (expanded) stringResource(R.string.collapse)
                                        else stringResource(R.string.expand),
                                modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(R.string.confirm_delete)) },
                text = { Text(parameter.name) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        onDeleteClick()
                    }) {
                        Text(stringResource(R.string.delete_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // 参数值设置（仅在展开时显示）
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                when (parameter.valueType) {
                    ParameterValueType.INT -> {
                        val intParam = parameter as ModelParameter<Int>
                        var textValue by remember {
                            mutableStateOf(intParam.currentValue.toString())
                        }

                        OutlinedTextField(
                                value = textValue,
                                onValueChange = {
                                    textValue = it
                                    try {
                                        val intValue = it.toInt()
                                        onErrorChange(null)
                                        onValueChange(intValue)
                                    } catch (e: NumberFormatException) {
                                    onErrorChange(mustBeIntegerText)
                                    }
                                },
                                label = { Text(valueText) },
                                isError = error != null,
                                supportingText = {
                                    if (error != null) {
                                        Text(error)
                                    } else if (intParam.minValue != null &&
                                                    intParam.maxValue != null
                                    ) {
                                    Text(
                                        rangeFormatText.format(
                                            intParam.minValue,
                                            intParam.maxValue
                                        )
                                    )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ParameterValueType.FLOAT -> {
                        val floatParam = parameter as ModelParameter<Float>
                        var textValue by remember {
                            mutableStateOf(floatParam.currentValue.toString())
                        }

                        OutlinedTextField(
                                value = textValue,
                                onValueChange = {
                                    textValue = it
                                    try {
                                        val floatValue = it.toFloat()
                                        onErrorChange(null)
                                        onValueChange(floatValue)
                                    } catch (e: NumberFormatException) {
                                    onErrorChange(mustBeFloatText)
                                    }
                                },
                                label = { Text(valueText) },
                                isError = error != null,
                                supportingText = {
                                    if (error != null) {
                                        Text(error)
                                    } else if (floatParam.minValue != null &&
                                                    floatParam.maxValue != null
                                    ) {
                                    Text(
                                        rangeFormatText.format(
                                            floatParam.minValue,
                                            floatParam.maxValue
                                        )
                                    )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ParameterValueType.STRING -> {
                        val stringParam = parameter as ModelParameter<String>

                        OutlinedTextField(
                                value = stringParam.currentValue,
                                onValueChange = { onValueChange(it) },
                                label = { Text(valueText) },
                                modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ParameterValueType.BOOLEAN -> {
                        val boolParam = parameter as ModelParameter<Boolean>

                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    text = "$valueText:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(end = 16.dp)
                            )
                            Switch(
                                    checked = boolParam.currentValue,
                                    onCheckedChange = { onValueChange(it) }
                            )
                        }
                    }
                    ParameterValueType.OBJECT -> {
                        val objParam = parameter as ModelParameter<String>
                        var textValue by remember { mutableStateOf(objParam.currentValue) }
                        val mustBeJsonTextLocal = stringResource(R.string.must_be_valid_json)
                        OutlinedTextField(
                                value = textValue,
                                onValueChange = {
                                    textValue = it
                                    // 实时校验 JSON
                                    val isValid = try {
                                        Json.parseToJsonElement(it)
                                        true
                                    } catch (e: Exception) {
                                        false
                                    }
                                    onErrorChange(if (isValid) null else mustBeJsonTextLocal)
                                    onValueChange(it)
                                },
                                isError = error != null,
                                supportingText = { if (error != null) Text(error) },
                                label = { Text(valueText) },
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp),
                                singleLine = false,
                                maxLines = 6
                        )
                    }
                }

                // 显示默认值
                Text(
                    text = stringResource(
                        R.string.default_value_format,
                        parameter.defaultValue.toString()
                    ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        }
    }
}

@Composable
private fun TemperatureRecommendationRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.temperature_recommendation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
        )

        Card(
                shape = RoundedCornerShape(16.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
        ) {
            Text(
                    text = "1.3",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}
