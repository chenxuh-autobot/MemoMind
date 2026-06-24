package cn.chenxuhang.creativeai

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

internal const val SherpaAsrAssetDirectory =
    "asr/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17"
internal const val SherpaAsrModelPath = "$SherpaAsrAssetDirectory/model.int8.onnx"
internal const val SherpaAsrTokensPath = "$SherpaAsrAssetDirectory/tokens.txt"
internal const val SherpaAsrVadPath = "asr/silero_vad.onnx"
internal const val SherpaAsrSampleRate = 16_000
internal const val SherpaAsrFeatureDim = 80

data class SpeechTranscriptUpdate(
    val text: String,
    val isFinal: Boolean,
)

data class SpeechTranscriberState(
    val isRunning: Boolean,
    val modeLabel: String,
)

object SpeechRecognitionAvailability {
    fun isAnyRecognitionAvailable(
        context: Context,
    ): Boolean {
        val modelFiles = context.assets.list(SherpaAsrAssetDirectory)?.toSet().orEmpty()
        val rootFiles = context.assets.list("asr")?.toSet().orEmpty()
        return modelFiles.contains("model.int8.onnx") &&
            modelFiles.contains("tokens.txt") &&
            rootFiles.contains("silero_vad.onnx")
    }
}

internal fun createBundledOfflineRecognizer(
    context: Context,
): OfflineRecognizer {
    return OfflineRecognizer(
        assetManager = context.assets,
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = SherpaAsrSampleRate,
                featureDim = SherpaAsrFeatureDim,
                dither = 0.0f,
            ),
            modelConfig = OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = SherpaAsrModelPath,
                    language = "auto",
                    useInverseTextNormalization = true,
                ),
                tokens = SherpaAsrTokensPath,
                numThreads = 2,
                provider = "cpu",
                debug = false,
            ),
        ),
    )
}

internal fun createBundledVad(
    context: Context,
): Vad {
    return Vad(
        assetManager = context.assets,
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = SherpaAsrVadPath,
                threshold = 0.5f,
                minSilenceDuration = 0.35f,
                minSpeechDuration = 0.2f,
                windowSize = 512,
                maxSpeechDuration = 8.0f,
            ),
            sampleRate = SherpaAsrSampleRate,
            numThreads = 1,
            provider = "cpu",
            debug = false,
        ),
    )
}

