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

rootProject.name = "kompact"

include(":kompact")
include(":kompact-gradle-plugin")
include(":kompact-ksp")
include(":kompact-ksp-integration")

includeBuild("build-logic")
