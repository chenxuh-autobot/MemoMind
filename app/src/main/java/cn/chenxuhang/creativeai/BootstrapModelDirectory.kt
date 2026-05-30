package cn.chenxuhang.creativeai

import java.io.File

fun resolveBootstrapModelDirectory(defaultDirectory: File): File {
    val configUrl = BuildConfig.QWEN_1_5B_TEXT_CONFIG_URL
    if (configUrl.startsWith("file://")) {
        val resolved = File(configUrl.removePrefix("file://")).parentFile
        if (resolved != null && resolved.exists()) {
            return resolved
        }
    }
    return defaultDirectory
}
