package ch.trancee.kompact.gradle

import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KompactPluginVersionsTest {
    @Test
    fun reportsMissingPluginMetadataResource() {
        URLClassLoader(emptyArray(), null).use { classLoader ->
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    KompactPluginVersions.load(classLoader)
                }

            assertTrue(failure.message.orEmpty().contains("version metadata is missing"))
        }
    }

    @Test
    fun requiresEveryPublishedVersionProperty() {
        val properties =
            mapOf(
                "pluginVersion" to "0.8.0",
                "kspVersion" to "2.3.12",
                "kspCoroutinesVersion" to "1.10.2",
                "kotlinVersion" to "2.4.20",
            )
        for (missingProperty in properties.keys) {
            val directory = Files.createTempDirectory("kompact-plugin-versions")
            try {
                val contents =
                    properties
                        .filterKeys { it != missingProperty }
                        .entries
                        .joinToString("\n") { (name, version) -> "$name=$version" }
                Files.writeString(directory.resolve("kompact-plugin.properties"), contents)

                URLClassLoader(arrayOf(directory.toUri().toURL()), null).use { classLoader ->
                    assertFailsWith<IllegalArgumentException>(missingProperty) {
                        KompactPluginVersions.load(classLoader)
                    }
                }
            } finally {
                directory.toFile().deleteRecursively()
            }
        }
    }
}
