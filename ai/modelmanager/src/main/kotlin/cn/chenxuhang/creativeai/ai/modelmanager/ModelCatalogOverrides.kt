package cn.chenxuhang.creativeai.ai.modelmanager

import cn.chenxuhang.creativeai.core.model.ModelAsset
import cn.chenxuhang.creativeai.core.model.ModelManifest

data class ModelAssetOverride(
    val downloadUrl: String? = null,
    val sha256: String? = null,
)

data class ModelOverride(
    val assets: Map<String, ModelAssetOverride>,
)

data class ModelCatalogOverrides(
    val models: Map<String, ModelOverride>,
) {
    companion object {
        val Empty = ModelCatalogOverrides(models = emptyMap())
    }
}

fun applyModelCatalogOverrides(
    manifests: List<ModelManifest>,
    overrides: ModelCatalogOverrides,
): List<ModelManifest> {
    return manifests.map { manifest ->
        val modelOverride = overrides.models[manifest.modelId] ?: return@map manifest
        manifest.copy(
            assets = manifest.assets.map { asset ->
                val assetOverride = modelOverride.assets[asset.fileName] ?: return@map asset
                asset.copy(
                    downloadUrl = assetOverride.downloadUrl?.takeIf { it.isNotBlank() } ?: asset.downloadUrl,
                    sha256 = assetOverride.sha256?.takeIf { it.isNotBlank() } ?: asset.sha256,
                )
            },
        )
    }
}
