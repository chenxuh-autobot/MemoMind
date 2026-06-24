plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(projects.core.model)
    implementation(libs.json)
}

kotlin {
    jvmToolchain(21)
}
