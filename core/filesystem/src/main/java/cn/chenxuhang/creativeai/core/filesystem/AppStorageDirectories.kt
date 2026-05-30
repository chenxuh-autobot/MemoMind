package cn.chenxuhang.creativeai.core.filesystem

import android.content.Context
import java.io.File

class AppStorageDirectories(
    context: Context,
) {
    private val rootDir = File(context.filesDir, "creative_ai").apply { mkdirs() }
    val modelsDir: File = File(rootDir, "models").apply { mkdirs() }
    val memosDir: File = File(rootDir, "memos").apply { mkdirs() }
    val cacheDir: File = File(rootDir, "cache").apply { mkdirs() }
    val recordingsDir: File = File(rootDir, "recordings").apply { mkdirs() }
    val taskIndexFile: File = File(rootDir, "tasks.json").apply {
        parentFile?.mkdirs()
        if (!exists()) writeText("[]")
    }
    val memoIndexFile: File = File(rootDir, "structured_memos.json").apply {
        parentFile?.mkdirs()
        if (!exists()) writeText("[]")
    }

    fun modelDir(modelId: String): File {
        return File(modelsDir, modelId).apply { mkdirs() }
    }
}
