package com.raikaai.app.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.raikaai.app.util.KLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Helper to record high-fidelity 16 kHz 16-bit Mono PCM audio into standard WAV format.
 * Matches Google AI Edge LiteRT-LM audio input requirements (Gemma 4 / Gemma 3n).
 */
class AudioRecorderHelper(
    private val context: Context,
) {
    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val TAG = "AudioRecorderHelper"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var recordJob: Job? = null
    private var timerJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var currentOutputFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _amplitude = MutableStateFlow(0)
    val amplitude: StateFlow<Int> = _amplitude

    @SuppressLint("MissingPermission")
    fun startRecording(outputFile: File? = null): Boolean {
        if (_isRecording.value) return false

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            KLog.e(TAG, "Invalid buffer size: $minBufferSize")
            return false
        }
        val bufferSize = maxOf(minBufferSize * 2, 4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize,
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                KLog.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            val targetFile = outputFile ?: File(
                context.cacheDir,
                "voice_prompt_${System.currentTimeMillis()}.wav",
            )
            currentOutputFile = targetFile

            audioRecord?.startRecording()
            _isRecording.value = true
            _durationMs.value = 0L
            _amplitude.value = 0

            val startTime = System.currentTimeMillis()
            timerJob = scope.launch {
                while (isActive && _isRecording.value) {
                    _durationMs.value = System.currentTimeMillis() - startTime
                    delay(100)
                }
            }

            recordJob = scope.launch {
                val buffer = ShortArray(bufferSize / 2)
                val pcmOutputStream = ByteArrayOutputStream()

                try {
                    while (isActive && _isRecording.value) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                        if (read > 0) {
                            var maxAmp = 0
                            val byteBuffer = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until read) {
                                val sample = buffer[i]
                                byteBuffer.putShort(sample)
                                val a = abs(sample.toInt())
                                if (a > maxAmp) maxAmp = a
                            }
                            _amplitude.value = maxAmp
                            pcmOutputStream.write(byteBuffer.array())
                        }
                    }

                    // Flush and write standard WAV file with 44-byte header
                    val pcmBytes = pcmOutputStream.toByteArray()
                    writeWavFile(targetFile, pcmBytes, SAMPLE_RATE, 1, 16)
                    KLog.d(TAG, "Recorded audio saved to: ${targetFile.absolutePath} (${pcmBytes.size} bytes PCM)")
                } catch (e: Exception) {
                    KLog.e(TAG, "Error recording audio", e)
                }
            }

            return true
        } catch (e: Exception) {
            KLog.e(TAG, "Failed to start audio recording", e)
            audioRecord?.release()
            audioRecord = null
            _isRecording.value = false
            return false
        }
    }

    suspend fun stopRecording(): File? {
        if (!_isRecording.value) return null
        _isRecording.value = false

        val rec = audioRecord
        audioRecord = null
        try {
            timerJob?.cancel()
            rec?.stop()
        } catch (e: Exception) {
            KLog.e(TAG, "Error stopping AudioRecord", e)
        }

        // Wait for recordJob to exit its read loop and finish writing the WAV
        // BEFORE releasing the native AudioRecord instance.
        try {
            recordJob?.join()
        } catch (_: Exception) {
        }
        try {
            rec?.release()
        } catch (_: Exception) {
        }

        val result = currentOutputFile
        currentOutputFile = null
        return result
    }

    fun stopRecordingAsync(onComplete: (File?) -> Unit) {
        if (!_isRecording.value) {
            onComplete(null)
            return
        }
        _isRecording.value = false

        val rec = audioRecord
        audioRecord = null
        try {
            timerJob?.cancel()
            rec?.stop()
        } catch (e: Exception) {
            KLog.e(TAG, "Error stopping AudioRecord", e)
        }

        val result = currentOutputFile
        currentOutputFile = null
        scope.launch {
            try {
                recordJob?.join()
            } catch (_: Exception) {
            }
            try {
                rec?.release()
            } catch (_: Exception) {
            }
            onComplete(result)
        }
    }

    fun cancelRecording() {
        _isRecording.value = false
        timerJob?.cancel()
        val job = recordJob
        job?.cancel()
        val rec = audioRecord
        audioRecord = null
        try {
            rec?.stop()
        } catch (_: Exception) {
        }
        scope.launch {
            try {
                job?.join()
            } catch (_: Exception) {
            }
            try {
                rec?.release()
            } catch (_: Exception) {
            }
        }
        currentOutputFile?.delete()
        currentOutputFile = null
        _durationMs.value = 0L
        _amplitude.value = 0
    }

    /**
     * Releases everything, including the internal coroutine scope. Call when
     * the owning composable leaves the composition for good (otherwise the
     * scope — and its captured Context — leaks across rotations).
     */
    fun destroy() {
        cancelRecording()
        scope.cancel()
    }

    /**
     * Writes 16-bit PCM samples with standard 44-byte canonical WAV header.
     */
    private fun writeWavFile(
        file: File,
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
    ) {
        val totalAudioLen = pcmData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()

        FileOutputStream(file).use { out ->
            val header = ByteArray(44)
            // RIFF/WAVE header
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            // WAVE
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            // 'fmt ' chunk
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            header[16] = 16 // 4 bytes: size of 'fmt ' chunk
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1 // format = 1 (PCM)
            header[21] = 0
            header[22] = channels.toByte()
            header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = (channels * bitsPerSample / 8).toByte() // block align
            header[33] = 0
            header[34] = bitsPerSample.toByte() // bits per sample
            header[35] = 0
            // 'data' chunk
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

            out.write(header, 0, 44)
            out.write(pcmData)
        }
    }
}
