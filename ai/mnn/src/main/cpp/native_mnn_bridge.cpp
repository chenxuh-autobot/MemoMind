#include <jni.h>
#include <filesystem>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

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

std::string jstringToStdString(JNIEnv* env, jstring value) {
    const char* utfChars = env->GetStringUTFChars(value, nullptr);
    std::string result = utfChars == nullptr ? "" : utfChars;
    if (utfChars != nullptr) {
        env->ReleaseStringUTFChars(value, utfChars);
    }
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

std::string buildRuntimeConfigJson(
        const std::filesystem::path& modelDirectory,
        const std::string& backendName,
        int threadCount,
        bool enableLowMemoryMode) {
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
    config << "\"tmp_path\":\"" << mmapDirectory.string() << "\"";
    config << "}";
    return config.str();
}

bool validateModelDirectoryForSession(
        const std::filesystem::path& root,
        std::string& errorMessage) {
    const bool hasTokenizer = std::filesystem::exists(root / "tokenizer.txt")
            || std::filesystem::exists(root / "tokenizer.json");
    const bool hasRuntimeConfig = std::filesystem::exists(root / "config.json");
    const bool hasGraph = std::filesystem::exists(root / "llm.mnn")
            || std::filesystem::exists(root / "model.mnn");
    const bool hasWeightData = !std::filesystem::exists(root / "llm.mnn")
            || std::filesystem::exists(root / "llm.mnn.weight");

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
    return true;
}

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
    const bool hasRuntimeConfig = exists && std::filesystem::exists(root / "config.json");
    const bool hasWeights = hasGraph && hasWeightData;
    const bool hasConfig = hasRuntimeConfig;

    std::vector<std::string> missingFiles;
    if (!hasTokenizer) missingFiles.emplace_back("tokenizer.txt|tokenizer.json");
    if (!hasGraph) missingFiles.emplace_back("llm.mnn|model.mnn");
    if (!hasWeightData) missingFiles.emplace_back("llm.mnn.weight");
    if (!hasRuntimeConfig) missingFiles.emplace_back("config.json");
    if (exists && !std::filesystem::exists(root / "llm_config.json")) missingFiles.emplace_back("llm_config.json");

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
        jboolean enableMultimodalPath) {
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
    const std::filesystem::path runtimeConfigPath = root / "config.json";
    auto* llm = MNN::Transformer::Llm::createLLM(runtimeConfigPath.string());
    if (llm == nullptr) {
        return newSessionOpenResult(
                env,
                false,
                runtimeVersionForCurrentBuild(),
                backendNameValue,
                "",
                "MNN LLM runtime failed to create session from config.json");
    }

    const std::string runtimeConfig = buildRuntimeConfigJson(
            root,
            backendNameValue,
            static_cast<int>(threadCount),
            enableLowMemoryMode == JNI_TRUE);
    llm->set_config(runtimeConfig);
    const bool loaded = llm->load();
    const auto* context = llm->getContext();
    const int statusCode = context == nullptr ? -999 : static_cast<int>(context->status);
    MNN::Transformer::Llm::destroy(llm);

    if (!loaded) {
        std::ostringstream error;
        error << "MNN LLM load() failed. backend=" << backendNameValue
              << ", threadCount=" << threadCount
              << ", lowMemory=" << (enableLowMemoryMode == JNI_TRUE ? "true" : "false")
              << ", status=" << statusCode;
        return newSessionOpenResult(
                env,
                false,
                runtimeVersionForCurrentBuild(),
                backendNameValue,
                "",
                error.str());
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
    const std::filesystem::path runtimeConfigPath = root / "config.json";
    auto* llm = MNN::Transformer::Llm::createLLM(runtimeConfigPath.string());
    if (llm == nullptr) {
        return newTextGenerationResult(
                env,
                false,
                runtimeVersionForCurrentBuild(),
                backendNameValue,
                "",
                "MNN LLM runtime failed to create generation session from config.json");
    }

    const std::string runtimeConfig = buildRuntimeConfigJson(
            root,
            backendNameValue,
            static_cast<int>(threadCount),
            enableLowMemoryMode == JNI_TRUE);
    llm->set_config(runtimeConfig);
    const bool loaded = llm->load();
    if (!loaded) {
        const auto* context = llm->getContext();
        const int statusCode = context == nullptr ? -999 : static_cast<int>(context->status);
        MNN::Transformer::Llm::destroy(llm);
        std::ostringstream error;
        error << "MNN LLM load() failed before generation. modelId=" << modelIdValue
              << ", status=" << statusCode;
        return newTextGenerationResult(
                env,
                false,
                runtimeVersionForCurrentBuild(),
                backendNameValue,
                "",
                error.str());
    }

    std::ostringstream output;
    llm->response(promptValue, &output, nullptr, static_cast<int>(maxNewTokens));
    const std::string generatedText = output.str();
    const auto* context = llm->getContext();
    const int statusCode = context == nullptr ? -999 : static_cast<int>(context->status);
    MNN::Transformer::Llm::destroy(llm);

    if (generatedText.empty()) {
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
