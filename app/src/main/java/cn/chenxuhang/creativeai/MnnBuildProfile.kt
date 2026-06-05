package cn.chenxuhang.creativeai

import android.content.Context
import org.json.JSONObject

data class BundledMnnBuildProfile(
    val androidAbi: String,
    val mnnSme2Enabled: Boolean,
    val mnnKleidiAiEnabled: Boolean,
    val mnnKleidiAiDefaultOn: Boolean,
    val cpuSmeCoreNum: Int,
    val cpuSme2NeonDivisionRatio: Int,
    val source: String,
    val notes: String,
)

fun Context.loadBundledMnnBuildProfile(): BundledMnnBuildProfile? {
    return runCatching {
        assets.open("mnn/mnn_build_profile.json").bufferedReader().use { reader ->
            val json = JSONObject(reader.readText())
            BundledMnnBuildProfile(
                androidAbi = json.optString("androidAbi", "arm64-v8a"),
                mnnSme2Enabled = json.optBoolean("mnnSme2Enabled", false),
                mnnKleidiAiEnabled = json.optBoolean("mnnKleidiAiEnabled", false),
                mnnKleidiAiDefaultOn = json.optBoolean("mnnKleidiAiDefaultOn", false),
                cpuSmeCoreNum = json.optInt("cpuSmeCoreNum", 2),
                cpuSme2NeonDivisionRatio = json.optInt("cpuSme2NeonDivisionRatio", 41),
                source = json.optString("source", "unknown"),
                notes = json.optString("notes", ""),
            )
        }
    }.getOrNull()
}
