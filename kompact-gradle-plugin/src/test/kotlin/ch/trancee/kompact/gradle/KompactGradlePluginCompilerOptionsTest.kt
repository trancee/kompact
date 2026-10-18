package ch.trancee.kompact.gradle

import org.gradle.api.GradleException
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KompactGradlePluginCompilerOptionsTest {
    @Test
    fun resolvesCompilerOptionProvidersAndUsesDefaultsForAbsentValues() {
        val project = ProjectBuilder.builder().build()
        val plugin = KompactGradlePlugin()
        val present =
            plugin.compilerOptionVersion(
                PropertyReceiver(project.providers.provider { "2.3" }),
                "value",
                "2.4",
                compatibility,
            ) as Provider<*>
        val absent =
            plugin.compilerOptionVersion(
                PropertyReceiver(project.providers.provider { null as String? }),
                "value",
                "2.4",
                compatibility,
            ) as Provider<*>

        assertEquals("2.3", present.get())
        assertEquals("2.4", absent.get())
    }

    @Test
    fun rejectsCompilerOptionsWithUnexpectedTypes() {
        val failure =
            assertFailsWith<GradleException> {
                KompactGradlePlugin().compilerOptionVersion(
                    PropertyReceiver(Any()),
                    "value",
                    "2.4",
                    compatibility,
                )
            }

        assertTrue(failure.message.orEmpty().contains("expected Kotlin compiler option 'value'"))
    }

    class PropertyReceiver(
        val value: Any?,
    )

    private companion object {
        const val compatibility = "Detected Kotlin Gradle Plugin 2.4.20 with KSP 2.3.12."
    }
}
