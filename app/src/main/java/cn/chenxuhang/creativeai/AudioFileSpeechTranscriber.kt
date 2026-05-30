package cn.chenxuhang.creativeai

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class AudioFileTranscriptionResult(
    val text: String,
    val modeLabel: String,
)

suspend fun Context.retranscribeAudioFile(
    audioUri: Uri,
    cacheDirectory: File,
): AudioFileTranscriptionResult {
    require(Build.VERSION.SDK_INT >= 33) {
        "音频文件级重跑转写需要 Android 13 及以上系统。"
    }
    val preparedAudio = withContext(Dispatchers.IO) {
        decodeAudioFileToPcm(
            context = this@retranscribeAudioFile,
            sourceUri = audioUri,
            cacheDirectory = cacheDirectory,
        )
    }
    var parcelFileDescriptor: ParcelFileDescriptor? = null
    var recognizer: SpeechRecognizer? = null
    return try {
        suspendCancellableCoroutine { continuation ->
            val useOnDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(this@retranscribeAudioFile)
            val modeLabel = if (useOnDevice) "端侧文件转写" else "系统文件转写"
            parcelFileDescriptor = ParcelFileDescriptor.open(
                preparedAudio.pcmFile,
                ParcelFileDescriptor.MODE_READ_ONLY,
            )
            recognizer = if (useOnDevice) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this@retranscribeAudioFile)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this@retranscribeAudioFile)
            }.also { speechRecognizer ->
                speechRecognizer.setRecognitionListener(
                    object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) = Unit
                        override fun onBeginningOfSpeech() = Unit
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() = Unit
                        override fun onPartialResults(partialResults: Bundle) = Unit
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit

                        override fun onResults(results: Bundle) {
                            val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                .orEmpty()
                            continuation.resume(
                                AudioFileTranscriptionResult(
                                    text = text,
                                    modeLabel = modeLabel,
                                ),
                            )
                        }

                        override fun onError(error: Int) {
                            continuation.resumeWithException(
                                IllegalStateException("音频文件转写失败: ${audioSpeechErrorMessage(error)}"),
                            )
                        }
                    },
                )
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.CHINA.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, parcelFileDescriptor)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, preparedAudio.channelCount)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, preparedAudio.pcmEncoding)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, preparedAudio.sampleRate)
            }
            recognizer?.startListening(intent)
        }
    } finally {
        runCatching { recognizer?.destroy() }
        runCatching { parcelFileDescriptor?.close() }
        preparedAudio.pcmFile.delete()
    }
}

private data class PreparedPcmAudio(
    val pcmFile: File,
    val sampleRate: Int,
    val channelCount: Int,
    val pcmEncoding: Int,
)

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
        sampleRate = outputFormat.optInt(MediaFormat.KEY_SAMPLE_RATE, 16_000),
        channelCount = outputFormat.optInt(MediaFormat.KEY_CHANNEL_COUNT, 1),
        pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        },
    )
}

private fun MediaFormat.optInt(
    key: String,
    fallback: Int,
): Int {
    return if (containsKey(key)) getInteger(key) else fallback
}

private fun audioSpeechErrorMessage(
    errorCode: Int,
): String {
    return when (errorCode) {
        SpeechRecognizer.ERROR_AUDIO -> "录音输入异常"
        SpeechRecognizer.ERROR_CLIENT -> "识别客户端异常"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少语音识别权限"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "当前语言不受支持"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "当前语言暂不可用"
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        -> "识别服务网络异常"
        SpeechRecognizer.ERROR_NO_MATCH -> "没有识别到有效语音内容"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别服务忙碌"
        SpeechRecognizer.ERROR_SERVER -> "识别服务异常"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "识别服务已断开"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音输入超时"
        else -> "未知错误($errorCode)"
    }
}
