package com.raikaai.app.core.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.raikaai.app.MainActivity
import com.raikaai.app.R
import com.raikaai.app.core.engine.LiteRtEngineManager
import com.raikaai.app.data.Settings
import com.raikaai.app.data.SettingsRepository
import com.raikaai.app.localai.LocalAiCatalog
import com.raikaai.app.localai.LocalModelManager
import com.raikaai.app.util.KLog
import com.raikaai.app.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground Service hosting the embedded OpenAI-compatible /v1 server.
 *
 * Keeps the process (and the loaded LLM) alive while external clients talk to
 * http://127.0.0.1:<port>/v1 — or http://<lan-ip>:<port>/v1 in bind-all mode.
 */
class ChatServerService : Service() {
    companion object {
        const val ACTION_START = "com.raikaai.app.action.START_CHAT_SERVER"
        const val ACTION_STOP = "com.raikaai.app.action.STOP_CHAT_SERVER"

        const val CHANNEL_ID = "raikaai_chat_server"
        const val NOTIFICATION_ID = 3001

        private const val PREFS_NAME = "raikaai_chat_server"
        private const val KEY_WAS_RUNNING = "was_running"

        /** Safety timeout for the generation WakeLock; always released earlier when idle. */
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        @Volatile
        private var channelCreated = false

        fun start(context: Context) {
            ensureNotificationChannel(context)
            val intent = Intent(context, ChatServerService::class.java).apply { action = ACTION_START }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, ChatServerService::class.java).apply { action = ACTION_STOP }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e("ChatServerService", "Failed to send stop intent", e)
                OpenAiServer.stop()
            }
        }

        private fun serverPrefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        /** True if the server was explicitly started and never explicitly stopped. */
        fun wasRunning(context: Context): Boolean =
            serverPrefs(context).getBoolean(KEY_WAS_RUNNING, false)

        private fun setWasRunning(context: Context, running: Boolean) {
            serverPrefs(context).edit().putBoolean(KEY_WAS_RUNNING, running).apply()
        }

        private fun ensureNotificationChannel(context: Context) {
            if (channelCreated) return
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel =
                        NotificationChannel(
                            CHANNEL_ID,
                            "Local Server",
                            NotificationManager.IMPORTANCE_LOW,
                        ).apply {
                            description = "OpenAI-compatible local server status"
                            setShowBadge(false)
                        }
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    manager?.createNotificationChannel(channel)
                }
                channelCreated = true
            } catch (e: Exception) {
                Log.e("ChatServerService", "Failed to create notification channel", e)
            }
        }

        /** Resolves the model file path from settings (preset id, else custom path). */
        fun resolveModelPath(
            context: Context,
            settings: Settings,
        ): String? {
            if (settings.customModelPath.isNotBlank()) return settings.customModelPath
            val preset = LocalAiCatalog.getModelById(settings.activeModelId)
            if (preset != null) {
                val file = LocalModelManager(context).getModelFile(preset)
                if (file.exists()) return file.absolutePath
            }
            // Fall back to any installed model.
            return LocalModelManager(context).getInstalledModels().firstOrNull()?.absolutePath
        }

        fun buildConfig(
            context: Context,
            settings: Settings,
        ): OpenAiServer.Config? {
            val modelPath = resolveModelPath(context, settings) ?: return null
            return OpenAiServer.Config(
                port = settings.serverPort,
                bindAll = settings.serverBindAll,
                authToken = settings.serverAuthToken.trim(),
                systemPrompt = settings.systemPrompt,
                temperature = settings.temperature,
                topK = settings.topK,
                topP = settings.topP,
                maxTokens = settings.maxTokens,
                contextWindow = settings.contextWindowTokens,
                backend = settings.backend,
                modelPath = modelPath,
                enableThinking = settings.enableThinking,
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                setWasRunning(this, false)
                releaseWakeLock()
                OpenAiServer.stop()
                stopForegroundNotification()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> setWasRunning(this, true)
            else -> {
                // Null intent means the system restarted the service (START_STICKY).
                // Restore the previous state: only come back up if we were running.
                if (intent != null || !wasRunning(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                KLog.d("ChatServerService", "Restarted by system; restoring previous server state")
            }
        }

        ensureNotificationChannel(this)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification("Starting local server…"),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                },
            )
        } catch (e: Exception) {
            Log.e("ChatServerService", "startForeground failed", e)
        }

        scope.launch {
            try {
                val settings = SettingsRepository(this@ChatServerService).settingsFlow.first()
                val config = buildConfig(this@ChatServerService, settings)
                if (config == null) {
                    updateNotification("No model downloaded — open RaikaAI to download one")
                    KLog.w("ChatServerService", "Server started without a model; /v1 requests will fail")
                    return@launch
                }
                OpenAiServer.start(config, LiteRtEngineManager.getInstance(this@ChatServerService))
                when (val st = OpenAiServer.status.value) {
                    is OpenAiServer.Status.Running ->
                        updateNotification(
                            "http://${if (st.bindAll) lanHint() else "127.0.0.1"}:${st.port}/v1",
                        )
                    is OpenAiServer.Status.Error -> updateNotification("Failed: ${st.message}")
                    else -> Unit
                }
            } catch (e: Exception) {
                KLog.e("ChatServerService", "Server startup error", e)
                updateNotification("Server error: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        observeServerActivity()
        return START_STICKY
    }

    @Volatile
    private var activityObserverStarted = false

    /**
     * Holds a partial WakeLock while generations are in flight so Doze cannot
     * suspend the CPU mid-generation when the screen is off; released when idle.
     */
    private fun observeServerActivity() {
        if (activityObserverStarted) return
        activityObserverStarted = true
        scope.launch {
            OpenAiServer.activeRequests.collect { active ->
                if (active > 0) acquireWakeLock() else releaseWakeLock()
            }
        }
    }

    private val wakeLockLock = Any()
    private var wakeLock: PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        synchronized(wakeLockLock) {
            try {
                var wl = wakeLock
                if (wl == null) {
                    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
                    wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RaikaAI:ServerGeneration")
                    wakeLock = wl
                }
                if (wl.isHeld) wl.release() // reset the safety timeout
                wl.acquire(WAKE_LOCK_TIMEOUT_MS)
            } catch (e: Exception) {
                Log.e("ChatServerService", "Failed to acquire wake lock", e)
            }
        }
    }

    private fun releaseWakeLock() {
        synchronized(wakeLockLock) {
            try {
                wakeLock?.let { if (it.isHeld) it.release() }
            } catch (_: Exception) {
            }
            wakeLock = null
        }
    }

    private fun lanHint(): String =
        // Delegate to the shared helper so the LAN-address rules stay in one place.
        NetworkUtils.getLocalIpAddress(this) ?: "0.0.0.0"

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val openPendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val stopIntent = Intent(this, ChatServerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent =
            PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("RaikaAI local server")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .addAction(R.mipmap.ic_launcher, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (e: Exception) {
            Log.e("ChatServerService", "Failed to update notification", e)
        }
    }

    private fun stopForegroundNotification() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        OpenAiServer.stop()
        scope.cancel()
        super.onDestroy()
    }
}
