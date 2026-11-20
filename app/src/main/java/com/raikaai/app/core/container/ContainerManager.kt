package com.raikaai.app.core.container

import android.app.Application
import com.raikaai.app.data.SettingsRepository
import com.raikaai.app.util.KLog
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Single gateway to the Alpine PRoot userland (Fase 1–4).
 *
 * Layout under app private storage:
 * - nativeLibraryDir / libproot.so  → proot engine (extracted by installer)
 * - nativeLibraryDir / lib*_prt.so  → proot deps + loader helpers
 * - filesDir / alpine               → unpacked minirootfs (+ .installed marker)
 * - filesDir / workspace            → bind-mounted as /workspace (sole read-write dir for AI)
 *
 * Only the Alpine rootfs is downloaded (with SHA-256 verification when the
 * checksum is pinned). No PTY here (Fase 3 terminal is shell-in-a-box
 * over [run]; full PTY is a later milestone).
 */
class ContainerManager private constructor(
    private val app: Application,
    private val settingsRepo: SettingsRepository,
) {
    companion object {
        private const val TAG = "Container"
        private const val OUTPUT_CAP = 64 * 1024

        /** How long [startDaemon] waits for a startup banner / early crash. */
        private const val STARTUP_LOG_WINDOW_MS = 6_000L

        /** Startup output kept for the agent before the rest is drained away. */
        private const val MAX_STARTUP_LOG_CHARS = 4_000

        /** Loopback connect timeout per candidate port when probing. */
        private const val PROBE_TIMEOUT_MS = 300
        /**
         * Rootfs layout schema. Bump when the unpack format changes
         * (symlinks, permissions, …) so stale on-device rootfs is detected
         * and the UI forces a reinstall instead of failing cryptically.
         */
        const val ROOTFS_SCHEMA = 5

        /**
         * Minimal `curl` subset (GET only) backed by busybox wget, installed
         * to /usr/local/bin at install time. Triple-quoted: no Kotlin
         * escapes needed except ${'$'} for shell variables (none used).
         */
        const val CURL_SHIM = """#!/bin/sh
# Minimal curl subset for agents (backed by busybox wget). GET only.
head_only=0
outfile=""
url=""
next_is_outfile=0
for a in "${'$'}@"; do
    if [ "${'$'}next_is_outfile" = 1 ]; then
        outfile="${'$'}a"; next_is_outfile=0; continue
    fi
    case "${'$'}a" in
        -I|--head) head_only=1 ;;
        -o|--output) next_is_outfile=1 ;;
        -O|--remote-name) outfile="REMOTE" ;;
        -s|-S|-sS|-Ss|-L|--location|-f|--fail|-k|--insecure|-q) ;;
        -*) ;;
        *) url="${'$'}a" ;;
    esac
done
if [ -z "${'$'}url" ]; then echo "curl: no URL" >&2; exit 2; fi
if [ "${'$'}outfile" = "REMOTE" ]; then outfile="${'$'}{url##*/}"; fi
if [ "${'$'}head_only" = 1 ]; then
    if wget -q -O /dev/null "${'$'}url"; then echo "HTTP OK: ${'$'}url"; else echo "curl: failed: ${'$'}url" >&2; exit 1; fi
else
    if [ -n "${'$'}outfile" ]; then wget -q -O "${'$'}outfile" "${'$'}url"; else wget -qO- "${'$'}url"; fi
fi
"""

        @Volatile private var instance: ContainerManager? = null

        fun getInstance(app: Application, settingsRepo: SettingsRepository): ContainerManager =
            instance ?: synchronized(this) {
                instance ?: ContainerManager(app.applicationContext as Application, settingsRepo)
                    .also { instance = it }
            }
    }

    sealed interface InstallState {
        data object Idle : InstallState
        data class Downloading(val label: String, val progress: Float) : InstallState
        data class Extracting(val label: String) : InstallState
        data object Ready : InstallState
        data class Error(val message: String) : InstallState
    }

    data class RunResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
        val timedOut: Boolean = false,
    ) {
        /** Compact rendering for chat bubbles / terminal log. */
        fun render(capChars: Int = 4000): String {
            val out = (stdout.ifBlank { "(no output)" }).take(capChars)
            val err = stderr.take(1000)
            val sb = StringBuilder("```\n$out")
            if (out.length >= capChars) sb.append("\n…(truncated)")
            sb.append("\n```")
            if (err.isNotBlank()) sb.append("\nstderr:\n```\n$err\n```")
            sb.append("\nexit=$exitCode")
            if (timedOut) sb.append(" (timed out)")
            return sb.toString()
        }
    }

    enum class TermKind { CMD, OUT, ERR, SYS }
    data class TermLine(
        val kind: TermKind,
        val text: String,
        /** Styled text from the ANSI screen; null for plain lines. */
        val rich: androidx.compose.ui.text.AnnotatedString? = null,
        /** Working directory shown in the prompt of a CMD line. */
        val cwd: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runMutex = Mutex()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val alpineDir: File get() = File(app.filesDir, "alpine")
    val workspaceDir: File get() = File(app.filesDir, "workspace")
    /** Installer-extracted native libs (exec permission guaranteed). */
    val nativeLibDir: File get() = File(app.applicationInfo.nativeLibraryDir)
    val prootBin: File get() = File(nativeLibDir, "libproot.so")

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState.asStateFlow()

    private val _servedPort = MutableStateFlow<Int?>(null)
    /** Currently served localhost port, null = not serving. */
    val servedPort: StateFlow<Int?> = _servedPort.asStateFlow()

    private val _previewUrl = MutableStateFlow<String?>(null)
    /**
     * Loopback URL of the most recently started dev server (daemon), discovered
     * from its own banner or by probing. Null when nothing is listening.
     */
    val previewUrl: StateFlow<String?> = _previewUrl.asStateFlow()

    private var serveProcess: Process? = null
    private var serveJob: Job? = null
    private var installJob: Job? = null
    private val activeProcs = java.util.concurrent.ConcurrentHashMap.newKeySet<Process>()
    private var resolvLastRefresh = 0L
    private val daemons = java.util.concurrent.ConcurrentHashMap<Int, Process>()
    private var daemonSeq = 0
    private val MAX_DAEMONS = 5

    /** Marker content is "<version>#<schema>". Missing/stale → not installed. */
    private fun markerSchema(): Int? {
        val text = runCatching { File(alpineDir, ".installed").readText().trim() }.getOrNull()
            ?: return null
        return text.substringAfterLast("#", "").toIntOrNull()
    }

    /** Filesystem truth: rootfs marker with current schema + engine present. */
    fun isInstalled(): Boolean =
        markerSchema() == ROOTFS_SCHEMA && prootBin.isFile

    /** Marker exists but was written by an older unpack format → reinstall needed. */
    fun isOutdated(): Boolean {
        if (!File(alpineDir, ".installed").isFile) return false
        return markerSchema() != ROOTFS_SCHEMA
    }

    /** One-shot diagnostics for the on-device state (copy-pasteable). */
    suspend fun diagnostics(): String {
        val sb = StringBuilder()
        sb.appendLine("abi=${android.os.Build.SUPPORTED_ABIS.firstOrNull()}")
        sb.appendLine("marker=${runCatching { File(alpineDir, ".installed").readText().trim() }.getOrDefault("(none)")}")
        sb.appendLine("schema=$ROOTFS_SCHEMA installed=${isInstalled()} outdated=${isOutdated()}")
        val bb = File(alpineDir, "bin/busybox")
        sb.appendLine("busybox exists=${bb.isFile} size=${if (bb.isFile) bb.length() else -1}")
        val sh = File(alpineDir, "bin/sh")
        val shTarget = runCatching { android.system.Os.readlink(sh.absolutePath) }.getOrNull()
        sb.appendLine("bin/sh exists=${sh.exists()} isSymlink=${shTarget != null} target=${shTarget ?: "-"} guestResolves=${guestResolves("bin/sh")}")
        sb.appendLine("alpineTop=${topLevelListing()}")
        sb.appendLine("alpineEntries=${countEntries(alpineDir, 0)}")
        sb.appendLine("nativeLibs=${nativeLibDir.listFiles()?.map { it.name }?.sorted() ?: "?"}")
        sb.append(runCatching { engineProbes() }.getOrDefault("probes failed"))
        return sb.toString()
    }

    private fun isSymlink(f: File): Boolean {
        return runCatching {
            val st = android.system.Os.lstat(f.absolutePath)
            android.system.OsConstants.S_ISLNK(st.st_mode)
        }.getOrDefault(false)
    }

    private fun countEntries(dir: File, depth: Int): Int {
        if (depth > 4) return 0
        val kids = dir.listFiles() ?: return 0
        var n = kids.size
        if (n > 3000) return n
        // Never follow symlinks: avoids double counts and link cycles.
        for (k in kids) if (k.isDirectory && !isSymlink(k)) n += countEntries(k, depth + 1)
        return n
    }

    fun workspaceFiles(dir: File = workspaceDir): List<File> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    fun resolveWorkspaceFile(relativePath: String): File =
        ContainerFileOps.resolveWorkspaceFile(relativePath, workspaceDir)

    fun writeFile(relativePath: String, content: String): FileWriteResult =
        ContainerFileOps.writeFile(relativePath, content, workspaceDir)

    fun editFile(
        relativePath: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean = false,
    ): FileEditResult =
        ContainerFileOps.editFile(relativePath, oldString, newString, replaceAll, workspaceDir)

    /** Several disjoint exact replacements in one call (see [ContainerFileOps.multiEdit]). */
    fun multiEditFile(
        relativePath: String,
        edits: List<Pair<String, String>>,
    ): FileEditResult = ContainerFileOps.multiEdit(relativePath, edits, workspaceDir)

    fun readFile(
        relativePath: String,
        offset: Int = 1,
        limit: Int = 400,
    ): FileReadResult =
        ContainerFileOps.readFile(relativePath, offset, limit, workspaceDir)

    // ── Install / wipe ────────────────────────────────────────────────

    /**
     * Verifies bundled native libs, downloads the rootfs, unpacks to
     * [alpineDir], writes resolv.conf + marker, flips settings.
     * Safe to call twice; concurrent calls are ignored.
     */
    fun install(manifest: ContainerManifest = ContainerManifest()) {
        if (installJob?.isActive == true) return
        installJob = scope.launch {
            try {
                workspaceDir.mkdirs()
                val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
                val bundled = bundledProotForAbi(abi)
                    ?: throw IllegalStateException("Unsupported ABI: $abi (need arm64-v8a or x86_64)")

                _installState.value = InstallState.Downloading("proot engine", 0f)
                verifyBundledLibs(bundled)
                _installState.value = InstallState.Downloading("proot engine", 1f)

                val tmpTar = File(app.cacheDir, "alpine-minirootfs.tar.gz")
                if (abi == "arm64-v8a" && copyBundledRootfs(tmpTar)) {
                    KLog.d(TAG, "Using the rootfs bundled in the APK")
                } else {
                    _installState.value = InstallState.Downloading("alpine-minirootfs", 0f)
                    downloadToFile(manifest.rootfsUrl, tmpTar, expectedSha256 = manifest.rootfsSha256.ifBlank { null }) { done, total ->
                        _installState.value = InstallState.Downloading(
                            "alpine-minirootfs",
                            if (total > 0) done.toFloat() / total else 0f,
                        )
                    }
                }

                _installState.value = InstallState.Extracting("alpine-minirootfs")
                // Clean slate: an older unpack may lack symlinks or be partial.
                alpineDir.deleteRecursively()
                alpineDir.mkdirs()
                val entries = unpackTarGz(tmpTar, alpineDir)
                tmpTar.delete()
                makeBusyboxExecutable()
                verifyRootfs(entries)

                File(alpineDir, "etc").mkdirs()
                // Real DNS comes after unpack (needs etc/ to exist first).
                refreshResolvConf()
                // apk expects these to exist (minirootfs omits some).
                listOf("tmp", "var/cache/apk", "etc/apk", "lib/apk/db", "root").forEach {
                    File(alpineDir, it).mkdirs()
                }
                installCurlShim()
                File(alpineDir, ".installed").writeText("${manifest.alpineVersion}#$ROOTFS_SCHEMA")
                settingsRepo.setAlpineInstalled(true)
                settingsRepo.setAlpineVersion(manifest.alpineVersion)
                // Smoke test: Ready must mean the shell actually runs.
                val smoke = run("echo ok", timeoutSec = 20)
                if (smoke.exitCode != 0 || !smoke.stdout.contains("ok")) {
                    File(alpineDir, ".installed").delete()
                    settingsRepo.setAlpineInstalled(false)
                    settingsRepo.setAlpineVersion("")
                    throw IllegalStateException(
                        "Shell smoke test failed (exit=${smoke.exitCode}): " +
                            (smoke.stderr.ifBlank { smoke.stdout }.take(300).ifBlank { "no output" }),
                    )
                }
                _installState.value = InstallState.Ready
                KLog.d(TAG, "Alpine ${manifest.alpineVersion} installed ($entries entries)")
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                KLog.e(TAG, "Install failed", t)
                _installState.value = InstallState.Error(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    /** True when this build carries the rootfs (no download needed). */
    fun hasBundledRootfs(): Boolean =
        android.os.Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a" &&
            runCatching { app.assets.open(BUNDLED_ROOTFS_ASSET).use { true } }.getOrDefault(false)

    /**
     * Copies the rootfs shipped in the APK to [dest] and checks its SHA-256.
     * False when the asset is missing or damaged (the caller downloads instead).
     */
    private suspend fun copyBundledRootfs(dest: File): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                _installState.value = InstallState.Extracting("alpine-minirootfs")
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                app.assets.open(BUNDLED_ROOTFS_ASSET).use { input ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            digest.update(buf, 0, n)
                            out.write(buf, 0, n)
                        }
                    }
                }
                val hex = digest.digest().joinToString("") { "%02x".format(it) }
                if (hex != BUNDLED_ROOTFS_SHA256) {
                    dest.delete()
                    KLog.w(TAG, "Bundled rootfs checksum mismatch; downloading instead")
                    false
                } else {
                    true
                }
            }.getOrElse {
                dest.delete()
                false
            }
        }

    /**
     * Installs the environment on first launch when the rootfs is bundled,
     * so the terminal is ready without a download or an Install tap.
     */
    fun autoInstallIfBundled() {
        if (isInstalled() || isOutdated() || installJob?.isActive == true) return
        if (!hasBundledRootfs()) return
        install()
    }

    fun cancelInstall() {
        installJob?.cancel()
        _installState.value = InstallState.Idle
    }

    /** Deletes the rootfs but keeps workspace/ (user files survive). */
    suspend fun wipe() {
        stopServe()
        withContext(Dispatchers.IO) {
            alpineDir.deleteRecursively()
        }
        settingsRepo.setAlpineInstalled(false)
        settingsRepo.setAlpineVersion("")
        _installState.value = InstallState.Idle
    }

    // ── Run ───────────────────────────────────────────────────────────

    /**
     * Runs [command] via `proot -r alpine -b workspace:/workspace -0 -w /workspace sh -c`.
     * Single-flight (mutex): concurrent callers queue. Timeout kills the process.
     */
    suspend fun run(
        command: String,
        timeoutSec: Long = 15,
        keepTailOutput: Boolean = false,
        extraEnv: Map<String, String> = emptyMap(),
    ): RunResult {
        if (!isInstalled()) {
            val hint = if (isOutdated()) {
                "Linux Environment format is outdated. Press Install again to update it."
            } else {
                "Linux Environment is not installed. Open Models Hub → Linux Environment → Install."
            }
            return RunResult("", hint, -1)
        }
        val clean = command.trim()
        if (clean.isEmpty()) return RunResult("", "empty command", -1)
        workspaceDir.mkdirs()
        // Networks change mid-session (WiFi/data/VPN flips): refresh guest
        // DNS when the copy is older than 15 minutes. Cheap system call,
        // avoids stale-DNS "network unreachable" ghosts.
        if (isInstalled() && System.currentTimeMillis() - resolvLastRefresh > 15 * 60_000L) {
            runCatching { refreshResolvConf() }
        }
        return execProot(
            args = baseArgs() + listOf("/bin/sh", "-c", clean),
            extraEnv = extraEnv,
            timeoutSec = timeoutSec,
            keepTailOutput = keepTailOutput,
        )
    }

    /** Shared proot invocation prefix: rootfs + binds + fake root + workdir. */
    /**
     * Raika's home (SOUL/AGENTS/MEMORY.md, mcp.json, chats/) lives in app
     * storage and is mounted at /root/.raika, so it survives a rootfs reset
     * and the agent can read and edit it from the terminal.
     */
    val raikaHomeDir: File get() = File(app.filesDir, "agent")

    /** True when the user turned on phone storage and granted all-files access. */
    fun phoneStorageMounted(): Boolean =
        runCatching {
            android.os.Build.VERSION.SDK_INT >= 30 &&
                android.os.Environment.isExternalStorageManager() &&
                app.getSharedPreferences("container", android.content.Context.MODE_PRIVATE).getBoolean("phone_storage", false)
        }.getOrDefault(false)

    fun setPhoneStorageEnabled(on: Boolean) {
        app.getSharedPreferences("container", android.content.Context.MODE_PRIVATE).edit().putBoolean("phone_storage", on).apply()
    }

    fun phoneStorageWanted(): Boolean =
        app.getSharedPreferences("container", android.content.Context.MODE_PRIVATE).getBoolean("phone_storage", false)

    private fun phoneStorageArgs(): List<String> {
        if (!phoneStorageMounted()) return emptyList()
        val root = android.os.Environment.getExternalStorageDirectory().absolutePath
        return listOf("-b", "$root:/sdcard", "-b", "$root:/storage/emulated/0")
    }

    private fun baseArgs(): List<String> {
        workspaceDir.mkdirs()
        raikaHomeDir.mkdirs()
        return listOf(
            "-r", alpineDir.absolutePath,
            "-b", "${workspaceDir.absolutePath}:/workspace",
            "-b", "${raikaHomeDir.absolutePath}:/root/.raika",
        ) + phoneStorageArgs() + listOf(
            "-b", "/dev",
            "-b", "/proc",
            "-0",
            "-w", "/workspace",
        )
    }

    /**
     * Raw proot invocation (single-flight). Used by [run] and engine probes.
     * No install gate: probes must run even on broken installs.
     */
    suspend fun execProot(
        args: List<String>,
        extraEnv: Map<String, String> = emptyMap(),
        timeoutSec: Long = 15,
        keepTailOutput: Boolean = false,
    ): RunResult {
        return runMutex.withLock {
            withContext(Dispatchers.IO) {
                var proc: Process? = null
                try {
                    val pb = ProcessBuilder(listOf(prootBin.absolutePath) + args)
                    pb.redirectErrorStream(false)
                    withContainerEnv(pb)
                    extraEnv.forEach { (k, v) -> pb.environment()[k] = v }
                    proc = pb.start()
                    activeProcs.add(proc)
                    val finished = try {
                        proc.waitFor(timeoutSec, TimeUnit.SECONDS)
                    } finally {
                        activeProcs.remove(proc)
                    }
                    if (!finished) {
                        proc.destroyForcibly()
                        return@withContext RunResult(
                            readCapped(proc.inputStream, keepTailOutput),
                            readCapped(proc.errorStream, keepTailOutput),
                            -1,
                            timedOut = true,
                        )
                    }
                    RunResult(
                        readCapped(proc.inputStream, keepTailOutput),
                        readCapped(proc.errorStream, keepTailOutput),
                        proc.exitValue(),
                    )
                } catch (t: Throwable) {
                    try { proc?.destroyForcibly() } catch (_: Throwable) {}
                    RunResult("", "run failed: ${t.message ?: t.javaClass.simpleName}", -1)
                }
            }
        }
    }

    /**
     * Executes [command] with real-time stdout/stderr streaming.
     * Prevents buffer saturation for verbose programs (like vite/npm)
     * and streams lines as they are produced without killing background servers.
     */
    suspend fun runStreaming(
        command: String,
        timeoutSec: Long = 600,
        onLine: (TermKind, String) -> Unit,
    ): Int {
        if (!isInstalled()) {
            onLine(TermKind.ERR, "Linux Environment is not installed. Press Install Alpine first.")
            return -1
        }
        return withContext(Dispatchers.IO) {
            var proc: Process? = null
            try {
                workspaceDir.mkdirs()
                val pb = ProcessBuilder(
                    listOf(prootBin.absolutePath) + baseArgs() + listOf("/bin/sh", "-c", command),
                )
                pb.redirectErrorStream(false)
                withContainerEnv(pb)
                proc = pb.start()
                activeProcs.add(proc)

                val outJob = scope.launch(Dispatchers.IO) {
                    try {
                        val reader = proc.inputStream.bufferedReader()
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            withContext(Dispatchers.Main) {
                                onLine(TermKind.OUT, line)
                            }
                        }
                    } catch (_: Throwable) {}
                }

                val errJob = scope.launch(Dispatchers.IO) {
                    try {
                        val reader = proc.errorStream.bufferedReader()
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            withContext(Dispatchers.Main) {
                                onLine(TermKind.ERR, line)
                            }
                        }
                    } catch (_: Throwable) {}
                }

                val finished = try {
                    proc.waitFor(timeoutSec, TimeUnit.SECONDS)
                } finally {
                    activeProcs.remove(proc)
                }

                try {
                    kotlinx.coroutines.withTimeoutOrNull(800) {
                        outJob.join()
                        errJob.join()
                    }
                } catch (_: Throwable) {}

                if (!finished) {
                    proc.destroyForcibly()
                    withContext(Dispatchers.Main) {
                        onLine(TermKind.SYS, "[timed out after ${timeoutSec}s]")
                    }
                    return@withContext -1
                }
                proc.exitValue()
            } catch (c: kotlinx.coroutines.CancellationException) {
                try { proc?.destroyForcibly() } catch (_: Throwable) {}
                throw c
            } catch (t: Throwable) {
                try { proc?.destroyForcibly() } catch (_: Throwable) {}
                withContext(Dispatchers.Main) {
                    onLine(TermKind.ERR, "run failed: ${t.message ?: t.javaClass.simpleName}")
                }
                -1
            }
        }
    }

    /**
     * Like [runStreaming] but delivers raw output chunks (not lines), so the
     * terminal can interpret carriage returns, cursor moves and colors.
     */
    suspend fun runStreamingRaw(
        command: String,
        timeoutSec: Long = 600,
        onChunk: (TermKind, String) -> Unit,
    ): Int {
        if (!isInstalled()) {
            onChunk(TermKind.ERR, "Linux Environment is not installed. Press Install Alpine first.\n")
            return -1
        }
        return withContext(Dispatchers.IO) {
            var proc: Process? = null
            try {
                workspaceDir.mkdirs()
                val pb = ProcessBuilder(
                    listOf(prootBin.absolutePath) + baseArgs() + listOf("/bin/sh", "-c", command),
                )
                pb.redirectErrorStream(false)
                withContainerEnv(pb)
                proc = pb.start()
                activeProcs.add(proc)

                val outJob = scope.launch(Dispatchers.IO) {
                    try {
                        val reader = java.io.InputStreamReader(proc.inputStream, Charsets.UTF_8)
                        val buf = CharArray(4096)
                        while (isActive) {
                            val n = reader.read(buf)
                            if (n < 0) break
                            val chunk = String(buf, 0, n)
                            withContext(Dispatchers.Main) { onChunk(TermKind.OUT, chunk) }
                        }
                    } catch (_: Throwable) {}
                }

                val errJob = scope.launch(Dispatchers.IO) {
                    try {
                        val reader = java.io.InputStreamReader(proc.errorStream, Charsets.UTF_8)
                        val buf = CharArray(4096)
                        while (isActive) {
                            val n = reader.read(buf)
                            if (n < 0) break
                            val chunk = String(buf, 0, n)
                            withContext(Dispatchers.Main) { onChunk(TermKind.ERR, chunk) }
                        }
                    } catch (_: Throwable) {}
                }

                val finished = try {
                    proc.waitFor(timeoutSec, TimeUnit.SECONDS)
                } finally {
                    activeProcs.remove(proc)
                }

                try {
                    kotlinx.coroutines.withTimeoutOrNull(800) {
                        outJob.join()
                        errJob.join()
                    }
                } catch (_: Throwable) {}

                if (!finished) {
                    proc.destroyForcibly()
                    withContext(Dispatchers.Main) {
                        onChunk(TermKind.SYS, "\n[timed out after ${timeoutSec}s]\n")
                    }
                    return@withContext -1
                }
                proc.exitValue()
            } catch (c: kotlinx.coroutines.CancellationException) {
                try { proc?.destroyForcibly() } catch (_: Throwable) {}
                throw c
            } catch (t: Throwable) {
                try { proc?.destroyForcibly() } catch (_: Throwable) {}
                withContext(Dispatchers.Main) {
                    onChunk(TermKind.ERR, "run failed: ${t.message ?: t.javaClass.simpleName}\n")
                }
                -1
            }
        }
    }

    /** PTY helper (native/raikapty), shipped next to the proot engine. */
    val ptyBin: File get() = File(nativeLibDir, "libraikapty.so")

    fun ptyAvailable(): Boolean = ptyBin.isFile

    /**
     * Runs [command] on a real pseudo-terminal (PTY mode of the terminal):
     * programs see a tty, so full-screen apps (opencode, vim, htop), colors,
     * and line editing work. Output arrives as one stream on [onChunk] (on
     * Main); [onStdin] gets the stream for keystrokes and resize messages
     * (see [resizeMessage]). No timeout: interactive programs run until they
     * exit or the job is cancelled.
     */
    suspend fun runPty(
        command: String,
        rows: Int,
        cols: Int,
        onStdin: (java.io.OutputStream?) -> Unit,
        onKill: ((() -> Unit)?) -> Unit = {},
        onChunk: (String) -> Unit,
    ): Int {
        if (!isInstalled()) {
            onChunk("Linux Environment is not installed. Press Install Alpine first.\r\n")
            return -1
        }
        if (!ptyAvailable()) {
            onChunk("Terminal helper missing from this build (libraikapty.so).\r\n")
            return -1
        }
        return withContext(Dispatchers.IO) {
            var proc: Process? = null
            try {
                workspaceDir.mkdirs()
                val pb = ProcessBuilder(
                    listOf(ptyBin.absolutePath, rows.coerceIn(2, 300).toString(), cols.coerceIn(10, 400).toString(), prootBin.absolutePath) +
                        baseArgs() + listOf("/bin/sh", "-c", command),
                )
                pb.redirectErrorStream(true)
                withContainerEnv(pb)
                val started = pb.start()
                proc = started
                activeProcs.add(started)
                // The read below blocks while an interactive program waits for
                // keys, so cancellation (Stop, closing the tab) kills it directly.
                kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.invokeOnCompletion { cause ->
                    if (cause != null) runCatching { started.destroyForcibly() }
                }
                val stdin = started.outputStream
                withContext(Dispatchers.Main) {
                    onStdin(stdin)
                    // Ends the whole tree (proot keeps running while a program's
                    // background helpers are alive, e.g. opencode's server).
                    onKill { runCatching { started.destroyForcibly() } }
                }
                try {
                    val reader = java.io.InputStreamReader(proc.inputStream, Charsets.UTF_8)
                    val buf = CharArray(8192)
                    while (true) {
                        val n = reader.read(buf)
                        if (n < 0) break
                        val chunk = String(buf, 0, n)
                        withContext(Dispatchers.Main) { onChunk(chunk) }
                    }
                } catch (_: java.io.IOException) {
                }
                proc.waitFor()
                proc.exitValue()
            } catch (c: kotlinx.coroutines.CancellationException) {
                try { proc?.destroyForcibly() } catch (_: Throwable) {}
                throw c
            } catch (t: Throwable) {
                try { proc?.destroyForcibly() } catch (_: Throwable) {}
                withContext(Dispatchers.Main) { onChunk("run failed: ${t.message ?: t.javaClass.simpleName}\r\n") }
                -1
            } finally {
                proc?.let { activeProcs.remove(it) }
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                    onStdin(null)
                    onKill(null)
                }
            }
        }
    }

    /** In-band resize message understood by the PTY helper. */
    fun resizeMessage(rows: Int, cols: Int): ByteArray =
        "\u001B]9999;${rows.coerceIn(2, 300)};${cols.coerceIn(10, 400)}\u0007".toByteArray()

    /**
     * Engine self-test probes (no install gate). Distinguishes engine/linker
     * failure (P0), rootfs access failure (P1), and shell lookup failure (P2).
     */
    suspend fun engineProbes(): String {
        val sb = StringBuilder()
        val p0 = execProot(listOf("--version"), timeoutSec = 10)
        sb.appendLine("P0 engine --version: exit=${p0.exitCode} out=${(p0.stdout.ifBlank { p0.stderr }).take(200)}")
        if (!File(alpineDir, ".installed").isFile) {
            sb.appendLine("P1-P4 skipped: no rootfs marker")
            return sb.toString()
        }
        val base = baseArgs()
        val p1 = execProot(base + listOf("/bin/busybox", "echo", "hi"), timeoutSec = 10)
        sb.appendLine("P1 busybox echo: exit=${p1.exitCode} out=${(p1.stdout.ifBlank { p1.stderr }).take(200)}")
        val p2 = execProot(base + listOf("/bin/sh", "-c", "echo hi"), timeoutSec = 10)
        sb.appendLine("P2 sh echo: exit=${p2.exitCode} out=${(p2.stdout.ifBlank { p2.stderr }).take(200)}")
        if (p2.exitCode != 0) {
            val pv = execProot(
                base + listOf("/bin/sh", "-c", "echo hi"),
                extraEnv = mapOf("PROOT_VERBOSE" to "1"),
                timeoutSec = 10,
            )
            sb.appendLine("P2 verbose: ${(pv.stdout.ifBlank { pv.stderr }).take(1500)}")
        }
        // P3: what does the GUEST see for the busybox links?
        val p3 = execProot(base + listOf("/bin/busybox", "ls", "-l", "/bin/sh", "/usr/bin/zipinfo"), timeoutSec = 10)
        sb.appendLine("P3 guest ls: exit=${p3.exitCode} out=${(p3.stdout.ifBlank { p3.stderr }).take(300)}")
        // P4: can the guest write where apk needs to (exact failing area)?
        val p4 = execProot(
            base + listOf("/bin/sh", "-c", "touch /usr/bin/_wtest && ls -l /usr/bin/_wtest && rm /usr/bin/_wtest && echo TOUCH_OK"),
            timeoutSec = 10,
        )
        sb.appendLine("P4 touch test: exit=${p4.exitCode} out=${(p4.stdout.ifBlank { p4.stderr }).take(300)}")
        // P5: dotfile create (apk temp-file style).
        val p5 = execProot(
            base + listOf("/bin/sh", "-c", "touch /usr/bin/.apk.probe && ls -l /usr/bin/.apk.probe && rm /usr/bin/.apk.probe && echo DOT_OK"),
            timeoutSec = 10,
        )
        sb.appendLine("P5 dotfile test: exit=${p5.exitCode} out=${(p5.stdout.ifBlank { p5.stderr }).take(300)}")
        // P6: the exact apk dance — create temp, then rename onto final name.
        val p6 = execProot(
            base + listOf("/bin/sh", "-c", "touch /usr/bin/.apk.mv && mv /usr/bin/.apk.mv /usr/bin/_apkfinal && ls -l /usr/bin/_apkfinal && rm /usr/bin/_apkfinal && echo MV_OK"),
            timeoutSec = 10,
        )
        sb.appendLine("P6 rename test: exit=${p6.exitCode} out=${(p6.stdout.ifBlank { p6.stderr }).take(300)}")
        return sb.toString()
    }

    /** Stops every background server started with `&` (used before a reset). */
    fun stopAllDaemons() {
        daemons.values.forEach { runCatching { it.destroyForcibly() } }
        daemons.clear()
    }

    /** Forcibly stops all currently running foreground commands, if any (terminal Ctrl+C). */
    fun cancelRun() {
        activeProcs.forEach { proc ->
            try { proc.destroyForcibly() } catch (_: Throwable) {}
        }
        activeProcs.clear()
    }

    /**
     * Starts [command] as a detached daemon (for `&` background servers):
     * returns once the process is up (or [STARTUP_LOG_WINDOW_MS] elapsed),
     * while the process tree keeps running. Proot lives as long as any tracee,
     * so fire-and-forget through [run] would hang until timeout and then kill
     * the server — this is the correct primitive.
     *
     * The first [MAX_STARTUP_LOG_CHARS] of stdout/stderr are captured and
     * returned so the agent can see the real banner (Vite prints its actual
     * port there). The listening port is then taken from that banner, or
     * probed, and surfaced as [previewUrl].
     */
    suspend fun startDaemon(command: String): RunResult {
        return runMutex.withLock {
            withContext(Dispatchers.IO) {
                // Reap daemons whose process already exited so a crashed server
                // never permanently consumes a slot.
                daemons.entries.removeAll { !it.value.isAlive }
                if (daemons.size >= MAX_DAEMONS) {
                    return@withContext RunResult(
                        "",
                        "Too many background servers (${daemons.size}). " +
                            "Stop one first: stop_daemon tool or /killdaemon <id>.",
                        -1,
                    )
                }
                try {
                    workspaceDir.mkdirs()
                    val pb = ProcessBuilder(
                        listOf(prootBin.absolutePath) + baseArgs() + listOf("/bin/sh", "-c", command),
                    )
                    pb.redirectErrorStream(false)
                    withContainerEnv(pb)
                    val proc = pb.start()
                    val id = ++daemonSeq
                    daemons[id] = proc

                    val startupLog = StringBuilder()
                    val logLock = Any()
                    val appendLog: (String) -> Unit = { chunk ->
                        synchronized(logLock) {
                            if (startupLog.length < MAX_STARTUP_LOG_CHARS) {
                                startupLog.append(chunk)
                            }
                        }
                    }
                    startLogDrains(proc, appendLog)

                    // Reap on exit.
                    scope.launch {
                        try { proc.waitFor() } catch (_: Throwable) {}
                        daemons.remove(id)
                        if (daemons.keys.none { it == id }) {
                            // Last owner of the discovered port is gone.
                            _previewUrl.value = null
                        }
                    }

                    // Wait briefly for a banner that names the port, or for the
                    // process to die (fast crash → we return its error output).
                    val deadline = System.currentTimeMillis() + STARTUP_LOG_WINDOW_MS
                    while (System.currentTimeMillis() < deadline) {
                        val seen = synchronized(logLock) { startupLog.toString() }
                        if (parseAnnouncedPort(seen) != null) break
                        if (!proc.isAlive) break
                        delay(150)
                    }

                    val log = synchronized(logLock) { startupLog.toString() }.trim()
                    val announced = parseAnnouncedPort(log)
                    val port = announced ?: probeListeningPort()
                    if (port != null) {
                        _previewUrl.value = "http://127.0.0.1:$port"
                    }
                    val portNote = when {
                        !proc.isAlive ->
                            "\nThe command EXITED immediately (exit=${runCatching { proc.exitValue() }.getOrDefault(-1)}). " +
                                "It is NOT serving. Read the output above and fix the error, then start it again."
                        port != null ->
                            "\nListening on http://127.0.0.1:$port — open that URL to verify before telling the user it works."
                        else ->
                            "\nNo listening port detected yet. It may still be starting; re-check with " +
                                "`wget -qO- http://127.0.0.1:<port>` or list_daemons."
                    }
                    val out = buildString {
                        append("Started in background as daemon #$id.")
                        append("\nIt keeps running after this call returns, independent of this tool call.")
                        if (log.isNotBlank()) append("\n\nStartup output:\n").append(log)
                        append(portNote)
                        append("\nStop it with the stop_daemon tool {\"id\": \"$id\"}.")
                    }
                    RunResult(out, "", 0)
                } catch (t: Throwable) {
                    RunResult("", "daemon start failed: ${t.message ?: t.javaClass.simpleName}", -1)
                }
            }
        }
    }

    /**
     * Drains stdout/stderr so a chatty daemon never blocks on a full pipe,
     * feeding each chunk to [onChunk] for the startup banner capture.
     */
    private fun startLogDrains(proc: Process, onChunk: (String) -> Unit) {
        scope.launch {
            runCatching {
                val reader = proc.inputStream.bufferedReader()
                val buf = CharArray(2048)
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    onChunk(String(buf, 0, n))
                }
            }
        }
        scope.launch {
            runCatching {
                val reader = proc.errorStream.bufferedReader()
                val buf = CharArray(2048)
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    onChunk(String(buf, 0, n))
                }
            }
        }
    }

    /**
     * Best-effort loopback connect scan. Runs in the app process, which shares
     * the container's network namespace, so a hit means a browser can reach it.
     */
    suspend fun probeListeningPort(ports: List<Int> = COMMON_DEV_PORTS): Int? {
        return withContext(Dispatchers.IO) {
            ports.firstOrNull { p ->
                runCatching {
                    java.net.Socket().use { s ->
                        s.connect(java.net.InetSocketAddress("127.0.0.1", p), PROBE_TIMEOUT_MS)
                    }
                    true
                }.getOrDefault(false)
            }
        }
    }

    /** Publishes (or clears) the preview URL after an on-demand probe. */
    fun markPreviewPort(port: Int?) {
        _previewUrl.value = port?.let { "http://127.0.0.1:$it" }
    }

    /** Stops one daemon by id. Returns false when unknown. */
    suspend fun stopDaemon(id: Int): Boolean {
        return runMutex.withLock {
            withContext(Dispatchers.IO) {
                val proc = daemons.remove(id) ?: return@withContext false
                try { proc.destroy() } catch (_: Throwable) {}
                try { proc.waitFor(2, TimeUnit.SECONDS) } catch (_: Throwable) {}
                try { proc.destroyForcibly() } catch (_: Throwable) {}
                if (daemons.isEmpty()) _previewUrl.value = null
                true
            }
        }
    }

    /** Human-readable daemon list for tools/chat. */
    suspend fun listDaemons(): String {
        return runMutex.withLock {
            withContext(Dispatchers.IO) {
                if (daemons.isEmpty()) "No background daemons running."
                else daemons.keys.sorted().joinToString("\n") { "#$it: running" }
            }
        }
    }

    private suspend fun killAllDaemons() {
        val ids: List<Int> = runMutex.withLock {
            withContext(Dispatchers.IO) { daemons.keys.toList() }
        }
        ids.forEach { stopDaemon(it) }
        _previewUrl.value = null
    }

    /**
     * Installs a minimal `curl` subset backed by the busybox `wget` applet
     * (Alpine minirootfs ships no curl/ss/nc, and models constantly reach
     * for `curl -I`). Supports: [-sS] [-L] [-I] [-o FILE] [-O] URL, GET only.
     */
    private fun installCurlShim() {
        val dest = File(alpineDir, "usr/local/bin/curl")
        dest.parentFile?.mkdirs()
        dest.writeText(CURL_SHIM)
        dest.setExecutable(true, false)
        if (!dest.canExecute()) throw IllegalStateException("curl shim not executable")
    }

    // ── Serve localhost ───────────────────────────────────────────────

    /**
     * Serves [workspaceDir] on 127.0.0.1:[port] via busybox httpd inside the
     * container. Returns the URL, or an error string prefixed with "ERROR:".
     */
    suspend fun serve(port: Int = DEFAULT_PREVIEW_PORT): String {
        if (!isValidServePort(port)) return "ERROR:Invalid port $port (use 1025–65535 except 8080)."
        if (!isInstalled()) return "ERROR:Linux Environment is not installed."
        stopServe()
        return runMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    workspaceDir.mkdirs()
                    val pb = ProcessBuilder(
                        prootBin.absolutePath,
                        "-r", alpineDir.absolutePath,
                        "-b", "${workspaceDir.absolutePath}:/workspace",
                        "-b", "/dev",
                        "-b", "/proc",
                        "-0",
                        "-w", "/workspace",
                        "httpd", "-f", "-p", "127.0.0.1:$port", "-h", "/workspace",
                    )
                    withContainerEnv(pb)
                    val proc = pb.start()
                    // Give httpd a moment; if it exits immediately the port is taken.
                    Thread.sleep(600)
                    val alive = try { proc.exitValue(); false } catch (_: IllegalThreadStateException) { true }
                    if (!alive) {
                        val err = readCapped(proc.errorStream)
                        return@withContext "ERROR:httpd failed to bind port $port. $err"
                    }
                    serveProcess = proc
                    _servedPort.value = port
                    _previewUrl.value = "http://127.0.0.1:$port"
                    serveJob = scope.launch {
                        try { proc.waitFor() } catch (_: Throwable) {}
                        _servedPort.value = null
                        _previewUrl.value = null
                    }
                    "http://127.0.0.1:$port"
                } catch (t: Throwable) {
                    "ERROR:serve failed: ${t.message ?: t.javaClass.simpleName}"
                }
            }
        }
    }

    suspend fun stopServe() {
        killAllDaemons()
        serveJob?.cancel()
        serveJob = null
        withContext(Dispatchers.IO) {
            try { serveProcess?.destroy() } catch (_: Throwable) {}
            try { serveProcess?.waitFor(2, TimeUnit.SECONDS) } catch (_: Throwable) {}
            try { serveProcess?.destroyForcibly() } catch (_: Throwable) {}
            serveProcess = null
        }
        _servedPort.value = null
    }

    fun close() = scope.cancel()

    // ── Internals ─────────────────────────────────────────────────────

    /** Points proot at its bundled engine: lib dir first on the search path. */
    /**
     * Gives the rootfs its own /etc/machine-id. The Alpine minirootfs ships
     * without one, so tools that identify the device by it (opencode's free
     * tier, dbus) saw every install as the same machine and shared one quota.
     */
    private fun ensureMachineId() {
        runCatching {
            val f = File(alpineDir, "etc/machine-id")
            if (!f.parentFile!!.isDirectory) return
            if (f.isFile && f.readText().trim().length >= 32) return
            f.writeText(java.util.UUID.randomUUID().toString().replace("-", "") + "\n")
        }
    }

    /**
     * Starts a long-lived shell command inside Alpine with open stdin/stdout
     * (stdio MCP servers). The caller owns the process and must destroy it.
     */
    fun startShellProcess(command: String): Process {
        check(isInstalled()) { "Linux environment is not installed" }
        val pb = ProcessBuilder(listOf(prootBin.absolutePath) + baseArgs() + listOf("/bin/sh", "-c", command))
        pb.redirectErrorStream(false)
        withContainerEnv(pb)
        return pb.start().also { activeProcs.add(it) }
    }

    private fun withContainerEnv(pb: ProcessBuilder) {
        ensureMachineId()
        val libDir = nativeLibDir.absolutePath
        val env = pb.environment()
        val existing = env["LD_LIBRARY_PATH"].orEmpty()
        env["LD_LIBRARY_PATH"] = if (existing.isBlank()) libDir else "$libDir:$existing"
        env["PROOT_LOADER"] = File(nativeLibDir, "libproot_loader.so").absolutePath
        env["PROOT_LOADER_32"] = File(nativeLibDir, "libproot_loader32.so").absolutePath
        // proot's own documented workaround: its seccomp fast path returns
        // bogus errno (e.g. EACCES on apk's dirfd-relative openat/linkat) on
        // newer kernels ("new syscall order"). Pure ptrace is the reference
        // path and always correct; the per-syscall cost is negligible here.
        env["PROOT_NO_SECCOMP"] = "1"
        // Guest shells inherit an empty PATH through proot: set Alpine's
        // default so apk/ls/busybox applets resolve without full paths.
        // Guest TMPDIR points inside the guest (/tmp is created on install);
        // PROOT_TMP_DIR stays host-side for proot's own glue files.
        env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        env["HOME"] = "/root"
        env["TMPDIR"] = "/tmp"
        env["TEMP"] = "/tmp"
        env["TMP"] = "/tmp"
        // proot's compiled-in tmp points at the Termux prefix: redirect all
        // temp (glue rootfs, f2fs probe, shm) into app storage.
        val tmpDir = File(app.filesDir, "proot-tmp").apply { mkdirs() }.absolutePath
        env["PROOT_TMP_DIR"] = tmpDir
        env["TMPDIR"] = tmpDir
        env["TEMP"] = tmpDir
        env["TMP"] = tmpDir
        env["PROOT_L2S_DIR"] = tmpDir
    }

    /**
     * Verifies every bundled native lib against its pinned SHA-256.
     * Throws when a lib is missing or corrupted (reinstall the app).
     */
    private fun verifyBundledLibs(bundled: BundledProot) {
        for (lib in bundled.libs) {
            val f = File(nativeLibDir, lib.fileName)
            if (!f.isFile) throw IllegalStateException("Bundled ${lib.fileName} missing — reinstall the app.")
            if (!verifySha256(f, lib.sha256)) {
                throw IllegalStateException("Bundled ${lib.fileName} corrupted — reinstall the app.")
            }
        }
        if (!prootBin.canExecute() && !prootBin.setExecutable(true)) {
            throw IllegalStateException("proot is not executable — reinstall the app.")
        }
    }

    private suspend fun downloadToFile(
        url: String,
        dest: File,
        expectedSha256: String?,
        onProgress: (done: Long, total: Long) -> Unit,
    ) {
        if (expectedSha256 != null && !isValidSha256Hex(expectedSha256)) {
            throw IllegalArgumentException("Bad SHA-256 in manifest")
        }
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).header("User-Agent", "RaikaAI-container").build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} for $url")
                val body = resp.body ?: throw IllegalStateException("Empty body for $url")
                val total = body.contentLength()
                dest.parentFile?.mkdirs()
                val tmp = File(dest.parent, dest.name + ".part")
                FileOutputStream(tmp).use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            onProgress(done, total)
                        }
                    }
                }
                if (tmp.length() == 0L) {
                    tmp.delete()
                    throw IllegalStateException("Downloaded file is empty: $url")
                }
                if (expectedSha256 != null && !verifySha256(tmp, expectedSha256)) {
                    tmp.delete()
                    throw IllegalStateException("SHA-256 mismatch for $url")
                }
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
            }
        }
    }

    private fun verifySha256(file: File, hex: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        val got = digest.digest().joinToString("") { "%02x".format(it) }
        return got.equals(hex, ignoreCase = true)
    }

    /**
     * Sanity-checks the unpacked rootfs. Repairs a missing /bin/sh from
     * busybox; anything worse throws with a directory listing for diagnosis.
     *
     * NOTE: guest symlinks are usually absolute (/bin/busybox), so plain
     * File.exists() — which resolves on the HOST — lies about them. All
     * checks here resolve guest-side via lstat/readlink.
     */
    private fun verifyRootfs(entries: Int) {
        if (entries < 100) {
            throw IllegalStateException(
                "Rootfs unpack suspicious ($entries entries). Top: ${topLevelListing()}",
            )
        }
        if (!guestResolves("bin/busybox")) {
            throw IllegalStateException(
                "bin/busybox missing after unpack. Top: ${topLevelListing()}",
            )
        }
        if (!guestResolves("bin/sh")) {
            try {
                val sh = File(alpineDir, "bin/sh")
                if (sh.exists() || runCatching { android.system.Os.lstat(sh.absolutePath) }.isSuccess) {
                    sh.delete()
                }
                android.system.Os.symlink("busybox", sh.absolutePath)
            } catch (t: Throwable) {
                throw IllegalStateException("bin/sh missing and relink failed: ${t.message}")
            }
            if (!guestResolves("bin/sh")) {
                throw IllegalStateException("bin/sh still unresolvable after relink")
            }
        }
    }

    /**
     * Guest-side existence: lstat must succeed and symlink chains (absolute
     * targets re-rooted at [alpineDir]) must land on a real file.
     */
    private fun guestResolves(relative: String): Boolean {
        var cur = File(alpineDir, relative)
        try {
            android.system.Os.lstat(cur.absolutePath)
        } catch (_: Throwable) {
            return false
        }
        repeat(8) {
            val target = runCatching { android.system.Os.readlink(cur.absolutePath) }.getOrNull()
                ?: return cur.isFile
            cur = if (target.startsWith("/")) {
                File(alpineDir, target.removePrefix("/"))
            } else {
                File(cur.parentFile, target)
            }
            try {
                android.system.Os.lstat(cur.absolutePath)
            } catch (_: Throwable) {
                return false
            }
        }
        return cur.isFile
    }

    private fun topLevelListing(): String {
        return runCatching {
            (alpineDir.listFiles()?.take(12)?.map { it.name } ?: emptyList()).toString()
        }.getOrDefault("?")
    }

    /**
     * Rewrites the guest resolv.conf from Android's CURRENT DNS servers
     * (captive portals / carriers / VPNs often block third-party DNS, so a
     * hardcoded 8.8.8.8 goes dark when the network changes). Returns the
     * servers used, for chat feedback.
     */
    suspend fun refreshResolvConf(): String {
        return withContext(Dispatchers.IO) {
            val servers = runCatching {
                val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
                    ?: return@runCatching emptyList<String>()
                val props = cm.getLinkProperties(cm.activeNetwork) ?: return@runCatching emptyList<String>()
                val all = props.dnsServers.mapNotNull { it.hostAddress?.takeIf { h -> h.isNotBlank() } }
                // IPv4 first: some mobile IPv6 DNS answers never arrive.
                (all.filter { !it.contains(':') } + all.filter { it.contains(':') }).distinct()
            }.getOrDefault(emptyList()).take(3)
                .ifEmpty { listOf("8.8.8.8", "1.1.1.1") }
            val f = File(alpineDir, "etc/resolv.conf")
            f.parentFile?.mkdirs()
            f.writeText(servers.joinToString(separator = "") { "nameserver $it\n" })
            resolvLastRefresh = System.currentTimeMillis()
            KLog.d(TAG, "resolv.conf DNS: ${servers.joinToString()}")
            servers.joinToString()
        }
    }

    /** Minimal tar.gz extractor: regular files + dirs + symlinks. Returns entry count. */
    private fun unpackTarGz(tarGz: File, destDir: File): Int {
        var count = 0
        GZIPInputStream(BufferedInputStream(FileInputStream(tarGz), 64 * 1024)).buffered().use { gz ->
            val header = ByteArray(512)
            var longName: String? = null
            while (true) {
                var read = 0
                while (read < 512) {
                    val n = gz.read(header, read, 512 - read)
                    if (n < 0) break
                    read += n
                }
                if (read == 0) break // EOF
                if (read < 512) throw IllegalStateException("Truncated tar header")
                if (header.all { it == 0.toByte() }) {
                    // Two zero blocks end the archive; one is enough to stop here.
                    break
                }
                val nameBytes = header.copyOfRange(0, 100).takeWhile { it != 0.toByte() }.toByteArray()
                var name = longName ?: String(nameBytes, Charsets.UTF_8)
                longName = null
                val typeFlag = header[156].toInt().toChar()
                val sizeOctal = String(header.copyOfRange(124, 136)).trim().trimEnd('\u0000').trim()
                val size = if (sizeOctal.isEmpty()) 0L else sizeOctal.toLong(8)
                val modeOctal = String(header.copyOfRange(100, 108)).trim().trimEnd('\u0000').trim()
                val mode = runCatching { if (modeOctal.isEmpty()) 0 else modeOctal.toLong(8).toInt() }.getOrDefault(0)

                if (typeFlag == 'g' || typeFlag == 'x') {
                    // pax extended header: skip payload
                    skipFully(gz, size)
                    skipPadding(gz, size)
                    continue
                }
                if (typeFlag == 'L') {
                    // GNU long name: payload is the real name
                    val payload = ByteArray(size.toInt())
                    readFully(gz, payload)
                    longName = String(payload, Charsets.UTF_8).trimEnd('\u0000').trim()
                    skipPadding(gz, size)
                    continue
                }
                val target = File(destDir, name)
                // Zip-slip guard
                val destCanonical = destDir.canonicalPath
                val targetCanonical = target.canonicalPath
                if (targetCanonical != destCanonical && !targetCanonical.startsWith(destCanonical + File.separator)) {
                    throw IllegalStateException("Tar entry escapes destination: $name")
                }
                // Guest-relative name (tar uses ./ prefix): needed to rewrite
                // absolute symlinks into relative ones below.
                val guestName = name.removePrefix("./").trimStart('/')
                when (typeFlag) {
                    '5' -> {
                        target.mkdirs()
                        count++
                    }
                    '2' -> {
                        // Symlink (Alpine links every applet to busybox).
                        // Link target lives at header offset 157, length 100.
                        val linkBytes = header.copyOfRange(157, 257).takeWhile { it != 0.toByte() }.toByteArray()
                        val linkTarget = String(linkBytes, Charsets.UTF_8)
                        if (linkTarget.isBlank()) throw IllegalStateException("Empty symlink target: $name")
                        // Host-side stat() must resolve our links too (proot
                        // consistency checks run on the host): rewrite absolute
                        // guest targets into relative ones. Inside the guest
                        // both forms resolve identically.
                        val guestTarget = relativizeGuestLink(guestName, linkTarget)
                        target.parentFile?.mkdirs()
                        // lstat, not exists(): broken/absolute links must be
                        // replaced too, and File.delete() removes the link
                        // itself (never follows it).
                        if (isSymlink(target) || target.exists()) {
                            if (!target.delete()) throw IllegalStateException("Cannot replace $name")
                        }
                        try {
                            android.system.Os.symlink(guestTarget, target.absolutePath)
                            count++
                        } catch (t: Throwable) {
                            throw IllegalStateException("Symlink failed ($name -> $guestTarget): ${t.message}")
                        }
                    }
                    else -> {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out ->
                            var remaining = size
                            val buf = ByteArray(64 * 1024)
                            while (remaining > 0) {
                                val want = minOf(buf.size.toLong(), remaining).toInt()
                                val n = gz.read(buf, 0, want)
                                if (n < 0) throw IllegalStateException("Truncated tar payload: $name")
                                out.write(buf, 0, n)
                                remaining -= n
                            }
                        }
                        if (mode and 0b001001001 != 0) target.setExecutable(true, false)
                        count++
                    }
                }
                skipPadding(gz, size)
            }
        }
        return count
    }

    private fun skipPadding(gz: java.io.InputStream, size: Long) {
        val pad = ((512 - (size % 512)) % 512)
        if (pad > 0) skipFully(gz, pad)
    }

    private fun skipFully(gz: java.io.InputStream, n: Long) {
        var remaining = n
        val buf = ByteArray(8 * 1024)
        while (remaining > 0) {
            val want = minOf(buf.size.toLong(), remaining).toInt()
            val r = gz.read(buf, 0, want)
            if (r < 0) throw IllegalStateException("Truncated tar stream")
            remaining -= r
        }
    }

    private fun readFully(gz: java.io.InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = gz.read(buf, off, buf.size - off)
            if (n < 0) throw IllegalStateException("Truncated tar stream")
            off += n
        }
    }

    private fun makeBusyboxExecutable() {
        listOf("bin/busybox", "bin/sh", "bin/bbconfig").forEach { rel ->
            val f = File(alpineDir, rel)
            if (f.isFile) f.setExecutable(true, false)
        }
    }

    /**
     * Reads [stream] to the END and keeps at most [OUTPUT_CAP] bytes: the
     * head by default, the tail with [keepTail]. Draining matters: the old
     * head mode stopped reading at the cap, so a chatty command (npm install,
     * apk add, a build) filled the pipe, blocked, and was killed at timeout.
     */
    private fun readCapped(stream: java.io.InputStream, keepTail: Boolean = false): String {
        return try {
            val buf = ByteArray(8192)
            if (!keepTail) {
                val head = java.io.ByteArrayOutputStream()
                var total = 0L
                while (true) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    val room = OUTPUT_CAP - head.size()
                    if (room > 0) head.write(buf, 0, minOf(n, room))
                    total += n
                }
                var text = head.toString(Charsets.UTF_8.name())
                if (total > OUTPUT_CAP) text += "\n…(${(total - OUTPUT_CAP) / 1024} KB more output not shown)"
                text
            } else {
                // Verbose traces: the failure is at the END. Keep the tail.
                val chunks = ArrayDeque<ByteArray>()
                var kept = 0
                var total = 0L
                while (true) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    chunks.addLast(buf.copyOf(n))
                    kept += n
                    total += n
                    while (kept > OUTPUT_CAP && chunks.size > 1) {
                        kept -= chunks.removeFirst().size
                    }
                }
                val out = ByteArray(kept)
                var off = 0
                for (c in chunks) {
                    c.copyInto(out, off)
                    off += c.size
                }
                val text = String(out, Charsets.UTF_8)
                if (total > kept) "…(earlier output truncated, showing last ${kept / 1024} KB)\n$text" else text
            }
        } catch (_: Throwable) {
            ""
        }
    }
}

/**
 * Rewrites an absolute guest symlink target into a relative one so the
 * link resolves both inside the guest AND on the host (proot stat checks
 * run host-side, where guest-absolute targets don't exist).
 * Targets outside the rootfs (/proc, /dev, sys) are kept absolute.
 */
internal fun relativizeGuestLink(guestName: String, linkTarget: String): String {
    if (!linkTarget.startsWith("/")) return linkTarget
    val destSegs = linkTarget.removePrefix("/").split("/").filter { it.isNotEmpty() }
    if (destSegs.isEmpty()) return linkTarget
    // Only rewrite targets that stay inside the rootfs (first segment is
    // a real top-level dir, not proc/dev/sys).
    if (destSegs[0] in setOf("proc", "dev", "sys")) return linkTarget
    val parentSegs = guestName.split("/").filter { it.isNotEmpty() }.dropLast(1)
    var common = 0
    while (common < parentSegs.size && common < destSegs.size &&
        parentSegs[common] == destSegs[common]
    ) {
        common++
    }
    val up = List(parentSegs.size - common) { ".." }
    return (up + destSegs.drop(common)).joinToString("/").ifEmpty { "." }
}
