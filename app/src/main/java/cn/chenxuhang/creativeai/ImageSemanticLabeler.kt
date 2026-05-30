package cn.chenxuhang.creativeai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class ImageSemanticSummary(
    val summaryText: String,
    val labels: List<String>,
)

suspend fun Context.extractImageSemanticSummary(
    imageUri: Uri,
): ImageSemanticSummary {
    val image = InputImage.fromFilePath(this, imageUri)
    val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.65f)
            .build(),
    )
    return try {
        suspendCancellableCoroutine { continuation ->
            labeler.process(image)
                .addOnSuccessListener { labels ->
                    val normalized = labels
                        .sortedByDescending { it.confidence }
                        .mapNotNull { label ->
                            label.text
                                .trim()
                                .takeIf { it.isNotBlank() }
                        }
                        .distinct()
                        .take(5)
                    continuation.resume(
                        ImageSemanticSummary(
                            summaryText = normalized.joinToString("、"),
                            labels = normalized,
                        ),
                    )
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
        }
    } finally {
        labeler.close()
    }
}