class DeviceSpeechTranscriber(
    private val context: Context,
    private val recordingsDirectory: File,
    private val onTranscript: (SpeechTranscriptUpdate) -> Unit,
    private val onStateChanged: (SpeechTranscriberState) -> Unit,
    private val onStatusMessage: (String) -> Unit,
    private val onAudioCaptured: (SelectedLocalAsset?) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var wavWriter: WavFileWriter? = null

    @Volatile
    private var isRunning = false

    @Volatile
    private var pendingStop = false

    private val committedSegments = mutableListOf<String>()

    fun currentModeLabel(): String = "端侧离线 ASR（SenseVoice）"

    fun startListening() {
        check(SpeechRecognitionAvailability.isAnyRecognitionAvailable(context)) {
            "当前 APK 内未打包 SenseVoice + VAD 语音模型。"
        }
        if (isRunning) {
            postStatus("语音转写已经在进行中。")
            return
        }

        ensureRecognizer()
        ensureVad().reset()

        val record = createAudioRecord()
        val outputFile = createRecordingFile(recordingsDirectory)
        wavWriter = WavFileWriter(outputFile, sampleRate = SherpaAsrSampleRate, channelCount = 1)
        committedSegments.clear()
        pendingStop = false
        audioRecord = record
        isRunning = true
        notifyState()
        postTranscript("", isFinal = false)
        postStatus("端侧语音识别已就绪，请开始说话。")
        record.startRecording()
        recordingThread = thread(
            start = true,
            isDaemon = true,
            name = "creative-ai-sensevoice-asr",
        ) {
            runRecognitionLoop(record, outputFile)
        }
    }

    fun stopListening() {
        if (!isRunning) return
        pendingStop = true
        isRunning = false
        notifyState()
        postStatus("正在停止录音并整理端侧转写结果...")
        runCatching { audioRecord?.stop() }
    }

    fun destroy() {
        isRunning = false
        pendingStop = true
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { wavWriter?.abort() }
        wavWriter = null
        releaseInferenceResources()
    }

    private fun runRecognitionLoop(
        record: AudioRecord,
        outputFile: File,
    ) {
        val shortBuffer = ShortArray(512)
        try {
            while (isRunning && !pendingStop) {
                val read = record.read(shortBuffer, 0, shortBuffer.size)
                if (read <= 0) continue
                wavWriter?.write(shortBuffer, read)
                val samples = FloatArray(read) { index -> shortBuffer[index] / 32768.0f }
                ensureVad().acceptWaveform(samples)
                drainVadSegments()
            }

            ensureVad().flush()
            drainVadSegments()
            wavWriter?.close()
            val capturedAsset = SelectedLocalAsset(
                uri = android.net.Uri.fromFile(outputFile).toString(),
                displayName = outputFile.name,
                mimeTypeLabel = "audio/wav",
            )
            postAudioCaptured(capturedAsset)
            postTranscript(currentTranscript(), isFinal = true)
            postStatus(
                if (currentTranscript().isBlank()) {
                    "端侧转写完成，但没有识别到清晰文字。"
                } else {
                    "端侧转写完成，录音已留档。"
                },
            )
        } catch (error: Throwable) {
            runCatching { wavWriter?.abort() }
            outputFile.delete()
            postStatus(error.message ?: "端侧语音转写失败。")
        } finally {
            runCatching { record.release() }
            audioRecord = null
            wavWriter = null
            releaseInferenceResources()
            isRunning = false
            pendingStop = false
            notifyState()
        }
    }

    private fun drainVadSegments() {
        val localVad = vad ?: return
        while (!localVad.empty()) {
            val segment = localVad.front()
            localVad.pop()
            val text = decodeSegment(segment.samples)
            if (text.isBlank()) continue
            if (committedSegments.lastOrNull() != text) {
                committedSegments += text
                postTranscript(currentTranscript(), isFinal = false)
            }
        }
    }

    private fun decodeSegment(
        samples: FloatArray,
    ): String {
        if (samples.isEmpty()) return ""
        val localRecognizer = recognizer ?: return ""
        val stream = localRecognizer.createStream()
        return try {
            stream.acceptWaveform(samples, SherpaAsrSampleRate)
            localRecognizer.decode(stream)
            localRecognizer.getResult(stream).text.normalizeTranscriptFragment()
        } finally {
            stream.release()
        }
    }

    private fun currentTranscript(): String {
        return committedSegments.joinToString(separator = "\n")
    }

    private fun ensureRecognizer(): OfflineRecognizer {
        recognizer?.let { return it }
        return createBundledOfflineRecognizer(context).also { recognizer = it }
    }

    private fun ensureVad(): Vad {
        vad?.let { return it }
        return createBundledVad(context).also { vad = it }
    }

    private fun releaseInferenceResources() {
        recognizer?.release()
        recognizer = null
        vad?.release()
        vad = null
    }

    private fun createAudioRecord(): AudioRecord {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SherpaAsrSampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBufferSize > 0) { "无法初始化端侧录音缓冲区。" }
        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SherpaAsrSampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2,
        )
    }

    private fun notifyState() {
        val state = SpeechTranscriberState(
            isRunning = isRunning,
            modeLabel = currentModeLabel(),
        )
        mainHandler.post { onStateChanged(state) }
    }

    private fun postTranscript(
        text: String,
        isFinal: Boolean,
    ) {
        mainHandler.post {
            onTranscript(
                SpeechTranscriptUpdate(
                    text = text,
                    isFinal = isFinal,
                ),
            )
        }
    }

    private fun postStatus(
        message: String,
    ) {
        mainHandler.post { onStatusMessage(message) }
    }

    private fun postAudioCaptured(
        asset: SelectedLocalAsset?,
    ) {
        mainHandler.post { onAudioCaptured(asset) }
    }
}

private fun createRecordingFile(
    recordingsDirectory: File,
): File {
    recordingsDirectory.mkdirs()
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return File(recordingsDirectory, "mic_note_$timestamp.wav")
}

private class WavFileWriter(
    file: File,
    private val sampleRate: Int,
    private val channelCount: Int,
) {
    private val raf = RandomAccessFile(file, "rw")
    private var dataSize: Long = 0
    private var closed = false

    init {
        writeHeaderPlaceholder()
    }

    fun write(
        samples: ShortArray,
        length: Int,
    ) {
        check(!closed) { "WAV writer already closed." }
        for (index in 0 until length) {
            raf.writeShort(samples[index].toInt().reverseBytesForLittleEndian())
        }
        dataSize += length * 2L
    }

    fun close() {
        if (closed) return
        closed = true
        raf.seek(0)
        writeWavHeader(totalAudioLen = dataSize)
        raf.close()
    }

    fun abort() {
        if (closed) return
        closed = true
        runCatching { raf.close() }
    }

    private fun writeHeaderPlaceholder() {
        repeat(44) { raf.write(0) }
    }

    private fun writeWavHeader(
        totalAudioLen: Long,
    ) {
        val byteRate = sampleRate * channelCount * 2
        val totalDataLen = totalAudioLen + 36

        raf.writeBytes("RIFF")
        raf.writeInt(Integer.reverseBytes(totalDataLen.toInt()))
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        raf.writeInt(Integer.reverseBytes(16))
        raf.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
        raf.writeShort(java.lang.Short.reverseBytes(channelCount.toShort()).toInt())
        raf.writeInt(Integer.reverseBytes(sampleRate))
        raf.writeInt(Integer.reverseBytes(byteRate))
        raf.writeShort(java.lang.Short.reverseBytes((channelCount * 2).toShort()).toInt())
        raf.writeShort(java.lang.Short.reverseBytes(16.toShort()).toInt())
        raf.writeBytes("data")
        raf.writeInt(Integer.reverseBytes(totalAudioLen.toInt()))
    }

    private fun Int.reverseBytesForLittleEndian(): Int = Integer.reverseBytes(this)
}

internal fun String?.normalizeTranscriptFragment(): String {
    return this
        .orEmpty()
        .replace("<|.*?|>".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
}
