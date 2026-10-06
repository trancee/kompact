package ch.trancee.kompact.gradle

import ch.trancee.kompact.gradle.fixture.KotlinPropertyFixtures
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KompactGradlePluginDiagnosticsTest {
    @Test
    fun reportsMissingKotlinPluginVersionAndRejectsUnsupportedVersions() {
        val plugin = KompactGradlePlugin()

        assertEquals("<unknown>", plugin.detectedKotlinVersion(null))
        assertEquals("<unknown>", plugin.detectedKotlinVersion(Any()))

        plugin.requireSupportedKotlinVersion("2.4.20", "2.4.20", compatibility)
        val failure =
            assertFailsWith<GradleException> {
                plugin.requireSupportedKotlinVersion("2.4.10", "2.4.20", compatibility)
            }

        assertTrue(failure.message.orEmpty().contains("incompatible"))
        assertTrue(failure.message.orEmpty().contains(compatibility))
    }

    @Test
    fun detectsImplementationVersionFromPluginClassPackage() {
        val plugin = loadPluginClass("ch.trancee.kompact.gradle.VersionedKotlinPlugin", "2.4.20")

        assertEquals("2.4.20", KompactGradlePlugin().detectedKotlinVersion(plugin))
    }

    @Test
    fun handlesPluginClassesFromUnnamedPackages() {
        val plugin = loadPluginClass("UnpackagedKotlinPlugin", null)

        assertEquals("", plugin.javaClass.`package`.name)
        assertEquals("<unknown>", KompactGradlePlugin().detectedKotlinVersion(plugin))
    }

    @Test
    fun reportsMissingKotlinExtension() {
        val failure =
            assertFailsWith<GradleException> {
                KompactGradlePlugin().requireKotlinExtension(null, compatibility)
            }

        assertTrue(failure.message.orEmpty().contains("did not create its 'kotlin' extension"))
        assertTrue(failure.message.orEmpty().contains(compatibility))
    }

    @Test
    fun rejectsKotlinSourceSetPropertiesWithUnexpectedTypes() {
        val failure =
            invocationFailure<GradleException> {
                KompactGradlePlugin().kotlinSources(
                    PropertyReceiver(Any()),
                    compatibility,
                )
            }

        assertTrue(failure.message.orEmpty().contains("expected a Gradle SourceDirectorySet"))
    }

    @Test
    fun rejectsNamedObjectPropertiesWithUnexpectedTypes() {
        val failure =
            invocationFailure<GradleException> {
                KompactGradlePlugin().namedObjects(
                    PropertyReceiver(Any()),
                    "sourceSets",
                    compatibility,
                )
            }

        assertTrue(failure.message.orEmpty().contains("expected 'sourceSets'"))
    }

    @Test
    fun reportsMissingCommonMainSourceSet() {
        val project = ProjectBuilder.builder().build()
        val sourceSets =
            project.objects.domainObjectContainer(
                NamedItem::class.java,
                org.gradle.api.NamedDomainObjectFactory { name ->
                    NamedItem(name)
                },
            )
        val failure =
            assertFailsWith<GradleException> {
                KompactGradlePlugin().requireCommonMain(sourceSets, compatibility)
            }

        assertTrue(failure.message.orEmpty().contains("did not create the 'commonMain' source set"))
        assertTrue(failure.message.orEmpty().contains(compatibility))
    }

    @Test
    fun reportsNullAndThrowingKotlinPropertyGetters() {
        val nullFailure =
            invocationFailure<GradleException> {
                KompactGradlePlugin().readKotlinProperty(
                    PropertyReceiver(null),
                    "value",
                    compatibility,
                )
            }
        assertTrue(nullFailure.message.orEmpty().contains("property 'value' was null"))

        val getterFailure =
            invocationFailure<GradleException> {
                KompactGradlePlugin().readKotlinProperty(
                    ThrowingPropertyReceiver(),
                    "value",
                    compatibility,
                )
            }
        assertTrue(getterFailure.message.orEmpty().contains("Could not read Kotlin Gradle property 'value'"))
        assertIs<IllegalStateException>(getterFailure.cause)
    }

    @Test
    fun reportsInaccessibleKotlinPropertyGetters() {
        val failure =
            invocationFailure<GradleException> {
                KompactGradlePlugin().readKotlinProperty(
                    KotlinPropertyFixtures.hiddenValueReceiver(),
                    "value",
                    compatibility,
                )
            }

        assertTrue(failure.message.orEmpty().contains("Could not read Kotlin Gradle property 'value'"))
        assertIs<IllegalAccessException>(failure.cause)
    }

    @Test
    fun rejectsKotlinPropertyGetterWithParameters() {
        val failure =
            invocationFailure<GradleException> {
                KompactGradlePlugin().readKotlinProperty(
                    ParameterizedPropertyReceiver(),
                    "value",
                    compatibility,
                )
            }

        assertTrue(failure.message.orEmpty().contains("missing getValue()"))
    }

    @Test
    fun reportsGetterNamesForUppercaseExpandingPropertyNames() {
        val failure =
            invocationFailure<GradleException> {
                KompactGradlePlugin().readKotlinProperty(
                    PropertyReceiver(Any()),
                    "ß",
                    compatibility,
                )
            }

        assertTrue(failure.message.orEmpty().contains("missing getSS()"))
    }

    @Test
    fun reportsGetterNamesForEmptyPropertyNames() {
        val failure =
            invocationFailure<GradleException> {
                KompactGradlePlugin().readKotlinProperty(
                    PropertyReceiver(Any()),
                    "",
                    compatibility,
                )
            }

        assertTrue(failure.message.orEmpty().contains("missing get()"))
    }

    private fun loadPluginClass(
        className: String,
        implementationVersion: String?,
    ): Any {
        val jarFile = Files.createTempFile("kompact-versioned-plugin", ".jar")
        try {
            val manifest =
                Manifest().apply {
                    mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                    implementationVersion?.let {
                        mainAttributes[Attributes.Name.IMPLEMENTATION_VERSION] = it
                    }
                }
            JarOutputStream(Files.newOutputStream(jarFile), manifest).use { jar ->
                val classPath = className.replace('.', '/') + ".class"
                jar.putNextEntry(JarEntry(classPath))
                requireNotNull(javaClass.getResourceAsStream("/$classPath")).use { classFile ->
                    classFile.copyTo(jar)
                }
                jar.closeEntry()
            }
            URLClassLoader(arrayOf(jarFile.toUri().toURL()), null).use { classLoader ->
                return classLoader.loadClass(className).getDeclaredConstructor().newInstance()
            }
        } finally {
            Files.deleteIfExists(jarFile)
        }
    }

    private inline fun <reified T : Throwable> invocationFailure(action: () -> Any?): T =
        assertFailsWith<T> { action() }

    class PropertyReceiver(
        val value: Any?,
    ) {
        fun getKotlin(): Any? = value

        fun getSourceSets(): Any? = value

        fun getFreeCompilerArgs(): Any? = value
    }

    class ThrowingPropertyReceiver {
        fun getValue(): Any = error("getter failed")
    }

    class ParameterizedPropertyReceiver {
        fun getValue(argument: Any): Any = argument
    }

    data class NamedItem(
        private val itemName: String,
    ) : Named {
        override fun getName(): String = itemName
    }

    private companion object {
        const val compatibility = "Detected Kotlin Gradle Plugin 2.4.20 with KSP 2.3.12."
    }
}
