package com.raikaai.app.localai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State coordinator for On-Device LiteRT LLM model downloads.
 * Coordinates between Foreground [ModelDownloadService] and UI components.
 * (Ported from KZKT.)
 */
class LocalModelDownloader private constructor(
    private val context: Context,
) {
    companion object {
        @Volatile
        private var instance: LocalModelDownloader? = null

        fun getInstance(context: Context): LocalModelDownloader =
            instance ?: synchronized(this) {
                instance ?: LocalModelDownloader(context.applicationContext).also { instance = it }
            }
    }

    sealed class DownloadState {
        data object Idle : DownloadState()

        data class Downloading(
            val progress: Float,
            val downloadedBytes: Long,
            val totalBytes: Long,
            val speedBps: Long,
            val fileName: String = "",
            val displayName: String = "",
        ) : DownloadState()

        data object Completed : DownloadState()

        data class Error(
            val message: String,
            val fileName: String = "",
        ) : DownloadState()
    }

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    fun startDownload(model: LocalAiModel) {
        updateState(model.id, DownloadState.Downloading(0f, 0L, model.sizeBytes, 0L, model.fileName, model.name))
        try {
            ModelDownloadService.start(
                context = context,
                id = model.id,
                name = model.name,
                fileName = model.fileName,
                downloadUrl = model.downloadUrl,
                sizeBytes = model.sizeBytes,
                expectedSha256 = model.expectedSha256,
            )
        } catch (e: Exception) {
            Log.e("LocalModelDownloader", "startDownload failed", e)
            updateState(model.id, DownloadState.Error(e.localizedMessage ?: "Failed to start service", model.fileName))
        }
    }

    fun startCustomDownload(
        id: String,
        fileName: String,
        downloadUrl: String,
        expectedSizeBytes: Long = 0L,
        expectedSha256: String? = null,
    ) {
        updateState(id, DownloadState.Downloading(0f, 0L, expectedSizeBytes, 0L, fileName, fileName))
        try {
            ModelDownloadService.start(
                context = context,
                id = id,
                name = fileName,
                fileName = fileName,
                downloadUrl = downloadUrl,
                sizeBytes = expectedSizeBytes,
                expectedSha256 = expectedSha256,
            )
        } catch (e: Exception) {
            Log.e("LocalModelDownloader", "startCustomDownload failed", e)
            updateState(id, DownloadState.Error(e.localizedMessage ?: "Failed to start service", fileName))
        }
    }

    fun cancelDownload(modelId: String) {
        try {
            ModelDownloadService.cancel(context, modelId)
        } catch (e: Exception) {
            Log.e("LocalModelDownloader", "cancelDownload failed", e)
        }
        updateState(modelId, DownloadState.Idle)
    }

    fun onServiceDownloading(
        id: String,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBps: Long,
        fileName: String = "",
        displayName: String = "",
    ) {
        val prev = _downloadStates.value[id] as? DownloadState.Downloading
        val fn = if (fileName.isNotBlank()) fileName else (prev?.fileName ?: "")
        val dn = if (displayName.isNotBlank()) displayName else (prev?.displayName ?: "")
        updateState(id, DownloadState.Downloading(progress, downloadedBytes, totalBytes, speedBps, fn, dn))
    }

    fun onServiceCompleted(id: String) {
        updateState(id, DownloadState.Completed)
    }

    fun onServiceCancelled(id: String) {
        updateState(id, DownloadState.Idle)
    }

    fun onServiceError(
        id: String,
        errorMessage: String,
        fileName: String = "",
    ) {
        updateState(id, DownloadState.Error(errorMessage, fileName))
    }

    private fun updateState(
        modelId: String,
        state: DownloadState,
    ) {
        _downloadStates.update { it + (modelId to state) }
    }
}
