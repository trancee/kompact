pluginManagement {
    repositories {
        maven { url = uri("../build/verification-repository") }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven { url = uri("../build/verification-repository") }
        google()
        mavenCentral()
    }
}

rootProject.name = "kompact-publication-consumer"
