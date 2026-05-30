package cn.chenxuhang.creativeai

import android.media.MediaRecorder
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioNoteRecorder(
    private val outputDirectory: File,
) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null

    val isRecording: Boolean
        get() = mediaRecorder != null

    fun startRecording(): File {
        check(!isRecording) { "Audio recorder is already running." }
        outputDirectory.mkdirs()
        val outputFile = File(
            outputDirectory,
            "voice_note_${timestampLabel()}.m4a",
        )
        val recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
        mediaRecorder = recorder
        currentOutputFile = outputFile
        return outputFile
    }

    fun finishRecording(): SelectedLocalAsset? {
        val recorder = mediaRecorder ?: return null
        val outputFile = currentOutputFile
        runCatching { recorder.stop() }
        recorder.reset()
        recorder.release()
        mediaRecorder = null
        currentOutputFile = null
        return outputFile
            ?.takeIf { it.exists() }
            ?.toSelectedLocalAsset()
    }

    fun cancelRecording() {
        val recorder = mediaRecorder ?: return
        runCatching { recorder.stop() }
        recorder.reset()
        recorder.release()
        mediaRecorder = null
        currentOutputFile?.delete()
        currentOutputFile = null
    }
}

private fun File.toSelectedLocalAsset(): SelectedLocalAsset {
    return SelectedLocalAsset(
        uri = Uri.fromFile(this).toString(),
        displayName = name,
        mimeTypeLabel = "audio/mp4",
    )
}

private fun timestampLabel(): String {
    return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
