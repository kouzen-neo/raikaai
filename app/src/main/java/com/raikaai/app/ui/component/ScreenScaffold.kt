package com.raikaai.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Corner radius of top-level cards on every screen. */
val CardShape = RoundedCornerShape(20.dp)

/** Corner radius of rows and tiles inside cards. */
val ItemShape = RoundedCornerShape(14.dp)

/**
 * The header every drawer screen uses: menu button, title, optional
 * one-line subtitle, and trailing actions. Keeps size, type and spacing
 * identical across Models, Parameters, Skills, MCP, Settings, etc.
 */
@Composable
fun ScreenHeader(
    title: String,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    insideHorizontalPadding: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    // Screens whose content column already has a 16dp side padding pass
    // insideHorizontalPadding: the header then reaches 12dp into it, so the
    // menu button sits at the same spot on every screen.
    val bleed = if (insideHorizontalPadding) Modifier.horizontalBleed(12.dp) else Modifier
    Row(
        modifier = modifier.then(bleed).fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenDrawer, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Menu, contentDescription = "Open menu", tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Lays the content out [amount] wider on both sides than the space it was given. */
private fun Modifier.horizontalBleed(amount: androidx.compose.ui.unit.Dp): Modifier =
    this.then(
        androidx.compose.ui.Modifier.layout { measurable, constraints ->
            val extra = amount.roundToPx()
            val wide =
                constraints.copy(
                    minWidth = (constraints.minWidth + 2 * extra).coerceAtLeast(0),
                    maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + 2 * extra else constraints.maxWidth,
                )
            val placeable = measurable.measure(wide)
            val width = (placeable.width - 2 * extra).coerceAtLeast(0)
            layout(width, placeable.height) { placeable.place(-extra, 0) }
        },
    )
