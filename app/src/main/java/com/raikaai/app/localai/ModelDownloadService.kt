package com.raikaai.app.localai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.raikaai.app.MainActivity
import com.raikaai.app.R
import com.raikaai.app.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Foreground Service for downloading On-Device AI models in the background.
 * Keeps the download and process alive even when app is backgrounded or device is locked,
 * updating ongoing notifications with real-time speed, bytes, and percentage.
 * (Ported from KZKT.)
 */
class ModelDownloadService : Service() {
    companion object {
        const val ACTION_START = "com.raikaai.app.action.START_MODEL_DOWNLOAD"
        const val ACTION_CANCEL = "com.raikaai.app.action.CANCEL_MODEL_DOWNLOAD"

        const val CHANNEL_ID = "raikaai_model_downloads"
        const val NOTIFICATION_ID = 2002

        private const val EXTRA_ID = "extra_id"
        private const val EXTRA_NAME = "extra_name"
        private const val EXTRA_FILE_NAME = "extra_file_name"
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_SIZE_BYTES = "extra_size_bytes"
        private const val EXTRA_SHA256 = "extra_sha256"

        @Volatile
        private var channelCreated = false

        fun ensureNotificationChannel(context: Context) {
            if (channelCreated) return
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel =
                        NotificationChannel(
                            CHANNEL_ID,
                            "RaikaAI Model Downloads",
                            NotificationManager.IMPORTANCE_LOW,
                        ).apply {
                            description = "Shows offline AI model download progress"
                            setShowBadge(false)
                        }
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    manager?.createNotificationChannel(channel)
                }
                channelCreated = true
            } catch (e: Exception) {
                Log.e("ModelDownloadService", "Failed to create notification channel", e)
            }
        }

        fun start(
            context: Context,
            id: String,
            name: String,
            fileName: String,
            downloadUrl: String,
            sizeBytes: Long,
            expectedSha256: String? = null,
        ) {
            try {
                ensureNotificationChannel(context)
                val intent =
                    Intent(context, ModelDownloadService::class.java).apply {
                        action = ACTION_START
                        putExtra(EXTRA_ID, id)
                        putExtra(EXTRA_NAME, name)
                        putExtra(EXTRA_FILE_NAME, fileName)
                        putExtra(EXTRA_URL, downloadUrl)
                        putExtra(EXTRA_SIZE_BYTES, sizeBytes)
                        if (!expectedSha256.isNullOrBlank()) putExtra(EXTRA_SHA256, expectedSha256)
                    }
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e("ModelDownloadService", "Failed to start download service", e)
                LocalModelDownloader.getInstance(context).onServiceError(
                    id = id,
                    errorMessage = "Failed to start download service: ${e.localizedMessage ?: e.javaClass.simpleName}",
                    fileName = fileName,
                )
            }
        }

        fun cancel(
            context: Context,
            id: String,
        ) {
            try {
                val intent =
                    Intent(context, ModelDownloadService::class.java).apply {
                        action = ACTION_CANCEL
                        putExtra(EXTRA_ID, id)
                    }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e("ModelDownloadService", "Failed to send cancel intent", e)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    /** Per-download cancellation handle. Replaces the old single global flag. */
    private data class DownloadHandle(
        val call: AtomicReference<Call?> = AtomicReference(null),
        val cancelled: AtomicBoolean = AtomicBoolean(false),
    )

    /** Active downloads by model id — the service supports parallel downloads. */
    private val activeDownloads = ConcurrentHashMap<String, DownloadHandle>()

    /** Per-download notification ids so parallel downloads don't overwrite each other. */
    private val notificationIds = ConcurrentHashMap<String, Int>()
    private val notificationIdCounter = AtomicInteger(NOTIFICATION_ID)

    private fun notificationIdFor(id: String): Int =
        notificationIds.getOrPut(id) { notificationIdCounter.incrementAndGet() }

    private lateinit var modelManager: LocalModelManager
    private lateinit var settingsRepo: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        modelManager = LocalModelManager(this)
        settingsRepo = SettingsRepository(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                val id = intent.getStringExtra(EXTRA_ID) ?: ""
                // Signal only this download; its coroutine performs the actual
                // cleanup and state update when it observes the flag.
                activeDownloads[id]?.let { handle ->
                    handle.cancelled.set(true)
                    handle.call.get()?.cancel()
                }
                stopIfIdle()
            }
            ACTION_START -> {
                val id =
                    intent.getStringExtra(EXTRA_ID) ?: run {
                        stopIfIdle()
                        return START_NOT_STICKY
                    }
                val name = intent.getStringExtra(EXTRA_NAME) ?: id
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "$id.litertlm"
                val url =
                    intent.getStringExtra(EXTRA_URL) ?: run {
                        stopIfIdle()
                        return START_NOT_STICKY
                    }
                val sizeBytes = intent.getLongExtra(EXTRA_SIZE_BYTES, 0L)
                val expectedSha256 = intent.getStringExtra(EXTRA_SHA256)?.ifBlank { null }

                // Restart semantics: a new START for an already-running id
                // cancels the previous attempt first.
                activeDownloads[id]?.let { old ->
                    old.cancelled.set(true)
                    old.call.get()?.cancel()
                }
                val handle = DownloadHandle()
                activeDownloads[id] = handle

                ensureNotificationChannel(this)
                val notifId = notificationIdFor(id)
                val initialNotification = buildNotification(id, name, 0f, 0L, sizeBytes, 0L)
                // Only the first concurrent download needs to promote the service
                // to foreground; the rest just post their own notifications.
                if (activeDownloads.size == 1) {
                    try {
                        ServiceCompat.startForeground(
                            this,
                            notifId,
                            initialNotification,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                            } else {
                                0
                            },
                        )
                    } catch (e: Exception) {
                        Log.e("ModelDownloadService", "startForeground failed", e)
                    }
                } else {
                    updateNotification(notifId, id, name, 0f, 0L, sizeBytes, 0L)
                }

                scope.launch {
                    runDownload(id, name, fileName, url, sizeBytes, expectedSha256, handle)
                }
            }
            else -> stopIfIdle()
        }
        return START_NOT_STICKY
    }

    /**
     * Stops the foreground state and the service itself, but only when no
     * downloads are still active. Never call stopSelf() unconditionally while
     * other downloads may be running.
     */
    private fun stopIfIdle() {
        if (activeDownloads.isEmpty()) {
            stopForegroundNotification()
            stopSelf()
        }
    }

    private suspend fun runDownload(
        id: String,
        name: String,
        fileName: String,
        url: String,
        expectedSizeBytes: Long,
        expectedSha256: String?,
        handle: DownloadHandle,
    ) {
        val downloader = LocalModelDownloader.getInstance(this)
        val modelsDir = modelManager.modelsDir
        modelsDir.mkdirs()
        val notifId = notificationIdFor(id)

        val cleanFileName = LocalModelManager.sanitizeModelFileName(fileName)
        val targetFile = File(modelsDir, cleanFileName)
        // B10: key the resume temp file per download URL so resuming a
        // different URL that happens to share the basename can never append
        // foreign bytes to a stale .tmp.
        val urlKey = sha256Hex(url).take(16)
        val tempFile = File(modelsDir, "$cleanFileName.$urlKey.tmp")
        // Drop stale resume files from other URLs (and the legacy unkeyed name).
        modelsDir.listFiles { f ->
            f.isFile && f.name.startsWith("$cleanFileName.") && f.name.endsWith(".tmp") && f != tempFile
        }?.forEach { runCatching { it.delete() } }
        runCatching { File(modelsDir, "$cleanFileName.tmp").takeIf { it.exists() }?.delete() }

        // Validate the checksum format up front so we fail fast instead of
        // downloading gigabytes before discovering the hash is unusable.
        // (The throw itself lives inside the try below so a malformed checksum
        // is routed to onServiceError instead of crashing the service.)
        val normalizedSha256 = expectedSha256?.trim()?.lowercase()?.ifEmpty { null }

        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
        // Tracks whether a terminal (completion/error) notification was already
        // posted, so the finally block doesn't cancel it.
        var terminalNotificationPosted = false
        downloader.onServiceDownloading(
            id,
            if (expectedSizeBytes > 0) (existingBytes.toFloat() / expectedSizeBytes.toFloat()).coerceIn(0f, 1f) else 0f,
            existingBytes,
            expectedSizeBytes,
            0L,
            cleanFileName,
            name,
        )

        try {
            if (normalizedSha256 != null && !normalizedSha256.matches(Regex("[0-9a-f]{64}"))) {
                throw IllegalArgumentException(
                    "Invalid expected SHA-256 checksum for $name: must be 64 hex characters.",
                )
            }

            val hfToken =
                settingsRepo.settingsFlow
                    .first()
                    .huggingFaceToken
                    .trim()
            val response = executeRequestWithAuthAndRedirects(url, hfToken, existingBytes, handle)

            if (!response.isSuccessful) {
                val errorMsg =
                    when (response.code) {
                        400 -> "HTTP 400: Request format or token was rejected by server."
                        401 -> "HTTP 401: Model requires a Hugging Face Token (add token in Settings tab)."
                        403 -> "HTTP 403: Access denied. If this is a gated model, please accept its license on Hugging Face and provide an access token."
                        404 -> "HTTP 404: Model file was not found in repository."
                        416 -> "HTTP 416: Requested Range Not Satisfiable."
                        500, 502, 503 -> "HTTP ${response.code}: Server is busy."
                        else -> "HTTP ${response.code}: ${response.message}"
                    }
                response.close()
                if (response.code in listOf(400, 401, 403, 404, 416)) {
                    if (tempFile.exists()) tempFile.delete()
                }
                throw IllegalStateException(errorMsg)
            }

            val isPartial = response.code == 206
            val body = response.body ?: throw IllegalStateException("Empty response body")
            val contentLen = body.contentLength()

            val downloadedStart = if (isPartial) existingBytes else 0L
            val totalBytes =
                when {
                    isPartial && contentLen > 0 -> existingBytes + contentLen
                    contentLen > 0 -> contentLen
                    expectedSizeBytes > 0 -> expectedSizeBytes
                    else -> 1_000_000_000L
                }

            var downloaded = downloadedStart
            var lastUpdate = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L
            var currentSpeed = 0L

            java.io.BufferedInputStream(body.byteStream(), 256 * 1024).use { input ->
                java.io.BufferedOutputStream(FileOutputStream(tempFile, isPartial), 256 * 1024).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (handle.cancelled.get()) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        bytesSinceLastUpdate += read

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastUpdate
                        if (elapsed >= 500) {
                            val instantSpeed = (bytesSinceLastUpdate * 1000L) / maxOf(1L, elapsed)
                            currentSpeed =
                                if (currentSpeed == 0L) {
                                    instantSpeed
                                } else {
                                    ((currentSpeed * 0.70f) + (instantSpeed * 0.30f)).toLong()
                                }
                            lastUpdate = now
                            bytesSinceLastUpdate = 0L

                            val progress = (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            downloader.onServiceDownloading(id, progress, downloaded, totalBytes, currentSpeed, cleanFileName, name)
                            updateNotification(notifId, id, name, progress, downloaded, totalBytes, currentSpeed)
                        }
                    }
                    output.flush()
                }
            }

            if (handle.cancelled.get()) {
                if (tempFile.exists()) tempFile.delete()
                downloader.onServiceCancelled(id)
            } else {
                if (targetFile.exists()) targetFile.delete()
                val renameSuccess = tempFile.renameTo(targetFile)
                val saved =
                    when {
                        renameSuccess -> true
                        targetFile.exists() && targetFile.length() > 0 -> true
                        else -> {
                            try {
                                tempFile.copyTo(targetFile, overwrite = true)
                                tempFile.delete()
                                true
                            } catch (e: Exception) {
                                Log.e("ModelDownloadService", "Failed to copy temp file to target", e)
                                throw IllegalStateException("Failed to save downloaded model file: ${e.localizedMessage}")
                            }
                        }
                    }
                if (!saved) throw IllegalStateException("Failed to save downloaded model file.")

                // B22: never mark a 0-byte or truncated file as Completed.
                val finalSize = targetFile.length()
                if (finalSize <= 0L) {
                    targetFile.delete()
                    throw IllegalStateException("Downloaded file is empty; deleted.")
                }
                if (expectedSizeBytes > 0 && finalSize < expectedSizeBytes) {
                    targetFile.delete()
                    throw IllegalStateException(
                        "Download incomplete: received $finalSize of $expectedSizeBytes expected bytes; deleted.",
                    )
                }

                // Optional integrity check: delete the file loudly on mismatch.
                if (normalizedSha256 != null) {
                    downloader.onServiceDownloading(id, 1f, downloaded, totalBytes, 0L, cleanFileName, "$name (verifying…)")
                    updateNotification(notifId, id, name, 1f, downloaded, totalBytes, 0L)
                    if (!verifySha256(targetFile, normalizedSha256)) {
                        targetFile.delete()
                        throw IllegalStateException(
                            "Checksum mismatch: the downloaded file failed SHA-256 verification and was deleted. " +
                                "It may be corrupted or from an unexpected source.",
                        )
                    }
                }

                downloader.onServiceCompleted(id)
                terminalNotificationPosted = true
                showCompletionNotification(notifId, name)
            }
        } catch (e: CancellationException) {
            if (tempFile.exists()) tempFile.delete()
            downloader.onServiceCancelled(id)
        } catch (e: Exception) {
            if (handle.cancelled.get()) {
                // call.cancel() surfaces as IOException; report as cancellation,
                // not as a download error.
                if (tempFile.exists()) tempFile.delete()
                downloader.onServiceCancelled(id)
            } else {
                Log.e("ModelDownloadService", "Download error for $id", e)
                val msg = e.localizedMessage ?: "Download failed"
                downloader.onServiceError(id, msg, cleanFileName)
                terminalNotificationPosted = true
                showErrorNotification(notifId, name, msg)
            }
        } finally {
            activeDownloads.remove(id)
            if (!terminalNotificationPosted) {
                // Progress notification only: remove it. Completion/error
                // notifications posted above must stay visible.
                notificationIds.remove(id)?.let { finishedId ->
                    (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(finishedId)
                }
            } else {
                notificationIds.remove(id)
            }
            stopIfIdle()
        }
    }

    /** Short SHA-256 hex of a string (used to key temp files per URL). */
    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /**
     * Parses [url], requiring the https scheme and a publicly-routable host.
     * Called on the initial URL and on every redirect hop, so a redirect can
     * neither downgrade to http nor bounce to an internal address (B11/SSRF).
     */
    private fun requirePublicHttpsUrl(url: String): HttpUrl {
        val httpUrl = url.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid download URL")
        if (!httpUrl.isHttps) {
            throw IllegalArgumentException("Only HTTPS download URLs are allowed")
        }
        assertPublicHost(httpUrl.host)
        return httpUrl
    }

    /** Rejects hosts that resolve to loopback / private / link-local / multicast IPs. */
    private fun assertPublicHost(host: String) {
        val addresses =
            try {
                InetAddress.getAllByName(host)
            } catch (e: UnknownHostException) {
                throw IllegalArgumentException("Cannot resolve download host: $host")
            }
        for (addr in addresses) {
            if (com.raikaai.app.util.NetworkUtils.isNonPublicAddress(addr)) {
                throw IllegalArgumentException("Download host resolves to a non-public IP address: $host")
            }
        }
    }

    /** Computes the SHA-256 hex digest of [file] and compares it to [expectedHex]. */
    private fun verifySha256(
        file: File,
        expectedHex: String,
    ): Boolean =
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered(256 * 1024).use { input ->
                val buffer = ByteArray(256 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            actual.equals(expectedHex.trim().lowercase(), ignoreCase = false)
        } catch (e: Exception) {
            Log.e("ModelDownloadService", "SHA-256 verification failed", e)
            false
        }

    private fun executeRequestWithAuthAndRedirects(
        initialUrl: String,
        hfToken: String,
        rangeStart: Long = 0L,
        handle: DownloadHandle,
    ): Response {
        var currentUrl = initialUrl
        var attempts = 0
        while (attempts < 8) {
            attempts++
            // Re-validated on every hop: https-only, public-IP-only (B11).
            val httpUrl = requirePublicHttpsUrl(currentUrl)
            val host = httpUrl.host
            val isHfHost = host.equals("huggingface.co", ignoreCase = true) ||
                host.equals("hf.co", ignoreCase = true) ||
                host.equals("api.huggingface.co", ignoreCase = true)

            val queryNames = httpUrl.queryParameterNames
            val isSignedUrl = queryNames.any { name ->
                name.contains("Signature", ignoreCase = true) ||
                    name.contains("Policy", ignoreCase = true) ||
                    name.contains("X-Amz", ignoreCase = true) ||
                    name.contains("Key-Pair-Id", ignoreCase = true)
            }
            val isCdn = host.contains("cdn") || host.contains("amazonaws") || host.contains("cloudfront") || isSignedUrl

            val reqBuilder = Request.Builder().url(httpUrl)
            if (isHfHost && !isCdn && hfToken.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer $hfToken")
            }
            if (rangeStart > 0) {
                reqBuilder.header("Range", "bytes=$rangeStart-")
            }
            reqBuilder.header("Accept-Encoding", "identity")
            reqBuilder.header("User-Agent", "RaikaAI-App/0.1.0 (Android; LiteRT-Downloader)")

            val call = client.newCall(reqBuilder.build())
            handle.call.set(call)
            val response = call.execute()
            if (response.isRedirect) {
                val location = response.header("Location")
                response.close()
                if (location.isNullOrBlank()) {
                    throw IllegalStateException("Server redirected without a Location header")
                }
                currentUrl = httpUrl.resolve(location)?.toString() ?: location
                continue
            }
            return response
        }
        throw IllegalStateException("Too many redirects from server")
    }

    private fun buildNotification(
        id: String,
        name: String,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBps: Long,
    ): Notification {
        val pct = (progress * 100).toInt().coerceIn(0, 100)
        val doneMb = downloadedBytes.toFloat() / (1024 * 1024)
        val totalMb = maxOf(1L, totalBytes).toFloat() / (1024 * 1024)
        val speedMb = speedBps.toFloat() / (1024 * 1024)

        val contentText = String.format(Locale.ROOT, "%.1f / %.1f MB · %.1f MB/s", doneMb, totalMb, speedMb)

        val openIntent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val openPendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val cancelIntent =
            Intent(this, ModelDownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_ID, id)
            }
        val cancelPendingIntent =
            PendingIntent.getService(
                this,
                // Unique per download so parallel downloads get independent cancel buttons.
                notificationIdFor(id),
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Downloading Model: $name")
            .setContentText(contentText)
            .setSubText("$pct%")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, pct, false)
            .setContentIntent(openPendingIntent)
            .addAction(R.mipmap.ic_launcher, "Cancel", cancelPendingIntent)
            .build()
    }

    private fun updateNotification(
        notifId: Int,
        id: String,
        name: String,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBps: Long,
    ) {
        try {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
            val notif = buildNotification(id, name, progress, downloadedBytes, totalBytes, speedBps)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(notifId, notif)
        } catch (e: Exception) {
            Log.e("ModelDownloadService", "Failed to update notification", e)
        }
    }

    private fun showCompletionNotification(
        notifId: Int,
        name: String,
    ) {
        try {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
            val openIntent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
            val openPendingIntent =
                PendingIntent.getActivity(
                    this,
                    0,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val notif =
                NotificationCompat
                    .Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Model Download Complete")
                    .setContentText("Model $name is ready for offline use.")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(openPendingIntent)
                    .build()

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(notifId, notif)
        } catch (e: Exception) {
            Log.e("ModelDownloadService", "Failed to show completion notification", e)
        }
    }

    private fun showErrorNotification(
        notifId: Int,
        name: String,
        errorMsg: String,
    ) {
        try {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
            val notif =
                NotificationCompat
                    .Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Failed to Download $name")
                    .setContentText(errorMsg)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(notifId, notif)
        } catch (e: Exception) {
            Log.e("ModelDownloadService", "Failed to show error notification", e)
        }
    }

    private fun stopForegroundNotification() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e("ModelDownloadService", "stopForeground error", e)
        }
    }

    override fun onDestroy() {
        activeDownloads.values.forEach { handle ->
            handle.cancelled.set(true)
            try {
                handle.call.get()?.cancel()
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
        scope.cancel()
    }
}
