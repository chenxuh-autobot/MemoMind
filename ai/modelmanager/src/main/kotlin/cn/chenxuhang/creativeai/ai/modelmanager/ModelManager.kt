package cn.chenxuhang.creativeai.ai.modelmanager

import cn.chenxuhang.creativeai.core.model.DeviceCapabilityReport
import cn.chenxuhang.creativeai.core.model.DeviceProfile
import cn.chenxuhang.creativeai.core.model.DeviceTier
import cn.chenxuhang.creativeai.core.model.InstalledModel
import cn.chenxuhang.creativeai.core.model.ModelAsset
import cn.chenxuhang.creativeai.core.model.ModelAssetKind
import cn.chenxuhang.creativeai.core.model.ModelInstallStatus
import cn.chenxuhang.creativeai.core.model.ModelManifest
import java.io.File

interface ModelManager {
    fun catalog(): List<ModelManifest>
    fun installedModels(): List<InstalledModel>
    fun capabilityReport(deviceProfile: DeviceProfile): DeviceCapabilityReport
    fun recommendedModel(deviceProfile: DeviceProfile, wantsVision: Boolean, wantsAudio: Boolean): ModelManifest?
    fun markInstalled(modelId: String, localDirectory: String)
    fun installPlan(modelId: String, localDirectory: String): ModelInstallPlan?
    fun validateInstallation(modelId: String, localDirectory: String): ModelInstallStatus
}

data class ModelInstallPlan(
    val modelId: String,
    val localDirectory: String,
    val requiredAssets: List<PlannedModelAsset>,
    val requiredFreeBytes: Long,
)

data class PlannedModelAsset(
    val fileName: String,
    val targetPath: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
)

