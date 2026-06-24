package cn.chenxuhang.creativeai

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.Vad
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AudioFileTranscriptionResult(
    val text: String,
    val modeLabel: String,
)

suspend fun Context.retranscribeAudioFile(
    audioUri: Uri,
    cacheDirectory: File,
): AudioFileTranscriptionResult = withContext(Dispatchers.IO) {
    check(SpeechRecognitionAvailability.isAnyRecognitionAvailable(this@retranscribeAudioFile)) {
        "当前 APK 内未打包 SenseVoice + VAD 语音模型。"
    }

    val preparedAudio = decodeAudioFileToPcm(
        context = this@retranscribeAudioFile,
        sourceUri = audioUri,
        cacheDirectory = cacheDirectory,
    )
    val recognizer = createBundledOfflineRecognizer(this@retranscribeAudioFile)
    val vad = createBundledVad(this@retranscribeAudioFile)
    try {
        val text = transcribePreparedAudioInSegments(
            preparedAudio = preparedAudio,
            recognizer = recognizer,
            vad = vad,
        )
        AudioFileTranscriptionResult(
            text = text,
            modeLabel = "端侧文件分段转写（SenseVoice + VAD）",
        )
    } finally {
        recognizer.release()
        vad.release()
        preparedAudio.pcmFile.delete()
    }
}

private data class PreparedPcmAudio(
    val pcmFile: File,
    val sampleRate: Int,
    val channelCount: Int,
)

private fun transcribePreparedAudioInSegments(
    preparedAudio: PreparedPcmAudio,
    recognizer: OfflineRecognizer,
    vad: Vad,
): String {
    vad.reset()
    val transcripts = mutableListOf<String>()
    FileInputStream(preparedAudio.pcmFile).buffered().use { input ->
        val bytesPerFrame = preparedAudio.channelCount * 2
        val frameBatchSize = 2_048
        val buffer = ByteArray(frameBatchSize * bytesPerFrame)
        while (true) {
            val byteCount = input.read(buffer)
            if (byteCount <= 0) break
            val monoChunk = pcm16BytesToMonoFloatArray(
                bytes = buffer,
                byteCount = byteCount,
                channelCount = preparedAudio.channelCount,
            )
            val vadChunk = monoChunk.resampleTo(
                sourceSampleRate = preparedAudio.sampleRate,
                targetSampleRate = SherpaAsrSampleRate,
            )
            if (vadChunk.isEmpty()) continue
            vad.acceptWaveform(vadChunk)
            drainVadSegments(vad, recognizer, transcripts)
        }
    }
    vad.flush()
    drainVadSegments(vad, recognizer, transcripts)
    return transcripts.joinToString(separator = "\n")
}

private fun drainVadSegments(
    vad: Vad,
    recognizer: OfflineRecognizer,
    transcripts: MutableList<String>,
) {
    while (!vad.empty()) {
        val segment = vad.front()
        vad.pop()
        val text = decodeSegment(recognizer, segment.samples)
        if (text.isBlank()) continue
        if (transcripts.lastOrNull() != text) {
            transcripts += text
        }
    }
}

private fun decodeSegment(
    recognizer: OfflineRecognizer,
    samples: FloatArray,
): String {
    if (samples.isEmpty()) return ""
    val stream = recognizer.createStream()
    return try {
        stream.acceptWaveform(samples, SherpaAsrSampleRate)
        recognizer.decode(stream)
        recognizer.getResult(stream).text.normalizeTranscriptFragment()
    } finally {
        stream.release()
    }
}

