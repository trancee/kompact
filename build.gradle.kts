plugins {
    alias(libs.plugins.kmp) apply false
    alias(libs.plugins.bcv) apply false
    alias(libs.plugins.dokka) apply false
}

allprojects {
    group = "ch.trancee.kompact"
    version = "0.1.0-SNAPSHOT"
}
