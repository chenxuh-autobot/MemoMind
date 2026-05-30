package cn.chenxuhang.creativeai

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

class AudioPreviewPlayer(
    private val context: Context,
    private val onStateChanged: (String?) -> Unit,
    private val onStatusMessage: (String) -> Unit,
) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentUri: String? = null

    fun togglePlayback(
        assetUri: String,
    ) {
        if (currentUri == assetUri && mediaPlayer?.isPlaying == true) {
            stop()
            onStatusMessage("已停止音频播放。")
            return
        }
        stop()
        runCatching {
            val uri = Uri.parse(assetUri)
            val player = MediaPlayer().apply {
                setDataSource(context, uri)
                setOnPreparedListener {
                    it.start()
                    onStateChanged(assetUri)
                    onStatusMessage("开始播放录音素材。")
                }
                setOnCompletionListener {
                    stop()
                    onStatusMessage("录音播放完成。")
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    onStatusMessage("录音播放失败。")
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
            currentUri = assetUri
        }.onFailure { error ->
            stop()
            onStatusMessage(error.message ?: "无法播放录音素材。")
        }
    }

    fun stop() {
        mediaPlayer?.runCatching {
            if (isPlaying) {
                stop()
            }
        }
        mediaPlayer?.release()
        mediaPlayer = null
        currentUri = null
        onStateChanged(null)
    }
}
