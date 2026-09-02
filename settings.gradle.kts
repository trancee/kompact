// Kompact — multi-module Kotlin Multiplatform serialization framework.
// Spec: .scratch/kompact-spec/map.md (Tickets 01–13, all resolved).

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "kompact"

include(":kompact")
include(":kompact-ksp")
include(":kompact-example")
