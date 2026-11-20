package com.raikaai.app.core.remote

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Token usage of remote requests, per model. Every request is counted (chat
 * replies, agent rounds, side questions, compaction), with the provider's own
 * usage numbers when it sends them and an estimate otherwise.
 */
object RemoteUsage {
    data class ModelUsage(
        val inputTokens: Long = 0,
        val outputTokens: Long = 0,
        val requests: Int = 0,
        /** Requests whose numbers were estimated (the provider sent no usage). */
        val estimated: Int = 0,
    )

    /** User-set prices in USD per 1M tokens, for providers that publish none. */
    data class Price(val inputPerMillion: Double, val outputPerMillion: Double)

    private var prefs: SharedPreferences? = null
    private val _usage = MutableStateFlow<Map<String, ModelUsage>>(emptyMap())
    val usage: StateFlow<Map<String, ModelUsage>> = _usage.asStateFlow()
    private val _prices = MutableStateFlow<Map<String, Price>>(emptyMap())
    val prices: StateFlow<Map<String, Price>> = _prices.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("remote_usage", Context.MODE_PRIVATE)
        prefs = p
        _usage.value = parseUsage(p.getString(KEY_USAGE, null))
        _prices.value = parsePrices(p.getString(KEY_PRICES, null))
    }

    @Synchronized
    fun record(model: String, inputTokens: Int, outputTokens: Int, estimated: Boolean) {
        if (model.isBlank() || (inputTokens <= 0 && outputTokens <= 0)) return
        val cur = _usage.value[model] ?: ModelUsage()
        val next =
            cur.copy(
                inputTokens = cur.inputTokens + inputTokens.coerceAtLeast(0),
                outputTokens = cur.outputTokens + outputTokens.coerceAtLeast(0),
                requests = cur.requests + 1,
                estimated = cur.estimated + if (estimated) 1 else 0,
            )
        _usage.value = _usage.value + (model to next)
        prefs?.edit()?.putString(KEY_USAGE, usageJson(_usage.value))?.apply()
    }

    fun setPrice(model: String, price: Price?) {
        _prices.value = if (price == null) _prices.value - model else _prices.value + (model to price)
        prefs?.edit()?.putString(KEY_PRICES, pricesJson(_prices.value))?.apply()
    }

    fun reset() {
        _usage.value = emptyMap()
        prefs?.edit()?.remove(KEY_USAGE)?.apply()
    }

    /** USD cost of [u], or null when no price is known for the model. */
    fun cost(u: ModelUsage, price: Price?): Double? =
        price?.let { u.inputTokens / 1_000_000.0 * it.inputPerMillion + u.outputTokens / 1_000_000.0 * it.outputPerMillion }

    /** Rough token count of [text] (about 3.5 characters per token). */
    fun estimateTokens(text: String): Int = if (text.isEmpty()) 0 else maxOf(1, Math.round(text.length / 3.5).toInt())

    internal fun parseUsage(json: String?): Map<String, ModelUsage> =
        runCatching {
            val o = JSONObject(json ?: return emptyMap())
            o.keys().asSequence().associateWith { k ->
                val e = o.getJSONObject(k)
                ModelUsage(e.optLong("i"), e.optLong("o"), e.optInt("r"), e.optInt("e"))
            }
        }.getOrDefault(emptyMap())

    internal fun usageJson(m: Map<String, ModelUsage>): String =
        JSONObject().apply {
            m.forEach { (k, u) -> put(k, JSONObject().put("i", u.inputTokens).put("o", u.outputTokens).put("r", u.requests).put("e", u.estimated)) }
        }.toString()

    private fun parsePrices(json: String?): Map<String, Price> =
        runCatching {
            val o = JSONObject(json ?: return emptyMap())
            o.keys().asSequence().associateWith { k ->
                val e = o.getJSONObject(k)
                Price(e.optDouble("i"), e.optDouble("o"))
            }
        }.getOrDefault(emptyMap())

    private fun pricesJson(m: Map<String, Price>): String =
        JSONObject().apply {
            m.forEach { (k, p) -> put(k, JSONObject().put("i", p.inputPerMillion).put("o", p.outputPerMillion)) }
        }.toString()

    private const val KEY_USAGE = "usage"
    private const val KEY_PRICES = "prices"
}
