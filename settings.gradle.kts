pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "knx-field-kit"

include(":core-model")
include(":core-knxproj")
include(":core-bus")
include(":core-controller")
include(":app-desktop")
include(":app-android")
