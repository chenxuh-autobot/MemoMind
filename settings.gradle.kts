pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "CreativeAiAndroid"

include(":app")
include(":core:model")
include(":core:database")
include(":core:filesystem")
include(":core:network")
include(":feature:home")
include(":feature:capture")
include(":feature:result")
include(":feature:history")
include(":ai:orchestrator")
include(":ai:modelmanager")
include(":ai:mnn")
