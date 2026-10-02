pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

includeBuild("@KOMPACT_ROOT@") {
    dependencySubstitution {
        substitute(module("ch.trancee.kompact:kompact")).using(project(":kompact"))
        substitute(module("ch.trancee.kompact:kompact-ksp")).using(project(":kompact-ksp"))
    }
}
rootProject.name = "kompact-kmp-consumer"
