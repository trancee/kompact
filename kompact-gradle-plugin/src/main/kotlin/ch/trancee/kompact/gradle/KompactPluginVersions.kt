package ch.trancee.kompact.gradle

import java.util.Properties

internal data class KompactPluginVersions(
    val plugin: String,
    val ksp: String,
    val coroutines: String,
    val kotlin: String,
) {
    companion object {
        fun load(classLoader: ClassLoader): KompactPluginVersions {
            val properties =
                Properties().apply {
                    requireNotNull(classLoader.getResourceAsStream("kompact-plugin.properties")) {
                        "Kompact Gradle plugin version metadata is missing."
                    }.use(::load)
                }
            return KompactPluginVersions(
                plugin = requireNotNull(properties.getProperty("pluginVersion")),
                ksp = requireNotNull(properties.getProperty("kspVersion")),
                coroutines = requireNotNull(properties.getProperty("kspCoroutinesVersion")),
                kotlin = requireNotNull(properties.getProperty("kotlinVersion")),
            )
        }
    }
}