private fun pcm16BytesToMonoFloatArray(
    bytes: ByteArray,
    byteCount: Int,
    channelCount: Int,
): FloatArray {
    if (byteCount <= 1 || channelCount <= 0) return FloatArray(0)
    val validBytes = byteCount - (byteCount % 2)
    val sampleCount = validBytes / 2
    if (sampleCount == 0) return FloatArray(0)

    if (channelCount == 1) {
        val mono = FloatArray(sampleCount)
        var byteIndex = 0
        var sampleIndex = 0
        while (byteIndex + 1 < validBytes) {
            val low = bytes[byteIndex].toInt() and 0xff
            val high = bytes[byteIndex + 1].toInt()
            mono[sampleIndex++] = (((high shl 8) or low).toShort() / 32768.0f)
            byteIndex += 2
        }
        return mono
    }

    val frameCount = sampleCount / channelCount
    val mono = FloatArray(frameCount)
    var byteIndex = 0
    for (frameIndex in 0 until frameCount) {
        var sum = 0f
        repeat(channelCount) {
            val low = bytes[byteIndex].toInt() and 0xff
            val high = bytes[byteIndex + 1].toInt()
            sum += (((high shl 8) or low).toShort() / 32768.0f)
            byteIndex += 2
        }
        mono[frameIndex] = sum / channelCount
    }
    return mono
}

private fun FloatArray.resampleTo(
    sourceSampleRate: Int,
    targetSampleRate: Int,
): FloatArray {
    if (isEmpty()) return this
    if (sourceSampleRate <= 0 || sourceSampleRate == targetSampleRate) return copyOf()
    val targetSize = ((size.toLong() * targetSampleRate) / sourceSampleRate)
        .coerceAtLeast(1L)
        .toInt()
    val scale = sourceSampleRate.toFloat() / targetSampleRate.toFloat()
    return FloatArray(targetSize) { index ->
        val sourceIndex = index * scale
        val left = sourceIndex.toInt().coerceIn(indices)
        val right = (left + 1).coerceIn(indices)
        val fraction = sourceIndex - left
        this[left] + ((this[right] - this[left]) * fraction)
    }
}

private fun decodeAudioFileToPcm(
    context: Context,
    sourceUri: Uri,
    cacheDirectory: File,
): PreparedPcmAudio {
    cacheDirectory.mkdirs()
    val extractor = MediaExtractor()
    extractor.setDataSource(context, sourceUri, null)
    val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
        extractor.getTrackFormat(index)
            .getString(MediaFormat.KEY_MIME)
            ?.startsWith("audio/") == true
    } ?: error("没有找到可解码的音频轨道。")

    extractor.selectTrack(trackIndex)
    val inputFormat = extractor.getTrackFormat(trackIndex)
    val mimeType = inputFormat.getString(MediaFormat.KEY_MIME)
        ?: error("无法识别音频 MIME 类型。")
    val decoder = MediaCodec.createDecoderByType(mimeType)
    val outputFile = File(cacheDirectory, "asr_retranscribe_${System.currentTimeMillis()}.pcm")
    val outputStream = FileOutputStream(outputFile)

    var outputFormat = inputFormat
    try {
        decoder.configure(inputFormat, null, null, 0)
        decoder.start()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputBufferIndex = decoder.dequeueInputBuffer(10_000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex) ?: continue
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            sampleSize,
                            extractor.sampleTime,
                            0,
                        )
                        extractor.advance()
                    }
                }
            }

            val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    outputFormat = decoder.outputFormat
                }
                outputBufferIndex >= 0 -> {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val bytes = ByteArray(bufferInfo.size)
                        outputBuffer.get(bytes)
                        outputStream.write(bytes)
                    }
                    decoder.releaseOutputBuffer(outputBufferIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }
        }
    } finally {
        outputStream.flush()
        outputStream.close()
        decoder.stop()
        decoder.release()
        extractor.release()
    }

    return PreparedPcmAudio(
        pcmFile = outputFile,
        sampleRate = outputFormat.optInt(MediaFormat.KEY_SAMPLE_RATE, SherpaAsrSampleRate),
        channelCount = outputFormat.optInt(MediaFormat.KEY_CHANNEL_COUNT, 1),
    )
}

private fun MediaFormat.optInt(
    key: String,
    fallback: Int,
): Int {
    return if (containsKey(key)) getInteger(key) else fallback
}
