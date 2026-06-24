plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

fun Project.booleanBuildConfigProperty(
    name: String,
    defaultValue: Boolean,
): Boolean {
    return when ((findProperty(name) as String?)?.trim()?.lowercase()) {
        "1", "true", "yes", "on" -> true
        "0", "false", "no", "off" -> false
        else -> defaultValue
    }
}

fun Project.stringBuildConfigProperty(
    name: String,
    defaultValue: String,
): String {
    return (findProperty(name) as String?)?.trim()?.takeIf { it.isNotEmpty() } ?: defaultValue
}

val bundleLargeModels = booleanBuildConfigProperty(
    name = "memomind.bundleLargeModels",
    defaultValue = false,
)
val bundleBundledAsr = booleanBuildConfigProperty(
    name = "memomind.bundleBundledAsr",
    defaultValue = true,
)
val supabaseUrl = stringBuildConfigProperty(
    name = "memomind.supabaseUrl",
    defaultValue = "",
)
val supabaseAnonKey = stringBuildConfigProperty(
    name = "memomind.supabaseAnonKey",
    defaultValue = "",
)
val supabaseAccessToken = stringBuildConfigProperty(
    name = "memomind.supabaseAccessToken",
    defaultValue = "",
)
val supabaseServiceRoleKey = stringBuildConfigProperty(
    name = "memomind.supabaseServiceRoleKey",
    defaultValue = "",
)
val preparedAssetsDir = layout.buildDirectory.dir("generated/memomind/assets/main")
val prepareBundledAssets = tasks.register<Sync>("prepareBundledAssets") {
    from("src/main/assets") {
        exclude("**/.cache/**")
        exclude("**/*.lock")
        exclude("**/*.metadata")
        exclude("**/README.md")
        exclude("**/LICENSE")
        exclude("**/export-onnx.py")
        if (!bundleLargeModels) {
            exclude("models/qwen-vl-2b-instruct-mnn/**")
        }
        if (!bundleBundledAsr) {
            exclude("asr/**")
        }
    }
    into(preparedAssetsDir)
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

        buildConfigField("boolean", "BUNDLE_QWEN_VL_MODEL", bundleLargeModels.toString())
        buildConfigField("boolean", "BUNDLE_SHERPA_ASR_MODEL", bundleBundledAsr.toString())
        buildConfigField("String", "SUPABASE_URL", "\"${supabaseUrl.replace("\"", "\\\"")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${supabaseAnonKey.replace("\"", "\\\"")}\"")
        buildConfigField("String", "SUPABASE_ACCESS_TOKEN", "\"${supabaseAccessToken.replace("\"", "\\\"")}\"")
        buildConfigField("String", "SUPABASE_SERVICE_ROLE_KEY", "\"${supabaseServiceRoleKey.replace("\"", "\\\"")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            assets.setSrcDirs(listOf(preparedAssetsDir.get().asFile.absolutePath))
        }
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
    implementation(projects.core.network)
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

tasks.named("preBuild") {
    dependsOn(prepareBundledAssets)
}
