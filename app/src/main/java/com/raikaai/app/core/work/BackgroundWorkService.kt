package com.raikaai.app.core.work

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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.raikaai.app.MainActivity
import com.raikaai.app.R
import com.raikaai.app.util.KLog

/**
 * Keeps RaikaAI running while it works in the background.
 *
 * Android freezes background apps and cuts their network (even resolving
 * "localhost" fails), so a reply or a tool loop stalled as soon as the user
 * switched apps. While a reply is generating or a terminal command runs, this
 * foreground service shows a quiet notification and holds a partial wake
 * lock; it stops as soon as the work is done.
 */
class BackgroundWorkService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Working…"
        ensureChannel(this)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(text),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
        } catch (e: Exception) {
            KLog.e("BackgroundWork", "startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }
        val wl =
            wakeLock
                ?: (getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RaikaAI:background-work")
                    .apply { setReferenceCounted(false) }
                    .also { wakeLock = it }
        // Re-acquire resets the safety timeout on long multi-step sessions.
        wl.acquire(MAX_WAKE_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("RaikaAI")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "raikaai_background_work"
        private const val NOTIFICATION_ID = 4311
        private const val EXTRA_TEXT = "text"

        /** Safety cap; the service is stopped when the work ends. */
        private const val MAX_WAKE_MS = 60L * 60 * 1000

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Background work", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while RaikaAI finishes a reply or a command in the background"
                    setShowBadge(false)
                },
            )
        }

        /**
         * Starts the service with [text], or stops it when [text] is null.
         * Starting can be refused when the app is already in the background;
         * work then continues unprotected, as before.
         */
        fun update(
            context: Context,
            text: String?,
        ) {
            val app = context.applicationContext
            if (text == null) {
                app.stopService(Intent(app, BackgroundWorkService::class.java))
                return
            }
            runCatching {
                ensureChannel(app)
                ContextCompat.startForegroundService(
                    app,
                    Intent(app, BackgroundWorkService::class.java).putExtra(EXTRA_TEXT, text),
                )
            }.onFailure { KLog.w("BackgroundWork", "Could not start: ${it.message}") }
        }
    }
}
