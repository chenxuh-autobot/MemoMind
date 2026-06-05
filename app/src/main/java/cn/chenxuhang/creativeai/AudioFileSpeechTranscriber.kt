package cn.chenxuhang.creativeai

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
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
    try {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(preparedAudio.toMonoFloatArray(), preparedAudio.sampleRate)
            recognizer.decode(stream)
            val text = recognizer.getResult(stream).text.normalizeTranscriptFragment()
            AudioFileTranscriptionResult(
                text = text,
                modeLabel = "端侧文件转写（SenseVoice）",
            )
        } finally {
            stream.release()
        }
    } finally {
        recognizer.release()
        preparedAudio.pcmFile.delete()
    }
}

private data class PreparedPcmAudio(
    val pcmFile: File,
    val sampleRate: Int,
    val channelCount: Int,
)

private fun PreparedPcmAudio.toMonoFloatArray(): FloatArray {
    val bytes = pcmFile.readBytes()
    if (bytes.isEmpty()) return FloatArray(0)

    val shorts = ShortArray(bytes.size / 2)
    var shortIndex = 0
    var byteIndex = 0
    while (byteIndex + 1 < bytes.size) {
        val low = bytes[byteIndex].toInt() and 0xff
        val high = bytes[byteIndex + 1].toInt()
        shorts[shortIndex++] = ((high shl 8) or low).toShort()
        byteIndex += 2
    }

    if (channelCount <= 1) {
        return FloatArray(shortIndex) { index -> shorts[index] / 32768.0f }
    }

    val frameCount = shortIndex / channelCount
    return FloatArray(frameCount) { frameIndex ->
        var sum = 0f
        repeat(channelCount) { channelIndex ->
            sum += shorts[frameIndex * channelCount + channelIndex] / 32768.0f
        }
        sum / channelCount
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
