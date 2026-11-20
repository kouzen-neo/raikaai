package com.raikaai.app.ui.models

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.Dataset
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.raikaai.app.data.ChatConstants
import com.raikaai.app.data.Settings
import com.raikaai.app.ui.component.SettingsTextField
import java.util.Locale

/**
 * Tab content for LiteRT on-device LLM parameter tuning, sampler presets, and context configuration.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalAiParametersTab(
    settings: Settings,
    onUpdateHuggingFaceToken: (String) -> Unit,
    onUpdateTemperature: (Float) -> Unit,
    onUpdateTopK: (Int) -> Unit,
    onUpdateTopP: (Float) -> Unit,
    onUpdateMaxTokens: (Int) -> Unit,
    onUpdateContextWindow: (Int) -> Unit,
    onUpdatePromptTemplate: (String) -> Unit,
    onApplySamplerPreset: (String) -> Unit,
    onUpdateBackend: (String) -> Unit,
    onResetToRecommended: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showHfToken by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── 1. One-Tap Sampler Presets ──
        SamplerPresetsCard(
            currentTemp = settings.temperature,
            onApplyPreset = onApplySamplerPreset,
        )

        // ── 2. Context Window & Memory Budget ──
        ContextWindowCard(
            currentTokens = settings.contextWindowTokens,
            onSelectTokens = onUpdateContextWindow,
        )

        // ── 3. Prompt Template Formatter Override ──
        PromptTemplateCard(
            currentFormat = settings.promptTemplateFormat,
            onSelectFormat = onUpdatePromptTemplate,
        )

        // ── 4. Hardware Acceleration Card ──
        HardwareBackendCard(
            currentBackend = settings.backend,
            onUpdateBackend = onUpdateBackend,
        )

        // ── 5. Hugging Face Access Token Card ──
        HuggingFaceTokenCard(
            initialToken = settings.huggingFaceToken,
            showToken = showHfToken,
            onToggleVisibility = { showHfToken = !showHfToken },
            onSaveToken = { onUpdateHuggingFaceToken(it) },
        )

        // ── 6. Fine-Grained Sliders ──
        ParameterSlider(
            title = "Temperature",
            value = settings.temperature,
            valueDisplay = String.format(Locale.ROOT, "%.2f", settings.temperature),
            description = "Low values (0.1 - 0.3) provide factual, consistent responses. Higher values (0.7 - 0.9) increase creativity.",
            range = 0.0f..2.0f,
            steps = 20,
            onValueChange = onUpdateTemperature,
        )

        ParameterSlider(
            title = "Top-K Sampling",
            value = settings.topK.toFloat(),
            valueDisplay = "${settings.topK}",
            description = "Restricts sampling choices to the top K most likely tokens.",
            range = 1.0f..100.0f,
            onValueChange = { onUpdateTopK(it.toInt()) },
        )

        ParameterSlider(
            title = "Top-P (Nucleus Sampling)",
            value = settings.topP,
            valueDisplay = String.format(Locale.ROOT, "%.2f", settings.topP),
            description = "Selects tokens from the smallest set whose cumulative probability exceeds P.",
            range = 0.0f..1.0f,
            onValueChange = onUpdateTopP,
        )

        ParameterSlider(
            title = "Max Output Tokens",
            value = settings.maxTokens.toFloat(),
            valueDisplay = "${settings.maxTokens}",
            description = "Maximum token limit generated per response turn.",
            range = 64.0f..2048.0f,
            onValueChange = { onUpdateMaxTokens(it.toInt()) },
        )

        OutlinedButton(
            onClick = onResetToRecommended,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Reset All to Recommended Defaults", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SamplerPresetsCard(
    currentTemp: Float,
    onApplyPreset: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "One-Tap Sampler Presets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Quickly switch sampling behavior for different tasks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val isPrecise = currentTemp <= 0.15f
                val isCreative = currentTemp >= 0.70f
                val isBalanced = !isPrecise && !isCreative

                PresetChip(
                    label = "Precise",
                    sub = "Coding / Math",
                    selected = isPrecise,
                    onClick = { onApplyPreset("precise") },
                    modifier = Modifier.weight(1f),
                )
                PresetChip(
                    label = "Balanced",
                    sub = "General Chat",
                    selected = isBalanced,
                    onClick = { onApplyPreset("balanced") },
                    modifier = Modifier.weight(1f),
                )
                PresetChip(
                    label = "Creative",
                    sub = "Story / Roleplay",
                    selected = isCreative,
                    onClick = { onApplyPreset("creative") },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContextWindowCard(
    currentTokens: Int,
    onSelectTokens: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Context Window Budget",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Total conversation token memory. Larger sizes remember longer chats.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    Triple(1024, "1K Fast", "Light RAM"),
                    Triple(2048, "2K Standard", "Balanced"),
                    Triple(4096, "4K Extended", "~600MB RAM"),
                ).forEach { (tokens, title, subtitle) ->
                    val selected = currentTokens == tokens
                    PresetChip(
                        label = title,
                        sub = subtitle,
                        selected = selected,
                        onClick = { onSelectTokens(tokens) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    Triple(8192, "8K Large", "~1.2GB RAM"),
                    Triple(16384, "16K Super", "~2.4GB RAM"),
                    Triple(32768, "32K Ultra", "~4.8GB RAM"),
                ).forEach { (tokens, title, subtitle) ->
                    val selected = currentTokens == tokens
                    PresetChip(
                        label = title,
                        sub = subtitle,
                        selected = selected,
                        onClick = { onSelectTokens(tokens) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptTemplateCard(
    currentFormat: String,
    onSelectFormat: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Dataset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Prompt Template Formatter",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Override prompt framing when using custom imported .litertlm models.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(
                    "auto" to "Auto-Detect",
                    "gemma" to "Gemma",
                    "chatml" to "ChatML (SmolLM/Qwen)",
                    "llama3" to "Llama 3",
                    "raw" to "Raw Plain",
                ).forEach { (key, label) ->
                    val isSelected = currentFormat.equals(key, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectFormat(key) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(50),
                    )
                }
            }
        }
    }
}

@Composable
private fun HardwareBackendCard(
    currentBackend: String,
    onUpdateBackend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Hardware Acceleration (Backend)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Select hardware engine. GPU (OpenCL) is recommended on Snapdragon for highest tok/s.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val isGpu = currentBackend.equals(ChatConstants.BACKEND_GPU, ignoreCase = true)
                Button(
                    onClick = { onUpdateBackend(ChatConstants.BACKEND_GPU) },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = if (isGpu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (isGpu) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("GPU (OpenCL)", fontWeight = if (isGpu) FontWeight.Bold else FontWeight.Normal)
                }
                Button(
                    onClick = { onUpdateBackend(ChatConstants.BACKEND_CPU) },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = if (!isGpu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (!isGpu) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("CPU (NEON)", fontWeight = if (!isGpu) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun HuggingFaceTokenCard(
    initialToken: String,
    showToken: Boolean,
    onToggleVisibility: () -> Unit,
    onSaveToken: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Mirror of the field below, used by the Save button. The field itself uses
    // SettingsTextField so typing is never wiped by unrelated settings emissions.
    var currentToken by remember { mutableStateOf(initialToken) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Hugging Face User Access Token",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Required to download gated models (e.g. Gemma 4).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingsTextField(
                initialValue = initialToken,
                onSave = {},
                onValueChange = { currentToken = it },
                label = { Text("hf_xxxxxxxxxxxxxxxxxxxx") },
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleVisibility) {
                        Icon(
                            if (showToken) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (showToken) "Hide token" else "Show token",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onSaveToken(currentToken.trim()) },
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Save Token")
            }
        }
    }
}

@Composable
private fun ParameterSlider(
    title: String,
    value: Float,
    valueDisplay: String,
    description: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = valueDisplay,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                steps = steps,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
