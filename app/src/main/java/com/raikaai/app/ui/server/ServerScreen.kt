package com.raikaai.app.ui.server

import com.raikaai.app.ui.component.ScreenHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raikaai.app.ui.chat.ChatViewModel

/**
 * Dedicated drawer destination for the embedded OpenAI-compatible local
 * server and the on-device speed benchmark. Previously merged into the
 * On-Device tab of Models Hub, which made that screen too long.
 */
@Composable
fun ServerScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 10.dp, start = 16.dp, end = 16.dp),
        ) {
            // ── 0. Top Header (same pattern as every other screen) ──
            ScreenHeader(
                title = "Local API",
                subtitle = "Serve the on-device model to other apps (OpenAI-compatible)",
                onOpenDrawer = onOpenDrawer,
                insideHorizontalPadding = true,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item(key = "server_benchmark_section") {
                    ServerBenchmarkSection(viewModel = viewModel)
                }
            }
        }
    }
}
