package com.raikaai.app.core.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.raikaai.app.util.KLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Lightweight helper to manage audio playback of voice prompts and audio notes.
 */
class AudioPlayerHelper(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    private val _currentlyPlayingPath = MutableStateFlow<String?>(null)
    val currentlyPlayingPath: StateFlow<String?> = _currentlyPlayingPath

    fun play(filePath: String, onCompletion: (() -> Unit)? = null) {
        if (_currentlyPlayingPath.value == filePath) {
            stop()
            return
        }

        stop()

        val file = File(filePath)
        if (!file.exists()) {
            KLog.w("AudioPlayer", "File does not exist: $filePath")
            return
        }

        try {
            val player = MediaPlayer()
            player.setDataSource(context, Uri.fromFile(file))
            // prepareAsync: prepare() blocks on disk I/O and must never run
            // on the main thread (this is called from click handlers).
            player.setOnPreparedListener { mp ->
                try {
                    mp.start()
                    _currentlyPlayingPath.value = filePath
                } catch (e: Exception) {
                    KLog.e("AudioPlayer", "Error starting playback for $filePath", e)
                    releasePlayer(mp)
                    _currentlyPlayingPath.value = null
                }
            }
            player.setOnCompletionListener { mp ->
                _currentlyPlayingPath.value = null
                releasePlayer(mp)
                onCompletion?.invoke()
            }
            player.setOnErrorListener { mp, what, extra ->
                KLog.e("AudioPlayer", "Playback error for $filePath (what=$what extra=$extra)")
                releasePlayer(mp)
                _currentlyPlayingPath.value = null
                true
            }
            mediaPlayer = player
            player.prepareAsync()
        } catch (e: Exception) {
            KLog.e("AudioPlayer", "Error playing audio file $filePath", e)
            _currentlyPlayingPath.value = null
        }
    }

    private fun releasePlayer(mp: MediaPlayer) {
        try {
            if (mediaPlayer === mp) mediaPlayer = null
            mp.reset()
            mp.release()
        } catch (_: Exception) {
        }
    }

    fun stop() {
        val mp = mediaPlayer
        mediaPlayer = null
        _currentlyPlayingPath.value = null
        if (mp != null) {
            try {
                if (mp.isPlaying) mp.stop()
            } catch (_: Exception) {
            } finally {
                try {
                    mp.reset()
                } catch (_: Exception) {
                }
                try {
                    mp.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun isPlaying(filePath: String): Boolean {
        return _currentlyPlayingPath.value == filePath
    }
}
