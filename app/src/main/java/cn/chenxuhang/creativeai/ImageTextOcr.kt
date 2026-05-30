package cn.chenxuhang.creativeai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class OcrRecognitionResult(
    val text: String,
    val blockCount: Int,
)

suspend fun Context.runChineseImageOcr(
    imageUri: Uri,
): OcrRecognitionResult {
    val image = InputImage.fromFilePath(this, imageUri)
    val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    return try {
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    continuation.resume(
                        OcrRecognitionResult(
                            text = result.text,
                            blockCount = result.textBlocks.size,
                        ),
                    )
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
        }
    } finally {
        recognizer.close()
    }
}
