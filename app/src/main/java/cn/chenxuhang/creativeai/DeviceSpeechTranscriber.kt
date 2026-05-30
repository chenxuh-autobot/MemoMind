package cn.chenxuhang.creativeai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

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
        return SpeechRecognizer.isRecognitionAvailable(context) ||
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }
}

class DeviceSpeechTranscriber(
    private val context: Context,
    private val onTranscript: (SpeechTranscriptUpdate) -> Unit,
    private val onStateChanged: (SpeechTranscriberState) -> Unit,
    private val onStatusMessage: (String) -> Unit,
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isRunning = false
    private var isUsingOnDevice = false

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onStatusMessage(if (isUsingOnDevice) "端侧语音识别已就绪，请开始说话。" else "系统语音识别已就绪，请开始说话。")
        }

        override fun onBeginningOfSpeech() {
            onStatusMessage("已检测到说话，正在转写...")
        }

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            onStatusMessage("已结束说话，正在整理最终转写结果...")
        }

        override fun onError(error: Int) {
            isRunning = false
            notifyState()
            onStatusMessage("语音转写失败: ${errorCodeToMessage(error)}")
        }

        override fun onResults(results: Bundle) {
            val text = results.bestRecognitionText()
            if (text.isNotBlank()) {
                onTranscript(SpeechTranscriptUpdate(text = text, isFinal = true))
            }
            isRunning = false
            notifyState()
            onStatusMessage("语音转写完成。")
        }

        override fun onPartialResults(partialResults: Bundle) {
            val text = partialResults.bestRecognitionText()
            if (text.isNotBlank()) {
                onTranscript(SpeechTranscriptUpdate(text = text, isFinal = false))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    fun currentModeLabel(): String {
        return if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            "本地优先"
        } else {
            "系统识别"
        }
    }

    fun startListening() {
        ensureRecognizer()
        val recognizer = speechRecognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.CHINA.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        isRunning = true
        notifyState()
        recognizer.startListening(intent)
    }

    fun stopListening() {
        if (!isRunning) return
        speechRecognizer?.stopListening()
        onStatusMessage("正在停止语音转写...")
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isRunning = false
    }

    private fun ensureRecognizer() {
        if (speechRecognizer != null) return
        val useOnDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        isUsingOnDevice = useOnDevice
        speechRecognizer = if (useOnDevice) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }.also {
            it.setRecognitionListener(recognitionListener)
        }
        notifyState()
    }

    private fun notifyState() {
        onStateChanged(
            SpeechTranscriberState(
                isRunning = isRunning,
                modeLabel = if (isUsingOnDevice) "端侧语音识别" else "系统语音识别",
            ),
        )
    }
}

private fun Bundle.bestRecognitionText(): String {
    return getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        .orEmpty()
}

private fun errorCodeToMessage(
    error: Int,
): String {
    return when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "音频采集异常"
        SpeechRecognizer.ERROR_CLIENT -> "客户端请求异常"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少录音权限"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "当前语言不支持"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "当前语言模型暂不可用"
        SpeechRecognizer.ERROR_NETWORK -> "网络错误"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
        SpeechRecognizer.ERROR_NO_MATCH -> "没有识别到有效语音"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器正忙"
        SpeechRecognizer.ERROR_SERVER -> "识别服务异常"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "识别服务已断开"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "说话超时"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "请求过于频繁"
        else -> "未知错误($error)"
    }
}
