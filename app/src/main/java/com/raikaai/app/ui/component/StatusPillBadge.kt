package com.raikaai.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raikaai.app.core.server.OpenAiServer
import com.raikaai.app.ui.theme.success
import java.io.File

/**
 * Pill-shaped status chip adapted directly from KZKT's design system.
 */
@Composable
fun StatusChip(
    icon: ImageVector,
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val chipShape = RoundedCornerShape(50)
    Surface(
        shape = chipShape,
        color = container,
        modifier =
            modifier
                .clip(chipShape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * LiteRT Engine status pill badge matching KZKT's YoloStatusCard.
 */
@Composable
fun EngineStatusBadge(
    isLoaded: Boolean,
    modelPath: String?,
    backend: String,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    when {
        isLoading -> {
            StatusChip(
                icon = Icons.Outlined.HourglassEmpty,
                text = "Loading Model...",
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier,
                onClick = onClick,
            )
        }
        errorMessage != null -> {
            StatusChip(
                icon = Icons.Default.Error,
                text = errorMessage,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                modifier = modifier,
                onClick = onClick,
            )
        }
        isLoaded -> {
            val displayName = modelPath?.let { File(it).nameWithoutExtension } ?: "Model"
            val shortName =
                if (displayName.length > 20) displayName.take(18) + "…" else displayName
            val backendLabel = if (backend.equals("gpu", ignoreCase = true)) "GPU" else "CPU"
            StatusChip(
                icon = Icons.Default.CheckCircle,
                text = "$shortName ($backendLabel)",
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = modifier,
                onClick = onClick,
            )
        }
        else -> {
            StatusChip(
                icon = Icons.Outlined.Memory,
                text = "No Model Loaded",
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier,
                onClick = onClick,
            )
        }
    }
}

/**
 * Server status pill badge matching KZKT status indicators.
 */
@Composable
fun ServerStatusBadge(
    serverStatus: OpenAiServer.Status,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    when (serverStatus) {
        is OpenAiServer.Status.Running -> {
            StatusChip(
                icon = Icons.Outlined.Dns,
                text = "Server :${serverStatus.port}",
                container = MaterialTheme.colorScheme.success.copy(alpha = 0.20f),
                content = MaterialTheme.colorScheme.success,
                modifier = modifier,
                onClick = onClick,
            )
        }
        is OpenAiServer.Status.Starting -> {
            StatusChip(
                icon = Icons.Outlined.HourglassEmpty,
                text = "Starting Server…",
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier,
                onClick = onClick,
            )
        }
        is OpenAiServer.Status.Error -> {
            StatusChip(
                icon = Icons.Default.Error,
                text = "Server Error",
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                modifier = modifier,
                onClick = onClick,
            )
        }
        OpenAiServer.Status.Stopped -> {
            StatusChip(
                icon = Icons.Default.StopCircle,
                text = "Server Off",
                container = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                content = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = modifier,
                onClick = onClick,
            )
        }
    }
}
