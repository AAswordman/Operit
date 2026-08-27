package com.ai.assistance.operit.data.stats

import android.content.Context
import com.ai.assistance.operit.data.collects.DefaultModelPricingCollect
import com.ai.assistance.operit.data.collects.PricingCurrency
import com.ai.assistance.operit.data.model.BillingMode
import com.ai.assistance.operit.data.model.TokenStatsModelEntity

enum class TokenStatsPriceScope { PROVIDER_MODEL, CONFIG }

data class TokenStatsPriceDraft(
    val scope: TokenStatsPriceScope,
    val provider: String,
    val model: String,
    val configId: String? = null,
    val billingMode: BillingMode,
    val currency: PricingCurrency,
    val inputPricePerMillion: Double? = null,
    val cachedInputPricePerMillion: Double? = null,
    val cacheWritePricePerMillion: Double? = null,
    val outputPricePerMillion: Double? = null,
    val pricePerRequest: Double? = null,
)

data class TokenStatsPriceSetting(
    val scope: TokenStatsPriceScope,
    val providerModel: String,
    val provider: String,
    val model: String,
    val configId: String?,
    val billingMode: BillingMode,
    val currency: PricingCurrency,
    val inputPricePerMillion: Double?,
    val cachedInputPricePerMillion: Double?,
    val cacheWritePricePerMillion: Double?,
    val outputPricePerMillion: Double?,
    val pricePerRequest: Double?,
)

class TokenStatsSettingsManager(context: Context) {
    private val appContext = context.applicationContext
    private val repository = TokenUsageRepository.getInstance(appContext)

    fun validatePriceValue(name: String, value: Double?): Double? {
        if (value == null) return null
        require(value.isFinite() && value > 0.0) {
            "$name must be positive and finite, got $value"
        }
        return value
    }

    suspend fun savePrice(draft: TokenStatsPriceDraft) {
        val provider = draft.provider.trim()
        val model = draft.model.trim()
        val configId = draft.configId?.trim().orEmpty()
        require(provider.isNotEmpty()) { "provider must not be blank" }
        require(model.isNotEmpty()) { "model must not be blank" }
        require(draft.scope != TokenStatsPriceScope.CONFIG || configId.isNotEmpty()) {
            "configId must not be blank for config pricing"
        }
        val storageConfigId =
            if (draft.scope == TokenStatsPriceScope.PROVIDER_MODEL) "" else configId
        repository.withDao { dao ->
            val current =
                dao.getStatsModel(storageConfigId, provider, model)
                    ?: TokenStatsModelEntity(storageConfigId, provider, model)
            dao.upsertStatsModel(
                current.copy(
                    billingMode = draft.billingMode.name,
                    currency = draft.currency.name,
                    inputPricePerMillion =
                        if (draft.billingMode == BillingMode.TOKEN) {
                            validatePriceValue("inputPrice", draft.inputPricePerMillion)
                        } else {
                            null
                        },
                    cachedInputPricePerMillion =
                        if (draft.billingMode == BillingMode.TOKEN) {
                            validatePriceValue("cachedInputPrice", draft.cachedInputPricePerMillion)
                        } else {
                            null
                        },
                    cacheWritePricePerMillion =
                        if (draft.billingMode == BillingMode.TOKEN) {
                            validatePriceValue("cacheWritePrice", draft.cacheWritePricePerMillion)
                        } else {
                            null
                        },
                    outputPricePerMillion =
                        if (draft.billingMode == BillingMode.TOKEN) {
                            validatePriceValue("outputPrice", draft.outputPricePerMillion)
                        } else {
                            null
                        },
                    pricePerRequest =
                        if (draft.billingMode == BillingMode.COUNT) {
                            validatePriceValue("pricePerRequest", draft.pricePerRequest)
                        } else {
                            null
                        },
                )
            )
        }
    }

    suspend fun allPriceSettings(): List<TokenStatsPriceSetting> {
        return repository.withDao { dao ->
            dao.getAllStatsModels()
                .filter(TokenStatsModelEntity::hasPriceSetting)
                .map(TokenStatsModelEntity::toPriceSetting)
                .sortedWith(
                    compareBy(
                        { it.providerModel.lowercase() },
                        { it.scope.ordinal },
                        { it.configId.orEmpty().lowercase() },
                    )
                )
        }
    }

    suspend fun restoreBuiltInPrice(providerModel: String) {
        val (provider, model) = splitProviderModel(providerModel)
        repository.withDao { dao ->
            dao.clearPricing("", provider, model)
            dao.deleteEmptyStatsModels()
        }
    }

    suspend fun resetConfigPrice(providerModel: String, configId: String) {
        require(configId.isNotBlank()) { "configId must not be blank" }
        val (provider, model) = splitProviderModel(providerModel)
        repository.withDao { dao ->
            dao.clearPricing(configId, provider, model)
            dao.deleteEmptyStatsModels()
        }
    }

    private fun splitProviderModel(providerModel: String): Pair<String, String> {
        val separator = providerModel.indexOf(':')
        require(separator > 0 && separator < providerModel.lastIndex) {
            "provider:model is required"
        }
        return providerModel.substring(0, separator) to providerModel.substring(separator + 1)
    }
}

internal fun TokenStatsModelEntity.hasPriceSetting(): Boolean =
    billingMode != null ||
        currency != null ||
        inputPricePerMillion != null ||
        cachedInputPricePerMillion != null ||
        cacheWritePricePerMillion != null ||
        outputPricePerMillion != null ||
        pricePerRequest != null

internal fun TokenStatsModelEntity.toModelPriceSettings(): ModelPriceSettings =
    ModelPriceSettings(
        billingMode = billingMode?.let { BillingMode.valueOf(it) },
        currency = currency?.let { PricingCurrency.valueOf(it) },
        inputPricePerMillion = inputPricePerMillion,
        cachedInputPricePerMillion = cachedInputPricePerMillion,
        cacheWritePricePerMillion = cacheWritePricePerMillion,
        outputPricePerMillion = outputPricePerMillion,
        pricePerRequest = pricePerRequest,
    )

private fun TokenStatsModelEntity.toPriceSetting(): TokenStatsPriceSetting {
    val providerModel = "$provider:$model"
    val defaults = DefaultModelPricingCollect.getDefaultPricing(providerModel)
    return TokenStatsPriceSetting(
        scope = if (configId.isEmpty()) TokenStatsPriceScope.PROVIDER_MODEL else TokenStatsPriceScope.CONFIG,
        providerModel = providerModel,
        provider = provider,
        model = model,
        configId = configId.ifEmpty { null },
        billingMode = billingMode?.let { BillingMode.valueOf(it) } ?: defaults.billingMode,
        currency = currency?.let { PricingCurrency.valueOf(it) } ?: defaults.currency,
        inputPricePerMillion = inputPricePerMillion,
        cachedInputPricePerMillion = cachedInputPricePerMillion,
        cacheWritePricePerMillion = cacheWritePricePerMillion,
        outputPricePerMillion = outputPricePerMillion,
        pricePerRequest = pricePerRequest,
    )
}
