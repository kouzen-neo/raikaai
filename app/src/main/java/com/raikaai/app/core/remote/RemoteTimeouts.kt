package com.raikaai.app.core.remote

import android.content.Context
import android.content.SharedPreferences
import com.raikaai.app.util.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * How long a remote chat request may wait for the next bytes before it gives
 * up. Long answers from slow providers or proxies (some ask for 300-600 s,
 * especially for Claude) used to be cut off by the fixed 120 s read timeout.
 */
object RemoteTimeouts {
    /** Choices offered in Settings, in seconds. */
    val OPTIONS = listOf(120, 300, 600, 1800)
    const val DEFAULT_SECONDS = 300

    private const val KEY = "read_timeout_s"
    private var prefs: SharedPreferences? = null
    private val _seconds = MutableStateFlow(DEFAULT_SECONDS)
    val seconds: StateFlow<Int> = _seconds.asStateFlow()

    @Volatile private var cached: Pair<Int, OkHttpClient>? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("remote_network", Context.MODE_PRIVATE)
        prefs = p
        _seconds.value = p.getInt(KEY, DEFAULT_SECONDS).coerceIn(30, 3600)
    }

    fun set(seconds: Int) {
        val s = seconds.coerceIn(30, 3600)
        _seconds.value = s
        prefs?.edit()?.putInt(KEY, s)?.apply()
    }

    /** Streaming client with the chosen read timeout; shares the base pool. */
    fun client(): OkHttpClient {
        val s = _seconds.value
        cached?.let { (sec, c) -> if (sec == s) return c }
        val c = NetworkUtils.HttpClient.streamingClient.newBuilder()
            .readTimeout(s.toLong(), TimeUnit.SECONDS)
            .writeTimeout(maxOf(60, s / 2).toLong(), TimeUnit.SECONDS)
            .build()
        cached = s to c
        return c
    }

    /** "5 min", "30 min", "2 min". */
    fun label(seconds: Int): String =
        if (seconds % 60 == 0) "${seconds / 60} min" else "$seconds s"
}
