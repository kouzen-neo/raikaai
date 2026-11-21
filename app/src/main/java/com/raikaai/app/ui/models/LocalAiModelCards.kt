package com.raikaai.app.ui.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.raikaai.app.localai.HuggingFaceModelResolver
import kotlinx.coroutines.launch

/** Matches a 64-character lowercase hex SHA-256 checksum. */
private val SHA256_HEX_REGEX = Regex("^[0-9a-f]{64}$")

/**
 * Card for importing models directly from Hugging Face repository URL with multi-file target dialog.
 */
@Composable
fun CustomUrlDownloadCard(
    urlInput: String,
    onUrlInputChange: (String) -> Unit,
    hfToken: String,
    onStartDownload: (url: String, fileName: String, sizeBytes: Long, sha256: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isResolving by remember { mutableStateOf(false) }
    var resolveError by remember { mutableStateOf<String?>(null) }
    var sha256Input by remember { mutableStateOf("") }
    var multiFileResult by remember { mutableStateOf<HuggingFaceModelResolver.ResolveResult.MultipleFiles?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Import from Hugging Face / URL",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Enter a Hugging Face repo URL, direct file link, or model ID. For example:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .clickable {
                            onUrlInputChange("https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm")
                        }.padding(vertical = 2.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = urlInput,
                onValueChange = {
                    onUrlInputChange(it)
                    resolveError = null
                },
                placeholder = { Text("https://huggingface.co/... or owner/model", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                trailingIcon = {
                    if (urlInput.isNotBlank()) {
                        IconButton(onClick = {
                            onUrlInputChange("")
                            resolveError = null
                        }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    } else {
                        IconButton(onClick = {
                            clipboardManager.getText()?.text?.let { clipText ->
                                if (clipText.isNotBlank()) {
                                    onUrlInputChange(clipText.trim())
                                    resolveError = null
                                }
                            }
                        }) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Optional SHA-256 checksum for verifying the downloaded model file.
            OutlinedTextField(
                value = sha256Input,
                onValueChange = {
                    sha256Input = it.trim().lowercase()
                    resolveError = null
                },
                label = { Text("SHA-256 checksum (optional)", style = MaterialTheme.typography.bodySmall) },
                placeholder = { Text("64 hex characters…", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            val currentError = resolveError
            if (currentError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isResolving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Resolving Hugging Face...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Button(
                        onClick = {
                            if (urlInput.isNotBlank()) {
                                val checksum = sha256Input.trim().lowercase()
                                if (checksum.isNotEmpty() && !SHA256_HEX_REGEX.matches(checksum)) {
                                    resolveError = "SHA-256 must be 64 hexadecimal characters."
                                    return@Button
                                }
                                isResolving = true
                                resolveError = null
                                coroutineScope.launch {
                                    when (val result = HuggingFaceModelResolver.resolve(urlInput, hfToken)) {
                                        is HuggingFaceModelResolver.ResolveResult.SingleFile -> {
                                            isResolving = false
                                            onStartDownload(result.file.downloadUrl, result.file.fileName, result.file.sizeBytes, checksum)
                                            onUrlInputChange("")
                                            sha256Input = ""
                                        }
                                        is HuggingFaceModelResolver.ResolveResult.MultipleFiles -> {
                                            isResolving = false
                                            multiFileResult = result
                                        }
                                        is HuggingFaceModelResolver.ResolveResult.Error -> {
                                            isResolving = false
                                            resolveError = result.message
                                        }
                                    }
                                }
                            }
                        },
                        enabled = urlInput.isNotBlank(),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text("Resolve & Download", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    // Modal dialog for selecting target file when repository has multiple .litertlm files
    multiFileResult?.let { result ->
        AlertDialog(
            onDismissRequest = { multiFileResult = null },
            title = { Text("Select Model File") },
            text = {
                Column {
                    Text(
                        "Multiple LiteRT files were found in repo '${result.repoId}'. Please select one to download:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    result.files.forEach { file ->
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        multiFileResult = null
                                        onStartDownload(file.downloadUrl, file.fileName, file.sizeBytes, sha256Input.trim().lowercase())
                                        onUrlInputChange("")
                                        sha256Input = ""
                                    },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(file.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                Text(file.sizeDisplay, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { multiFileResult = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/**
 * Card for importing local .litertlm / .task model files via SAF (Storage Access Framework).
 */
@Composable
fun LocalSafImportCard(
    onImportModel: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) {
                onImportModel(uri)
            }
        }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "Import from Device",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Pick a local .litertlm / .task file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    filePickerLauncher.launch(
                        arrayOf(
                            "application/octet-stream",
                            "*/*",
                        ),
                    )
                },
                shape = RoundedCornerShape(50),
            ) {
                Text("Browse", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
