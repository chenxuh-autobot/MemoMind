package cn.chenxuhang.creativeai.ai.mnn

import cn.chenxuhang.creativeai.core.model.MnnSessionConfig
import cn.chenxuhang.creativeai.core.model.ModelProbeResult
import cn.chenxuhang.creativeai.core.model.SessionOpenResult
import cn.chenxuhang.creativeai.core.model.TextGenerationResult

enum class MnnRuntimeState {
    UNINITIALIZED,
    STUB_READY,
    NATIVE_LOAD_FAILED,
    NATIVE_READY,
}

interface MnnRuntime {
    fun state(): MnnRuntimeState
    fun describe(): String
    fun runtimeVersion(): String
    fun supportsRealMnn(): Boolean
    fun probeModelDirectory(modelId: String?, modelDirectory: String): ModelProbeResult
    fun openSession(config: MnnSessionConfig): SessionOpenResult
    fun generateText(config: MnnSessionConfig, prompt: String, maxNewTokens: Int): TextGenerationResult
}

class StubMnnRuntime : MnnRuntime {
    override fun state(): MnnRuntimeState = MnnRuntimeState.STUB_READY

    override fun describe(): String {
        return "Stub runtime ready. Replace with JNI + MNN session bootstrap next."
    }

    override fun runtimeVersion(): String = "stub-runtime/0.1"

    override fun supportsRealMnn(): Boolean = false

    override fun probeModelDirectory(modelId: String?, modelDirectory: String): ModelProbeResult {
        return ModelProbeResult(
            modelId = modelId,
            modelDirectory = modelDirectory,
            exists = false,
            hasTokenizer = false,
            hasWeights = false,
            hasConfig = false,
            missingFiles = listOf("tokenizer.txt|tokenizer.json", "llm.mnn|model.mnn", "config.json"),
        )
    }

    override fun openSession(config: MnnSessionConfig): SessionOpenResult {
        return SessionOpenResult(
            success = false,
            runtimeVersion = runtimeVersion(),
            backendName = "stub",
            errorMessage = "Stub runtime cannot open model sessions.",
        )
    }

    override fun generateText(
        config: MnnSessionConfig,
        prompt: String,
        maxNewTokens: Int,
    ): TextGenerationResult {
        return TextGenerationResult(
            success = false,
            runtimeVersion = runtimeVersion(),
            backendName = "stub",
            errorMessage = "Stub runtime cannot generate text.",
        )
    }
}

data class NativeBridgeStatus(
    val state: MnnRuntimeState,
    val loadedLibraryName: String?,
    val nativeVersion: String?,
    val errorMessage: String?,
)

class NativeBackedMnnRuntime(
    private val libraryName: String = "creative_ai_mnn_bridge",
) : MnnRuntime {
    private val status: NativeBridgeStatus by lazy {
        try {
            System.loadLibrary(libraryName)
            NativeBridgeStatus(
                state = MnnRuntimeState.NATIVE_READY,
                loadedLibraryName = libraryName,
                nativeVersion = nativeRuntimeVersion(),
                errorMessage = null,
            )
        } catch (error: UnsatisfiedLinkError) {
            NativeBridgeStatus(
                state = MnnRuntimeState.NATIVE_LOAD_FAILED,
                loadedLibraryName = null,
                nativeVersion = null,
                errorMessage = error.message,
            )
        }
    }

    override fun state(): MnnRuntimeState = status.state

    override fun describe(): String {
        return when (status.state) {
            MnnRuntimeState.NATIVE_READY -> {
                "Native runtime loaded from ${status.loadedLibraryName}, version=${status.nativeVersion}, realMnn=${supportsRealMnn()}"
            }
            MnnRuntimeState.NATIVE_LOAD_FAILED -> {
                "Native runtime load failed: ${status.errorMessage}"
            }
            MnnRuntimeState.STUB_READY -> "Unexpected state: stub"
            MnnRuntimeState.UNINITIALIZED -> "Native runtime not initialized"
        }
    }

    override fun runtimeVersion(): String = status.nativeVersion ?: "unknown"

    override fun supportsRealMnn(): Boolean {
        return status.state == MnnRuntimeState.NATIVE_READY && nativeSupportsRealMnn()
    }

    override fun probeModelDirectory(modelId: String?, modelDirectory: String): ModelProbeResult {
        if (status.state != MnnRuntimeState.NATIVE_READY) {
            return ModelProbeResult(
                modelId = modelId,
                modelDirectory = modelDirectory,
                exists = false,
                hasTokenizer = false,
                hasWeights = false,
                hasConfig = false,
                missingFiles = listOf("native-runtime-unavailable"),
            )
        }
        val result = nativeProbeModelDirectory(modelDirectory)
        return result.copy(modelId = modelId ?: result.modelId)
    }

    override fun openSession(config: MnnSessionConfig): SessionOpenResult {
        if (status.state != MnnRuntimeState.NATIVE_READY) {
            return SessionOpenResult(
                success = false,
                runtimeVersion = runtimeVersion(),
                backendName = "native-unavailable",
                errorMessage = status.errorMessage ?: "Native runtime unavailable.",
            )
        }
        return nativeOpenSession(
            modelId = config.modelId,
            modelDirectory = config.modelDirectory,
            backendName = config.backend.name,
            threadCount = config.threadCount,
            enableLowMemoryMode = config.enableLowMemoryMode,
            enableMultimodalPath = config.enableMultimodalPath,
        )
    }

    override fun generateText(
        config: MnnSessionConfig,
        prompt: String,
        maxNewTokens: Int,
    ): TextGenerationResult {
        if (status.state != MnnRuntimeState.NATIVE_READY) {
            return TextGenerationResult(
                success = false,
                runtimeVersion = runtimeVersion(),
                backendName = "native-unavailable",
                errorMessage = status.errorMessage ?: "Native runtime unavailable.",
            )
        }
        return nativeGenerateText(
            modelId = config.modelId,
            modelDirectory = config.modelDirectory,
            backendName = config.backend.name,
            threadCount = config.threadCount,
            enableLowMemoryMode = config.enableLowMemoryMode,
            enableMultimodalPath = config.enableMultimodalPath,
            prompt = prompt,
            maxNewTokens = maxNewTokens,
        )
    }

    fun bridgeStatus(): NativeBridgeStatus = status

    external fun nativeRuntimeVersion(): String
    external fun nativeSupportsRealMnn(): Boolean
    external fun nativeProbeModelDirectory(modelDirectory: String): ModelProbeResult
    external fun nativeOpenSession(
        modelId: String,
        modelDirectory: String,
        backendName: String,
        threadCount: Int,
        enableLowMemoryMode: Boolean,
        enableMultimodalPath: Boolean,
    ): SessionOpenResult

    external fun nativeGenerateText(
        modelId: String,
        modelDirectory: String,
        backendName: String,
        threadCount: Int,
        enableLowMemoryMode: Boolean,
        enableMultimodalPath: Boolean,
        prompt: String,
        maxNewTokens: Int,
    ): TextGenerationResult
}
