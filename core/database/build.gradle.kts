plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "cn.chenxuhang.creativeai.core.database"
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
    implementation(libs.json)
}
