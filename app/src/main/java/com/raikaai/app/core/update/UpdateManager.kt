package com.raikaai.app.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.raikaai.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Self-update from GitHub Releases (same flow as kzkt-core): read the latest
 * release, pick the APK for this phone's ABI, download it into the cache with
 * resume, check its SHA-256 against the release's checksums file, then hand it
 * to the system installer. Android itself refuses an APK signed with another
 * key, so only official builds can replace the app.
 */
object UpdateManager {
    private const val REPO = "kouzen-neo/raikaai"
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    private const val FILE_PREFIX = "raikaai-update-"

    data class Release(
        val version: String,
        val notes: String,
        val apkName: String,
        val apkUrl: String,
        val apkSize: Long,
        val checksumsUrl: String?,
        val pageUrl: String,
    )

    sealed class State {
        data object Idle : State()
        data object Checking : State()
        data object UpToDate : State()
        data class Available(val release: Release) : State()
        data class Downloading(val release: Release, val done: Long, val total: Long, val bytesPerSec: Long) : State()
        data class Ready(val release: Release, val file: File) : State()
        data class Failed(val message: String, val release: Release? = null) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val downloadHttp = http.newBuilder().readTimeout(120, TimeUnit.SECONDS).build()

    fun check() {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = State.Checking
            _state.value = runCatching { fetchLatest() }.getOrElse { State.Failed(it.message ?: "Couldn't reach GitHub") }
        }
    }

