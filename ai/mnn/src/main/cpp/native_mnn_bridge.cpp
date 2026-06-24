#include <jni.h>
#include <filesystem>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>
#include <algorithm>
#include <cctype>
#include <mutex>

#if defined(__aarch64__)
#include <sys/auxv.h>
#ifdef __linux__
#include <asm/hwcap.h>
#endif
#endif

#ifdef HAS_REAL_MNN
#include "MNN/Interpreter.hpp"
#endif

#ifdef HAS_REAL_MNN_LLM
#include "llm/llm.hpp"
#endif

namespace {

constexpr const char* kBridgeVersionStub = "creative-ai-mnn-bridge/0.4+stub";
constexpr const char* kBridgeVersionRealMnn = "creative-ai-mnn-bridge/0.4+real-mnn";
constexpr const char* kBridgeVersionRealLlm = "creative-ai-mnn-bridge/0.4+real-llm";

#ifdef HAS_REAL_MNN_LLM
struct LlmSessionDeleter {
    void operator()(MNN::Transformer::Llm* llm) const {
        if (llm != nullptr) {
            MNN::Transformer::Llm::destroy(llm);
        }
    }
};

struct CachedLlmSession {
    std::string key;
    std::string modelId;
    std::string modelDirectory;
    std::string backendName;
    std::unique_ptr<MNN::Transformer::Llm, LlmSessionDeleter> llm;
};

std::mutex gCachedLlmSessionMutex;
CachedLlmSession gCachedLlmSession;
#endif

std::string jstringToStdString(JNIEnv* env, jstring value) {
    const char* utfChars = env->GetStringUTFChars(value, nullptr);
    std::string result = utfChars == nullptr ? "" : utfChars;
    if (utfChars != nullptr) {
        env->ReleaseStringUTFChars(value, utfChars);
    }
    return result;
}

std::vector<uint8_t> jbyteArrayToUint8Vector(JNIEnv* env, jbyteArray value) {
    std::vector<uint8_t> result;
    if (value == nullptr) {
        return result;
    }
    const jsize length = env->GetArrayLength(value);
    if (length <= 0) {
        return result;
    }
    result.resize(static_cast<size_t>(length));
    env->GetByteArrayRegion(value, 0, length, reinterpret_cast<jbyte*>(result.data()));
    return result;
}

jclass requireClass(JNIEnv* env, const char* className) {
    return env->FindClass(className);
}

jobject newModelProbeResult(
        JNIEnv* env,
        const std::string& modelDirectory,
        bool exists,
        bool hasTokenizer,
        bool hasWeights,
        bool hasConfig,
        const std::vector<std::string>& missingFiles) {
    jclass listClass = requireClass(env, "java/util/ArrayList");
    jmethodID listCtor = env->GetMethodID(listClass, "<init>", "()V");
    jmethodID listAdd = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");
    jobject list = env->NewObject(listClass, listCtor);
    for (const auto& file : missingFiles) {
        env->CallBooleanMethod(list, listAdd, env->NewStringUTF(file.c_str()));
    }

    jclass resultClass = requireClass(env, "cn/chenxuhang/creativeai/core/model/ModelProbeResult");
    jmethodID ctor = env->GetMethodID(
            resultClass,
            "<init>",
            "(Ljava/lang/String;Ljava/lang/String;ZZZZLjava/util/List;)V");
    return env->NewObject(
            resultClass,
            ctor,
            nullptr,
            env->NewStringUTF(modelDirectory.c_str()),
            exists,
            hasTokenizer,
            hasWeights,
            hasConfig,
            list);
}

jobject newSessionOpenResult(
        JNIEnv* env,
        bool success,
        const std::string& runtimeVersion,
        const std::string& backendName,
        const std::string& sessionId,
        const std::string& errorMessage) {
    jclass resultClass = requireClass(env, "cn/chenxuhang/creativeai/core/model/SessionOpenResult");
    jmethodID ctor = env->GetMethodID(
            resultClass,
            "<init>",
            "(ZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    jobject sessionIdValue = sessionId.empty() ? nullptr : env->NewStringUTF(sessionId.c_str());
    jobject errorValue = errorMessage.empty() ? nullptr : env->NewStringUTF(errorMessage.c_str());
    return env->NewObject(
            resultClass,
            ctor,
            success,
            env->NewStringUTF(runtimeVersion.c_str()),
            env->NewStringUTF(backendName.c_str()),
            sessionIdValue,
            errorValue);
}

jobject newTextGenerationResult(
        JNIEnv* env,
        bool success,
        const std::string& runtimeVersion,
        const std::string& backendName,
        const std::string& outputText,
        const std::string& errorMessage) {
    jclass resultClass = requireClass(env, "cn/chenxuhang/creativeai/core/model/TextGenerationResult");
    jmethodID ctor = env->GetMethodID(
            resultClass,
            "<init>",
            "(ZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    jobject outputValue = outputText.empty() ? nullptr : env->NewStringUTF(outputText.c_str());
    jobject errorValue = errorMessage.empty() ? nullptr : env->NewStringUTF(errorMessage.c_str());
    return env->NewObject(
            resultClass,
            ctor,
            success,
            env->NewStringUTF(runtimeVersion.c_str()),
            env->NewStringUTF(backendName.c_str()),
            outputValue,
            errorValue);
}

jobject newCpuAccelerationProbeResult(
        JNIEnv* env,
        bool isArm64,
        bool hasSme,
        bool hasSme2,
        const std::string& detectionSource,
        const std::string& rawHints) {
    jclass resultClass = requireClass(env, "cn/chenxuhang/creativeai/core/model/CpuAccelerationProbeResult");
    jmethodID ctor = env->GetMethodID(
            resultClass,
            "<init>",
            "(ZZZLjava/lang/String;Ljava/lang/String;)V");
    jobject rawHintsValue = rawHints.empty() ? nullptr : env->NewStringUTF(rawHints.c_str());
    return env->NewObject(
            resultClass,
            ctor,
            isArm64,
            hasSme,
            hasSme2,
            env->NewStringUTF(detectionSource.c_str()),
            rawHintsValue);
}

std::string runtimeVersionForCurrentBuild() {
#ifdef HAS_REAL_MNN_LLM
    return kBridgeVersionRealLlm;
#elif defined(HAS_REAL_MNN)
    return kBridgeVersionRealMnn;
#else
    return kBridgeVersionStub;
#endif
}

std::string backendLabelForCurrentBuild() {
#ifdef HAS_REAL_MNN_LLM
    return "prebuilt-mnn-llm";
#elif defined(HAS_REAL_MNN)
    return "prebuilt-mnn";
#else
    return "stub-probe";
#endif
}

std::string backendTypeForRuntime(const std::string& backendName) {
    if (backendName == "OPENCL") {
        return "opencl";
    }
    if (backendName == "NNAPI") {
        // MNN-LLM runtime uses "npu" for device-accelerated execution.
        return "npu";
    }
    return "cpu";
}

std::string jsonBool(bool value) {
    return value ? "true" : "false";
}

std::string toLower(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    return value;
}

std::string buildRuntimeConfigJson(
        const std::filesystem::path& modelDirectory,
        const std::string& backendName,
        int threadCount,
        bool enableLowMemoryMode,
        int cpuSmeCoreCount,
        int cpuSme2NeonDivisionRatio) {
    const std::filesystem::path mmapDirectory = modelDirectory / ".mnn-cache";
    std::error_code errorCode;
    std::filesystem::create_directories(mmapDirectory, errorCode);

    std::ostringstream config;
    config << "{";
    config << "\"backend_type\":\"" << backendTypeForRuntime(backendName) << "\",";
    config << "\"thread_num\":" << threadCount << ",";
    config << "\"memory\":\"" << (enableLowMemoryMode ? "low" : "high") << "\",";
    config << "\"precision\":\"low\",";
    config << "\"use_mmap\":" << jsonBool(enableLowMemoryMode) << ",";
    config << "\"cpu_sme_core_num\":" << cpuSmeCoreCount << ",";
    config << "\"cpu_sme2_neon_division_ratio\":" << cpuSme2NeonDivisionRatio << ",";
    config << "\"tmp_path\":\"" << mmapDirectory.string() << "\"";
    config << "}";
    return config.str();
}

std::filesystem::path runtimeCacheDirectoryFor(
        const std::filesystem::path& modelDirectory) {
    return modelDirectory / ".mnn-cache";
}

void resetRuntimeCacheDirectory(
        const std::filesystem::path& modelDirectory) {
    const std::filesystem::path mmapDirectory = runtimeCacheDirectoryFor(modelDirectory);
    std::error_code errorCode;
    std::filesystem::remove_all(mmapDirectory, errorCode);
    errorCode.clear();
    std::filesystem::create_directories(mmapDirectory, errorCode);
}

std::string readTextFile(
        const std::filesystem::path& path) {
    std::ifstream input(path);
    if (!input.is_open()) {
        return "";
    }
    std::stringstream buffer;
    buffer << input.rdbuf();
    return buffer.str();
}

std::string detectCpuHintText() {
    return readTextFile("/proc/cpuinfo").substr(0, 4000);
}

bool modelRequiresVisualAssets(
        const std::filesystem::path& root) {
    const std::string config = readTextFile(root / "llm_config.json");
    return config.find("\"is_visual\"") != std::string::npos
            && (config.find("\"is_visual\": true") != std::string::npos
            || config.find("\"is_visual\":true") != std::string::npos);
}

bool validateModelDirectoryForSession(
        const std::filesystem::path& root,
        std::string& errorMessage) {
    const bool hasTokenizer = std::filesystem::exists(root / "tokenizer.txt")
            || std::filesystem::exists(root / "tokenizer.json");
    const bool hasRuntimeConfig = std::filesystem::exists(root / "config.json");
    const bool hasLlmConfig = std::filesystem::exists(root / "llm_config.json");
    const bool hasGraph = std::filesystem::exists(root / "llm.mnn")
            || std::filesystem::exists(root / "model.mnn");
    const bool hasWeightData = !std::filesystem::exists(root / "llm.mnn")
            || std::filesystem::exists(root / "llm.mnn.weight");
    const bool requiresVisualAssets = hasLlmConfig && modelRequiresVisualAssets(root);
    const bool hasVisualGraph = !requiresVisualAssets || std::filesystem::exists(root / "visual.mnn");
    const bool hasVisualWeight = !requiresVisualAssets || std::filesystem::exists(root / "visual.mnn.weight");

    if (!hasTokenizer) {
        errorMessage = "Missing tokenizer.txt or tokenizer.json under " + root.string();
        return false;
    }
    if (!hasRuntimeConfig) {
        errorMessage = "Missing config.json under " + root.string();
        return false;
    }
    if (!hasGraph) {
        errorMessage = "Missing llm.mnn or model.mnn under " + root.string();
        return false;
    }
    if (!hasWeightData) {
        errorMessage = "Missing llm.mnn.weight under " + root.string();
        return false;
    }
    if (!hasLlmConfig) {
        errorMessage = "Missing llm_config.json under " + root.string();
        return false;
    }
    if (!hasVisualGraph) {
        errorMessage = "Missing visual.mnn under " + root.string();
        return false;
    }
    if (!hasVisualWeight) {
        errorMessage = "Missing visual.mnn.weight under " + root.string();
        return false;
    }
    return true;
}

#ifdef HAS_REAL_MNN_LLM
std::string buildSessionKey(
        const std::string& modelId,
        const std::string& modelDirectory,
        const std::string& backendName,
        int threadCount,
        bool enableLowMemoryMode,
        int cpuSmeCoreCount,
        int cpuSme2NeonDivisionRatio) {
    std::ostringstream key;
    key << modelId
        << "|dir=" << modelDirectory
        << "|backend=" << backendName
        << "|threads=" << threadCount
        << "|lowMemory=" << (enableLowMemoryMode ? "1" : "0")
        << "|smeCore=" << cpuSmeCoreCount
        << "|smeRatio=" << cpuSme2NeonDivisionRatio;
    return key.str();
}

void invalidateCachedSession() {
    gCachedLlmSession.llm.reset();
    gCachedLlmSession.key.clear();
    gCachedLlmSession.modelId.clear();
    gCachedLlmSession.modelDirectory.clear();
    gCachedLlmSession.backendName.clear();
}

bool ensureCachedSessionLoaded(
        const std::string& modelId,
        const std::filesystem::path& root,
        const std::string& backendName,
        int threadCount,
        bool enableLowMemoryMode,
        int cpuSmeCoreCount,
        int cpuSme2NeonDivisionRatio,
        std::string& errorMessage) {
    const std::string sessionKey = buildSessionKey(
            modelId,
            root.string(),
            backendName,
            threadCount,
            enableLowMemoryMode,
            cpuSmeCoreCount,
            cpuSme2NeonDivisionRatio);
    if (gCachedLlmSession.llm != nullptr && gCachedLlmSession.key == sessionKey) {
        return true;
    }

    invalidateCachedSession();
    resetRuntimeCacheDirectory(root);

    const std::filesystem::path runtimeConfigPath = root / "config.json";
    auto* llm = MNN::Transformer::Llm::createLLM(runtimeConfigPath.string());
    if (llm == nullptr) {
        errorMessage = "MNN LLM runtime failed to create cached session from config.json";
        return false;
    }

    const std::string runtimeConfig = buildRuntimeConfigJson(
            root,
            backendName,
            threadCount,
            enableLowMemoryMode,
            cpuSmeCoreCount,
            cpuSme2NeonDivisionRatio);
    llm->set_config(runtimeConfig);
    const bool loaded = llm->load();
    if (!loaded) {
        const auto* context = llm->getContext();
        const int statusCode = context == nullptr ? -999 : static_cast<int>(context->status);
        MNN::Transformer::Llm::destroy(llm);
        std::ostringstream error;
        error << "MNN LLM load() failed while preparing cached session. modelId=" << modelId
              << ", status=" << statusCode;
        errorMessage = error.str();
        return false;
    }

    gCachedLlmSession.key = sessionKey;
    gCachedLlmSession.modelId = modelId;
    gCachedLlmSession.modelDirectory = root.string();
    gCachedLlmSession.backendName = backendName;
    gCachedLlmSession.llm.reset(llm);
    return true;
}
#endif

}  // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_cn_chenxuhang_creativeai_ai_mnn_NativeBackedMnnRuntime_nativeRuntimeVersion(
        JNIEnv* env,
        jobject /* this */) {
    const std::string version = runtimeVersionForCurrentBuild();
    return env->NewStringUTF(version.c_str());
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_cn_chenxuhang_creativeai_ai_mnn_NativeBackedMnnRuntime_nativeSupportsRealMnn(
        JNIEnv* /* env */,
        jobject /* this */) {
#ifdef HAS_REAL_MNN
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C"
JNIEXPORT jobject JNICALL
Java_cn_chenxuhang_creativeai_ai_mnn_NativeBackedMnnRuntime_nativeCpuAccelerationProbe(
        JNIEnv* env,
        jobject /* this */) {
    bool isArm64 = false;
    bool hasSme = false;
    bool hasSme2 = false;
    std::string detectionSource = "proc-cpuinfo";
    const std::string rawHints = detectCpuHintText();

#if defined(__aarch64__)
    isArm64 = true;
#if defined(HWCAP2_SME) || defined(HWCAP2_SME2)
    const unsigned long hwcap2 = getauxval(AT_HWCAP2);
    detectionSource = "getauxval";
#ifdef HWCAP2_SME
    hasSme = (hwcap2 & HWCAP2_SME) != 0;
#endif
#ifdef HWCAP2_SME2
    hasSme2 = (hwcap2 & HWCAP2_SME2) != 0;
#endif
#endif
#endif

    if (!hasSme || !hasSme2) {
        const std::string loweredHints = toLower(rawHints);
        if (!hasSme && loweredHints.find("sme") != std::string::npos) {
            hasSme = true;
        }
        if (!hasSme2 && loweredHints.find("sme2") != std::string::npos) {
            hasSme2 = true;
        }
    }

    return newCpuAccelerationProbeResult(
            env,
            isArm64,
            hasSme,
            hasSme2,
            detectionSource,
            rawHints);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_cn_chenxuhang_creativeai_ai_mnn_NativeBackedMnnRuntime_nativeProbeModelDirectory(
        JNIEnv* env,
        jobject /* this */,
        jstring modelDirectory) {
    const std::string directory = jstringToStdString(env, modelDirectory);
    const std::filesystem::path root(directory);
    const bool exists = std::filesystem::exists(root);
    const bool hasTokenizer = exists && (
            std::filesystem::exists(root / "tokenizer.txt") ||
            std::filesystem::exists(root / "tokenizer.json"));
    const bool hasGraph = exists && (
            std::filesystem::exists(root / "llm.mnn") ||
            std::filesystem::exists(root / "model.mnn"));
    const bool hasWeightData = !std::filesystem::exists(root / "llm.mnn") ||
            std::filesystem::exists(root / "llm.mnn.weight");
    const bool hasLlmConfig = exists && std::filesystem::exists(root / "llm_config.json");
    const bool requiresVisualAssets = hasLlmConfig && modelRequiresVisualAssets(root);
    const bool hasVisualGraph = !requiresVisualAssets || std::filesystem::exists(root / "visual.mnn");
    const bool hasVisualWeight = !requiresVisualAssets || std::filesystem::exists(root / "visual.mnn.weight");
    const bool hasRuntimeConfig = exists && std::filesystem::exists(root / "config.json");
    const bool hasWeights = hasGraph && hasWeightData && hasVisualGraph && hasVisualWeight;
    const bool hasConfig = hasRuntimeConfig;

    std::vector<std::string> missingFiles;
    if (!hasTokenizer) missingFiles.emplace_back("tokenizer.txt|tokenizer.json");
    if (!hasGraph) missingFiles.emplace_back("llm.mnn|model.mnn");
    if (!hasWeightData) missingFiles.emplace_back("llm.mnn.weight");
    if (!hasRuntimeConfig) missingFiles.emplace_back("config.json");
    if (!hasLlmConfig) missingFiles.emplace_back("llm_config.json");
    if (!hasVisualGraph) missingFiles.emplace_back("visual.mnn");
    if (!hasVisualWeight) missingFiles.emplace_back("visual.mnn.weight");

    return newModelProbeResult(
            env,
            directory,
            exists,
            hasTokenizer,
            hasWeights,
            hasConfig,
            missingFiles);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_cn_chenxuhang_creativeai_ai_mnn_NativeBackedMnnRuntime_nativeOpenSession(
        JNIEnv* env,
        jobject /* this */,
        jstring modelId,
        jstring modelDirectory,
        jstring backendName,
        jint threadCount,
        jboolean enableLowMemoryMode,
        jboolean enableMultimodalPath,
        jint cpuSmeCoreCount,
        jint cpuSme2NeonDivisionRatio) {
    const std::string modelIdValue = jstringToStdString(env, modelId);
    const std::string directoryValue = jstringToStdString(env, modelDirectory);
    const std::string backendNameValue = jstringToStdString(env, backendName);

    const std::filesystem::path root(directoryValue);
    const std::filesystem::path modelPath = std::filesystem::exists(root / "llm.mnn")
            ? (root / "llm.mnn")
            : (root / "model.mnn");
    std::string validationError;
    if (!validateModelDirectoryForSession(root, validationError)) {
        return newSessionOpenResult(
                env,
                false,
                runtimeVersionForCurrentBuild(),
                backendNameValue,
                "",
                validationError);
    }

#ifdef HAS_REAL_MNN_LLM
    std::lock_guard<std::mutex> lock(gCachedLlmSessionMutex);
    if (!ensureCachedSessionLoaded(
                modelIdValue,
                root,
                backendNameValue,
                static_cast<int>(threadCount),
                enableLowMemoryMode == JNI_TRUE,
                static_cast<int>(cpuSmeCoreCount),
                static_cast<int>(cpuSme2NeonDivisionRatio),
                validationError)) {
        return newSessionOpenResult(
                env,
                false,
                runtimeVersionForCurrentBuild(),
                backendNameValue,
                "",
                validationError);
    }
#elif defined(HAS_REAL_MNN)
    auto interpreter = MNN::Interpreter::createFromFile(modelPath.string().c_str());
    if (interpreter == nullptr) {
        return newSessionOpenResult(
                env,
                false,
                runtimeVersionForCurrentBuild(),
                backendNameValue,
                "",
                "MNN interpreter failed to open model graph");
    }
    delete interpreter;
#endif

    std::ostringstream sessionId;
    sessionId << modelIdValue << ":" << backendNameValue << ":t" << threadCount
              << ":lm=" << (enableLowMemoryMode ? "1" : "0")
              << ":mm=" << (enableMultimodalPath ? "1" : "0");

    return newSessionOpenResult(
            env,
            true,
            runtimeVersionForCurrentBuild(),
            backendLabelForCurrentBuild(),
            sessionId.str(),
            "");
}

extern "C"
JNIEXPORT jobject JNICALL
Java_cn_chenxuhang_creativeai_ai_mnn_NativeBackedMnnRuntime_nativeGenerateText(
        JNIEnv* env,
        jobject /* this */,
        jstring modelId,
        jstring modelDirectory,
        jstring backendName,
        jint threadCount,
        jboolean enableLowMemoryMode,
        jboolean enableMultimodalPath,
        jint cpuSmeCoreCount,
        jint cpuSme2NeonDivisionRatio,
        jstring prompt,
        jint maxNewTokens) {
    const std::string modelIdValue = jstringToStdString(env, modelId);
    const std::string directoryValue = jstringToStdString(env, modelDirectory);
    const std::string backendNameValue = jstringToStdString(env, backendName);
    const std::string promptValue = jstringToStdString(env, prompt);
    const std::filesystem::path root(directoryValue);

    std::string validationError;
    if (!validateModelDirectoryForSession(root, validationError)) {
        return newTextGenerationResult(
                env,
                false,
                runtimeVersionForCurrentBuild(),
                backendNameValue,
                "",
                validationError);
    }

#ifdef HAS_REAL_MNN_LLM
    std::lock_guard<std::mutex> lock(gCachedLlmSessionMutex);
    if (!ensureCachedSessionLoaded(
                modelIdValue,
                root,
                backendNameValue,
                static_cast<int>(threadCount),
                enableLowMemoryMode == JNI_TRUE,
                static_cast<int>(cpuSmeCoreCount),
                static_cast<int>(cpuSme2NeonDivisionRatio),
                validationError)) {
        return newTextGenerationResult(
                env,
                false,
                runtimeVersionForCurrentBuild(),
                backendNameValue,
                "",
                validationError);
    }
    auto* llm = gCachedLlmSession.llm.get();
    llm->reset();

    std::ostringstream output;
    llm->response(promptValue, &output, nullptr, static_cast<int>(maxNewTokens));
    const std::string generatedText = output.str();
    const auto* context = llm->getContext();
    const int statusCode = context == nullptr ? -999 : static_cast<int>(context->status);
    llm->reset();

    if (generatedText.empty()) {
        if (statusCode != static_cast<int>(MNN::Transformer::LlmStatus::NORMAL_FINISHED) &&
            statusCode != static_cast<int>(MNN::Transformer::LlmStatus::MAX_TOKENS_FINISHED)) {
            invalidateCachedSession();
        }
        std::ostringstream error;
        error << "MNN LLM generated empty output. status=" << statusCode;
        return newTextGenerationResult(
                env,
                false,
                runtimeVersionForCurrentBuild(),
                backendNameValue,
                "",
                error.str());
    }

    return newTextGenerationResult(
            env,
            true,
            runtimeVersionForCurrentBuild(),
            backendLabelForCurrentBuild(),
            generatedText,
            "");
#else
    (void) modelIdValue;
    (void) threadCount;
    (void) enableLowMemoryMode;
    (void) enableMultimodalPath;
    (void) promptValue;
    (void) maxNewTokens;
    return newTextGenerationResult(
            env,
            false,
            runtimeVersionForCurrentBuild(),
            backendLabelForCurrentBuild(),
            "",
            "Text generation requires MNN LLM headers and real LLM runtime support.");
#endif
}

extern "C"
JNIEXPORT jobject JNICALL
Java_cn_chenxuhang_creativeai_ai_mnn_NativeBackedMnnRuntime_nativeGenerateVisionText(
        JNIEnv* env,
        jobject /* this */,
        jstring modelId,
        jstring modelDirectory,
        jstring backendName,
        jint threadCount,
        jboolean enableLowMemoryMode,
        jboolean enableMultimodalPath,
        jint cpuSmeCoreCount,
        jint cpuSme2NeonDivisionRatio,
        jstring prompt,
        jbyteArray imageRgbBytes,
        jint width,
        jint height,
        jint maxNewTokens) {
    const std::string modelIdValue = jstringToStdString(env, modelId);
    const std::string directoryValue = jstringToStdString(env, modelDirectory);
    const std::string backendNameValue = jstringToStdString(env, backendName);
    const std::string promptValue = jstringToStdString(env, prompt);
    const std::filesystem::path root(directoryValue);

    std::string validationError;
    if (!validateModelDirectoryForSession(root, validationError)) {
        return newTextGenerationResult(
                env,
                false,
                runtimeVersionForCurrentBuild(),
                backendNameValue,
                "",
                validationError);
    }

#ifdef HAS_REAL_MNN_LLM
    if (enableMultimodalPath != JNI_TRUE) {
        return newTextGenerationResult(
                env,
                false,
                runtimeVersionForCurrentBuild(),
                backendNameValue,
                "",
                "Current model is not configured for multimodal vision generation.");
    }

    const std::vector<uint8_t> imageBytes = jbyteArrayToUint8Vector(env, imageRgbBytes);
    const int pixelCount = static_cast<int>(imageBytes.size());
    if (width <= 0 || height <= 0 || pixelCount != width * height * 3) {
        return newTextGenerationResult(
                env,
                false,
                runtimeVersionForCurrentBuild(),
                backendNameValue,
                "",
                "Vision input image buffer is invalid.");
    }

    std::lock_guard<std::mutex> lock(gCachedLlmSessionMutex);
    if (!ensureCachedSessionLoaded(
                modelIdValue,
                root,
                backendNameValue,
                static_cast<int>(threadCount),
                enableLowMemoryMode == JNI_TRUE,
                static_cast<int>(cpuSmeCoreCount),
                static_cast<int>(cpuSme2NeonDivisionRatio),
                validationError)) {
        return newTextGenerationResult(
                env,
                false,
                runtimeVersionForCurrentBuild(),
                backendNameValue,
                "",
                validationError);
    }
    auto* llm = gCachedLlmSession.llm.get();
    llm->reset();

    MNN::Transformer::MultimodalPrompt multimodalPrompt;
    multimodalPrompt.prompt_template = promptValue;

    MNN::Transformer::PromptImagePart imagePart;
    imagePart.width = static_cast<int>(width);
    imagePart.height = static_cast<int>(height);
    imagePart.image_data = MNN::Express::_Const(
            imageBytes.data(),
            {static_cast<int>(height), static_cast<int>(width), 3},
            MNN::Express::NHWC,
            halide_type_of<uint8_t>());
    multimodalPrompt.images["image_0"] = imagePart;

    std::ostringstream output;
    llm->response(multimodalPrompt, &output, nullptr, static_cast<int>(maxNewTokens));
    const std::string generatedText = output.str();
    const auto* context = llm->getContext();
    const int statusCode = context == nullptr ? -999 : static_cast<int>(context->status);
    llm->reset();

    if (generatedText.empty()) {
        if (statusCode != static_cast<int>(MNN::Transformer::LlmStatus::NORMAL_FINISHED) &&
            statusCode != static_cast<int>(MNN::Transformer::LlmStatus::MAX_TOKENS_FINISHED)) {
            invalidateCachedSession();
        }
        std::ostringstream error;
        error << "MNN VL generated empty output. status=" << statusCode;
        return newTextGenerationResult(
                env,
                false,
                runtimeVersionForCurrentBuild(),
                backendNameValue,
                "",
                error.str());
    }

    return newTextGenerationResult(
            env,
            true,
            runtimeVersionForCurrentBuild(),
            backendLabelForCurrentBuild(),
            generatedText,
            "");
#else
    (void) modelIdValue;
    (void) threadCount;
    (void) enableLowMemoryMode;
    (void) enableMultimodalPath;
    (void) promptValue;
    (void) maxNewTokens;
    (void) imageRgbBytes;
    (void) width;
    (void) height;
    return newTextGenerationResult(
            env,
            false,
            runtimeVersionForCurrentBuild(),
            backendLabelForCurrentBuild(),
            "",
            "Vision generation requires MNN multimodal runtime support.");
#endif
}
