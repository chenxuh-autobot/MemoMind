package cn.chenxuhang.creativeai.core.network

data class CloudAssistPolicy(
    val enabledByDefault: Boolean,
    val allowFallbackOnFailure: Boolean,
)

interface CloudAssistClient {
    fun policy(): CloudAssistPolicy
    fun isAvailable(): Boolean
}

class DisabledCloudAssistClient : CloudAssistClient {
    override fun policy(): CloudAssistPolicy {
        return CloudAssistPolicy(
            enabledByDefault = false,
            allowFallbackOnFailure = true,
        )
    }

    override fun isAvailable(): Boolean = false
}