    fun download(context: Context, release: Release) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = scope.launch {
            var lastError: Exception? = null
            repeat(3) { attempt ->
                try {
                    val file = downloadOnce(app, release)
                    verify(file, release)
                    _state.value = State.Ready(release, file)
                    return@launch
                } catch (e: kotlinx.coroutines.CancellationException) {
                    _state.value = State.Available(release)
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    if (e is ChecksumException) {
                        _state.value = State.Failed(e.message ?: "Checksum mismatch", release)
                        return@launch
                    }
                    if (attempt < 2) kotlinx.coroutines.delay(2000L * (attempt + 1))
                }
            }
            _state.value = State.Failed(lastError?.message ?: "Download failed", release)
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** True when Android lets this app open the installer (Android 8+ asks per app). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" switch for RaikaAI. */
    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    // ── internals ──────────────────────────────────────────────────────

    private fun fetchLatest(): State {
        val req = Request.Builder().url(API_LATEST)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "RaikaAI-Android")
            .build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 404) return State.Failed("No releases published yet")
            if (!resp.isSuccessful) return State.Failed("GitHub responded with HTTP ${resp.code}")
            val json = JSONObject(resp.body?.string() ?: return State.Failed("Empty response from GitHub"))
            val tag = json.optString("tag_name").trim()
            if (tag.isBlank()) return State.Failed("Release has no version tag")
            if (!isNewer(tag, BuildConfig.VERSION_NAME)) return State.UpToDate
            val assets = json.optJSONArray("assets") ?: JSONArray()
            val list = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
            val apk = pickApk(list.map { it.optString("name") }, Build.SUPPORTED_ABIS.toList())
                ?.let { name -> list.first { it.optString("name") == name } }
                ?: return State.Failed("This release has no APK for your phone (${Build.SUPPORTED_ABIS.firstOrNull()})")
            return State.Available(
                Release(
                    version = tag.removePrefix("v"),
                    notes = json.optString("body").trim().take(2000),
                    apkName = apk.optString("name"),
                    apkUrl = apk.optString("browser_download_url"),
                    apkSize = apk.optLong("size", 0L),
                    checksumsUrl = list.firstOrNull { it.optString("name") == "checksums-sha256.txt" }?.optString("browser_download_url"),
                    pageUrl = json.optString("html_url"),
                ),
            )
        }
    }

    private suspend fun downloadOnce(context: Context, release: Release): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles { f -> f.name.startsWith(FILE_PREFIX) && f.name != "$FILE_PREFIX${release.version}.apk" }?.forEach { it.delete() }
        val file = File(dir, "$FILE_PREFIX${release.version}.apk")
        val existing = if (file.exists()) file.length() else 0L
        if (release.apkSize > 0 && existing == release.apkSize) return file
        if (release.apkSize > 0 && existing > release.apkSize) file.delete()

        val reqB = Request.Builder().url(release.apkUrl).header("User-Agent", "RaikaAI-Android")
        val have = if (file.exists()) file.length() else 0L
        if (have > 0) reqB.header("Range", "bytes=$have-")
        val call = downloadHttp.newCall(reqB.build())
        val hook = currentCoroutineContext()[Job]?.invokeOnCompletion { if (it != null) call.cancel() }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Download failed: HTTP ${resp.code}")
                val resume = resp.code == 206
                val body = resp.body ?: throw IOException("Empty download")
                val base = if (resume) have else 0L
                val total = if (body.contentLength() > 0) base + body.contentLength() else release.apkSize
                val start = System.nanoTime()
                var session = 0L
                var lastTick = 0L
                FileOutputStream(file, resume).use { out ->
                    val input = body.byteStream()
                    val buf = ByteArray(64 * 1024)
                    while (currentCoroutineContext().isActive) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        session += n
                        val now = System.nanoTime()
                        if (now - lastTick > 250_000_000L) {
                            lastTick = now
                            val secs = (now - start) / 1e9
                            _state.value = State.Downloading(release, base + session, total, if (secs > 0.5) (session / secs).toLong() else 0L)
                        }
                    }
                }
                currentCoroutineContext()[Job]?.let { if (!it.isActive) throw kotlinx.coroutines.CancellationException() }
                if (total > 0 && file.length() < total) throw IOException("Download ended early")
            }
        } finally {
            hook?.dispose()
        }
        return file
    }

    private class ChecksumException(msg: String) : IOException(msg)

    private fun verify(file: File, release: Release) {
        val url = release.checksumsUrl ?: return // older releases have no checksums file
        val text = http.newCall(Request.Builder().url(url).header("User-Agent", "RaikaAI-Android").build())
            .execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("Couldn't fetch release checksums (HTTP ${resp.code})")
                }
                resp.body?.string() ?: throw IOException("Empty release checksums file")
            }
        checksumProblem(text, release.apkName, sha256(file))?.let { problem ->
            file.delete()
            throw ChecksumException(problem)
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // ── pure helpers (unit tested) ─────────────────────────────────────

    /** "v0.4.10" vs "0.4.9" style comparison on numeric segments. */
    fun isNewer(remote: String, current: String): Boolean {
        fun parse(v: String) = v.trim().removePrefix("v").split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        val a = parse(remote)
        val b = parse(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** The APK for the first supported ABI, else a universal one (no ABI in its name). */
    fun pickApk(names: List<String>, abis: List<String>): String? {
        val apks = names.filter { it.endsWith(".apk", ignoreCase = true) }
        abis.forEach { abi -> apks.firstOrNull { it.contains("-$abi") }?.let { return it } }
        val known = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        return apks.firstOrNull { n -> known.none { n.contains("-$it") } }
    }

    /**
     * Fail-closed check of a downloaded APK against the release checksums:
     * null when it matches, otherwise the reason to refuse the install.
     */
    fun checksumProblem(checksums: String, apkName: String, actualSha256: String): String? {
        val expected = expectedSha256(checksums, apkName)
            ?: return "Release checksums file does not list $apkName."
        return if (actualSha256.equals(expected, ignoreCase = true)) {
            null
        } else {
            "The downloaded file doesn't match the release checksum. It was deleted; try again."
        }
    }

    /** Reads `<sha256>  <file>` lines (sha256sum format). */
    fun expectedSha256(checksums: String, fileName: String): String? =
        checksums.lineSequence()
            .map { it.trim().split(Regex("\\s+"), limit = 2) }
            .firstOrNull { it.size == 2 && it[1].removePrefix("*") == fileName }
            ?.get(0)
            ?.takeIf { it.length == 64 }
}