class InMemoryModelManager(
    private val models: List<ModelManifest>,
    private val installedDirectories: MutableMap<String, String> = mutableMapOf(),
) : ModelManager {
    override fun catalog(): List<ModelManifest> = models

    override fun installedModels(): List<InstalledModel> {
        return models.map { manifest ->
            val localDirectory = installedDirectories[manifest.modelId]
            InstalledModel(
                manifest = manifest,
                installStatus = if (localDirectory == null) {
                    ModelInstallStatus.NOT_INSTALLED
                } else {
                    ModelInstallStatus.INSTALLED
                },
                localDirectory = localDirectory,
            )
        }
    }

    override fun capabilityReport(deviceProfile: DeviceProfile): DeviceCapabilityReport {
        val tier = DeviceTierClassifier.classify(deviceProfile)
        val canRunTextModel = models.any {
            !it.supportsVision && !it.supportsAudio && deviceProfile.ramClassGb >= it.recommendedMinRamGb
        }
        val canRunMultimodalModel = models.any {
            it.supportsVision && it.supportsAudio && deviceProfile.ramClassGb >= it.recommendedMinRamGb
        }
        val notes = buildList {
            add("deviceTier=$tier")
            add("ram=${deviceProfile.ramClassGb}GB")
            add("abi=${deviceProfile.abi}")
            if (deviceProfile.supportsNnapi) add("nnapi=enabled")
            if (deviceProfile.supportsGpuPath) add("gpuPath=enabled")
        }
        return DeviceCapabilityReport(
            deviceTier = tier,
            canRunTextModel = canRunTextModel,
            canRunMultimodalModel = canRunMultimodalModel,
            notes = notes,
        )
    }

    override fun recommendedModel(
        deviceProfile: DeviceProfile,
        wantsVision: Boolean,
        wantsAudio: Boolean,
    ): ModelManifest? {
        return models
            .filter { deviceProfile.ramClassGb >= it.recommendedMinRamGb }
            .filter { !wantsVision || it.supportsVision }
            .filter { !wantsAudio || it.supportsAudio }
            .maxByOrNull { it.recommendedMaxInput }
            ?: models
                .filter { deviceProfile.ramClassGb >= it.recommendedMinRamGb }
                .maxByOrNull { it.recommendedMaxInput }
    }

    override fun markInstalled(modelId: String, localDirectory: String) {
        installedDirectories[modelId] = localDirectory
    }

    override fun installPlan(modelId: String, localDirectory: String): ModelInstallPlan? {
        val manifest = models.firstOrNull { it.modelId == modelId } ?: return null
        return ModelInstallPlan(
            modelId = modelId,
            localDirectory = localDirectory,
            requiredAssets = manifest.assets.map { asset ->
                PlannedModelAsset(
                    fileName = asset.fileName,
                    targetPath = File(localDirectory, asset.fileName).absolutePath,
                    downloadUrl = asset.downloadUrl,
                    sha256 = asset.sha256,
                    sizeBytes = asset.sizeBytes,
                )
            },
            requiredFreeBytes = manifest.estimatedStorageBytes,
        )
    }

    override fun validateInstallation(modelId: String, localDirectory: String): ModelInstallStatus {
        val manifest = models.firstOrNull { it.modelId == modelId } ?: return ModelInstallStatus.BROKEN
        val installRoot = File(localDirectory)
        if (!installRoot.exists()) return ModelInstallStatus.NOT_INSTALLED
        val allFilesExist = manifest.assets.all { asset -> File(installRoot, asset.fileName).exists() }
        return if (allFilesExist) ModelInstallStatus.INSTALLED else ModelInstallStatus.BROKEN
    }

    companion object {
        fun bootstrapDefaults(
            overrides: ModelCatalogOverrides = ModelCatalogOverrides.Empty,
        ): InMemoryModelManager {
            val manifests = applyModelCatalogOverrides(
                manifests = listOf(
                    ModelManifest(
                        modelId = "qwen-local-1_5b-text",
                        displayName = "Qwen Local Text 1.5B",
                        recommendedMinRamGb = 8,
                        supportsVision = false,
                        supportsAudio = false,
                        recommendedMaxInput = 6000,
                        recommendedDeviceTier = DeviceTier.A,
                        estimatedStorageBytes = 1_900_000_000,
                        assets = listOf(
                            ModelAsset(
                                fileName = "tokenizer.txt",
                                downloadUrl = "https://example.com/models/qwen-local-1_5b-text/tokenizer.txt",
                                sha256 = "todo-tokenizer-txt-sha256",
                                kind = ModelAssetKind.TOKENIZER,
                                sizeBytes = 8_000_000,
                            ),
                            ModelAsset(
                                fileName = "llm.mnn",
                                downloadUrl = "https://example.com/models/qwen-local-1_5b-text/llm.mnn",
                                sha256 = "todo-llm-mnn-sha256",
                                kind = ModelAssetKind.MODEL_GRAPH,
                                sizeBytes = 120_000_000,
                            ),
                            ModelAsset(
                                fileName = "llm.mnn.weight",
                                downloadUrl = "https://example.com/models/qwen-local-1_5b-text/llm.mnn.weight",
                                sha256 = "todo-llm-weight-sha256",
                                kind = ModelAssetKind.MODEL_WEIGHT,
                                sizeBytes = 1_620_000_000,
                            ),
                            ModelAsset(
                                fileName = "llm_config.json",
                                downloadUrl = "https://example.com/models/qwen-local-1_5b-text/llm_config.json",
                                sha256 = "todo-llm-config-sha256",
                                kind = ModelAssetKind.MODEL_CONFIG,
                                sizeBytes = 40_000,
                            ),
                            ModelAsset(
                                fileName = "config.json",
                                downloadUrl = "https://example.com/models/qwen-local-1_5b-text/config.json",
                                sha256 = "todo-runtime-config-sha256",
                                kind = ModelAssetKind.RUNTIME_CONFIG,
                                sizeBytes = 40_000,
                            ),
                        ),
                        promptTemplateVersion = "v1",
                    ),
                    ModelManifest(
                        modelId = "qwen-local-3b-multimodal",
                        displayName = "Qwen Local Multimodal 3B",
                        recommendedMinRamGb = 12,
                        supportsVision = true,
                        supportsAudio = true,
                        recommendedMaxInput = 8000,
                        recommendedDeviceTier = DeviceTier.S,
                        estimatedStorageBytes = 4_600_000_000,
                        assets = listOf(
                            ModelAsset(
                                fileName = "tokenizer.json",
                                downloadUrl = "https://example.com/models/qwen-local-3b-multimodal/tokenizer.json",
                                sha256 = "todo-tokenizer-sha256",
                                kind = ModelAssetKind.TOKENIZER,
                                sizeBytes = 12_000_000,
                            ),
                            ModelAsset(
                                fileName = "config.json",
                                downloadUrl = "https://example.com/models/qwen-local-3b-multimodal/config.json",
                                sha256 = "todo-config-sha256",
                                kind = ModelAssetKind.RUNTIME_CONFIG,
                                sizeBytes = 40_000,
                            ),
                            ModelAsset(
                                fileName = "model.mnn",
                                downloadUrl = "https://example.com/models/qwen-local-3b-multimodal/model.mnn",
                                sha256 = "todo-model-sha256",
                                kind = ModelAssetKind.MODEL_GRAPH,
                                sizeBytes = 4_550_000_000,
                            ),
                        ),
                        promptTemplateVersion = "v1",
                    ),
                ),
                overrides = overrides,
            )
            return InMemoryModelManager(
                models = manifests,
            )
        }
    }
}

object DeviceTierClassifier {
    fun classify(deviceProfile: DeviceProfile): DeviceTier {
        return when {
            deviceProfile.ramClassGb >= 12 && deviceProfile.supportsNnapi -> DeviceTier.S
            deviceProfile.ramClassGb >= 8 -> DeviceTier.A
            else -> DeviceTier.B
        }
    }
}
