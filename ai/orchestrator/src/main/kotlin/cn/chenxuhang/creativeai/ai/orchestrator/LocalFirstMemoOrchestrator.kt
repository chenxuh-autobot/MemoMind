package cn.chenxuhang.creativeai.ai.orchestrator

import cn.chenxuhang.creativeai.ai.modelmanager.ModelManager
import cn.chenxuhang.creativeai.core.model.DeviceCapabilityReport
import cn.chenxuhang.creativeai.core.model.DeviceProfile

enum class ExecutionPath {
    LOCAL_TEXT_PIVOT,
    LOCAL_MULTIMODAL,
    CLOUD_ASSISTED_FALLBACK,
}

data class OrchestrationPlan(
    val path: ExecutionPath,
    val selectedModelId: String?,
    val deviceCapabilityReport: DeviceCapabilityReport,
    val reason: String,
    val cloudAssistEnabled: Boolean,
)

class LocalFirstMemoOrchestrator(
    private val modelManager: ModelManager,
) {
    fun plan(
        deviceProfile: DeviceProfile,
        wantsVision: Boolean,
        wantsAudio: Boolean,
    ): OrchestrationPlan {
        val capabilityReport = modelManager.capabilityReport(deviceProfile)
        val exactModel = modelManager.recommendedModel(deviceProfile, wantsVision, wantsAudio)
        if (exactModel != null && exactModel.supportsVision == wantsVision && exactModel.supportsAudio == wantsAudio) {
            return OrchestrationPlan(
                path = ExecutionPath.LOCAL_MULTIMODAL,
                selectedModelId = exactModel.modelId,
                deviceCapabilityReport = capabilityReport,
                reason = "设备满足多模态本地模型要求，优先走端侧直达链路。",
                cloudAssistEnabled = true,
            )
        }

        val textPivotModel = modelManager.recommendedModel(
            deviceProfile = deviceProfile,
            wantsVision = false,
            wantsAudio = false,
        )
        if (textPivotModel != null) {
            return OrchestrationPlan(
                path = ExecutionPath.LOCAL_TEXT_PIVOT,
                selectedModelId = textPivotModel.modelId,
                deviceCapabilityReport = capabilityReport,
                reason = "默认先把图片和音频转成文本上下文，再交给本地 Qwen 文本模型。",
                cloudAssistEnabled = true,
            )
        }

        return OrchestrationPlan(
            path = ExecutionPath.CLOUD_ASSISTED_FALLBACK,
            selectedModelId = null,
            deviceCapabilityReport = capabilityReport,
            reason = "设备条件不足，保留本地采集与缓存，必要时启用云端辅助。",
            cloudAssistEnabled = true,
        )
    }
}
