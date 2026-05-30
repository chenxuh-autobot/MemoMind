plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "cn.chenxuhang.creativeai.ai.orchestrator"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.ai.mnn)
    implementation(projects.ai.modelmanager)
    implementation(libs.json)
}
