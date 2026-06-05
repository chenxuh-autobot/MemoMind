plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

fun Project.stringBuildConfigProperty(name: String): String {
    return (findProperty(name) as String?) ?: ""
}

android {
    namespace = "cn.chenxuhang.creativeai"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        applicationId = "cn.chenxuhang.creativeai"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += "arm64-v8a"
        }

        buildConfigField("String", "QWEN_1_5B_TEXT_TOKENIZER_TXT_URL", "\"${project.stringBuildConfigProperty("model.qwen_local_1_5b_text.tokenizer_txt_url")}\"")
        buildConfigField("String", "QWEN_1_5B_TEXT_TOKENIZER_TXT_SHA256", "\"${project.stringBuildConfigProperty("model.qwen_local_1_5b_text.tokenizer_txt_sha256")}\"")
        buildConfigField("String", "QWEN_1_5B_TEXT_LLM_MNN_URL", "\"${project.stringBuildConfigProperty("model.qwen_local_1_5b_text.llm_mnn_url")}\"")
        buildConfigField("String", "QWEN_1_5B_TEXT_LLM_MNN_SHA256", "\"${project.stringBuildConfigProperty("model.qwen_local_1_5b_text.llm_mnn_sha256")}\"")
        buildConfigField("String", "QWEN_1_5B_TEXT_LLM_WEIGHT_URL", "\"${project.stringBuildConfigProperty("model.qwen_local_1_5b_text.llm_weight_url")}\"")
        buildConfigField("String", "QWEN_1_5B_TEXT_LLM_WEIGHT_SHA256", "\"${project.stringBuildConfigProperty("model.qwen_local_1_5b_text.llm_weight_sha256")}\"")
        buildConfigField("String", "QWEN_1_5B_TEXT_LLM_CONFIG_URL", "\"${project.stringBuildConfigProperty("model.qwen_local_1_5b_text.llm_config_url")}\"")
        buildConfigField("String", "QWEN_1_5B_TEXT_LLM_CONFIG_SHA256", "\"${project.stringBuildConfigProperty("model.qwen_local_1_5b_text.llm_config_sha256")}\"")
        buildConfigField("String", "QWEN_1_5B_TEXT_CONFIG_URL", "\"${project.stringBuildConfigProperty("model.qwen_local_1_5b_text.config_url")}\"")
        buildConfigField("String", "QWEN_1_5B_TEXT_CONFIG_SHA256", "\"${project.stringBuildConfigProperty("model.qwen_local_1_5b_text.config_sha256")}\"")
        buildConfigField("String", "QWEN_1_5B_TEXT_VISUAL_MNN_URL", "\"${project.stringBuildConfigProperty("model.qwen_local_1_5b_text.visual_mnn_url")}\"")
        buildConfigField("String", "QWEN_1_5B_TEXT_VISUAL_MNN_SHA256", "\"${project.stringBuildConfigProperty("model.qwen_local_1_5b_text.visual_mnn_sha256")}\"")
        buildConfigField("String", "QWEN_1_5B_TEXT_VISUAL_WEIGHT_URL", "\"${project.stringBuildConfigProperty("model.qwen_local_1_5b_text.visual_weight_url")}\"")
        buildConfigField("String", "QWEN_1_5B_TEXT_VISUAL_WEIGHT_SHA256", "\"${project.stringBuildConfigProperty("model.qwen_local_1_5b_text.visual_weight_sha256")}\"")

        buildConfigField("String", "QWEN_3B_MM_TOKENIZER_URL", "\"${project.stringBuildConfigProperty("model.qwen_local_3b_multimodal.tokenizer_url")}\"")
        buildConfigField("String", "QWEN_3B_MM_TOKENIZER_SHA256", "\"${project.stringBuildConfigProperty("model.qwen_local_3b_multimodal.tokenizer_sha256")}\"")
        buildConfigField("String", "QWEN_3B_MM_MODEL_URL", "\"${project.stringBuildConfigProperty("model.qwen_local_3b_multimodal.model_url")}\"")
        buildConfigField("String", "QWEN_3B_MM_MODEL_SHA256", "\"${project.stringBuildConfigProperty("model.qwen_local_3b_multimodal.model_sha256")}\"")
        buildConfigField("String", "QWEN_3B_MM_CONFIG_URL", "\"${project.stringBuildConfigProperty("model.qwen_local_3b_multimodal.config_url")}\"")
        buildConfigField("String", "QWEN_3B_MM_CONFIG_SHA256", "\"${project.stringBuildConfigProperty("model.qwen_local_3b_multimodal.config_sha256")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += listOf("mnn", "weight", "txt", "json", "onnx", "wav")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.filesystem)
    implementation(projects.ai.orchestrator)
    implementation(projects.ai.modelmanager)
    implementation(projects.ai.mnn)
    implementation(projects.feature.home)
    implementation(projects.feature.capture)
    implementation(projects.feature.result)
    implementation(projects.feature.history)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.image.labeling)
    implementation(files("libs/sherpa-onnx-1.13.2.aar"))
    implementation("com.github.yalantis:ucrop:2.2.10")
    debugImplementation(libs.androidx.compose.ui.tooling)
}
