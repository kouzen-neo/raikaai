package com.raikaai.app.ui.models

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raikaai.app.core.remote.RemoteUsage

/**
 * Remote usage per model: tokens in and out, requests, and a cost estimate
 * from the provider's published prices or a price the user enters.
 */
@Composable
fun RemoteUsageCard(
    providerPricesPerToken: Map<String, com.raikaai.app.core.remote.ModelPricing>,
    modifier: Modifier = Modifier,
) {
    val usage by RemoteUsage.usage.collectAsState()
    val manual by RemoteUsage.prices.collectAsState()
    var editing by remember { mutableStateOf<String?>(null) }

    fun priceFor(model: String): RemoteUsage.Price? =
        manual[model] ?: providerPricesPerToken[model]?.let {
            RemoteUsage.Price(it.promptPerToken * 1_000_000, it.completionPerToken * 1_000_000)
        }

    val rows = usage.entries.sortedByDescending { it.value.inputTokens + it.value.outputTokens }
    val totalIn = rows.sumOf { it.value.inputTokens }
    val totalOut = rows.sumOf { it.value.outputTokens }
    val totalReq = rows.sumOf { it.value.requests }
    val costs = rows.mapNotNull { RemoteUsage.cost(it.value, priceFor(it.key)) }
    val unpriced = rows.count { priceFor(it.key) == null }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Remote usage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (totalReq == 0) "No remote requests yet" else "${formatTokens(totalIn)} in · ${formatTokens(totalOut)} out · $totalReq requests",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        text = if (costs.isEmpty()) "—" else formatUsd(costs.sum()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            if (rows.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                rows.forEach { (model, u) ->
                    val price = priceFor(model)
                    val cost = RemoteUsage.cost(u, price)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { editing = model }.padding(vertical = 4.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(model, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val approx = if (u.estimated > 0) "~" else ""
                            Text(
                                "$approx${formatTokens(u.inputTokens)} in · $approx${formatTokens(u.outputTokens)} out · ${u.requests} req",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = cost?.let { formatUsd(it) } ?: "Set price",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (cost == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Text(
                    buildString {
                        append("Tokens come from the provider when it reports them; ~ marks estimates. ")
                        append(if (unpriced > 0) "Tap a model to enter its price per 1M tokens (free or subscription proxies can stay unpriced)." else "Tap a model to change its price.")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { RemoteUsage.reset() }) { Text("Reset usage") }
                }
            }
        }
    }

    editing?.let { model ->
        val cur = priceFor(model)
        var inText by remember(model) { mutableStateOf(cur?.inputPerMillion?.let { trim(it) } ?: "") }
        var outText by remember(model) { mutableStateOf(cur?.outputPerMillion?.let { trim(it) } ?: "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Price for $model", maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "USD per 1 million tokens, from your provider's pricing page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = inText,
                        onValueChange = { inText = it },
                        label = { Text("Input") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        value = outText,
                        onValueChange = { outText = it },
                        label = { Text("Output") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Spacer(Modifier.height(2.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val i = inText.replace(',', '.').toDoubleOrNull()
                    val o = outText.replace(',', '.').toDoubleOrNull()
                    if (i != null && o != null && i >= 0 && o >= 0) RemoteUsage.setPrice(model, RemoteUsage.Price(i, o))
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (manual.containsKey(model)) {
                        TextButton(onClick = {
                            RemoteUsage.setPrice(model, null)
                            editing = null
                        }) { Text("Clear") }
                    }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
}

private fun trim(d: Double): String = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

internal fun formatTokens(n: Long): String =
    when {
        n >= 1_000_000 -> "%.2fM".format(n / 1_000_000.0)
        n >= 1_000 -> "%.1fk".format(n / 1_000.0)
        else -> n.toString()
    }

private fun formatUsd(v: Double): String =
    when {
        v == 0.0 -> "$0"
        v < 0.01 -> "$%.4f".format(v)
        else -> "$%.2f".format(v)
    }
