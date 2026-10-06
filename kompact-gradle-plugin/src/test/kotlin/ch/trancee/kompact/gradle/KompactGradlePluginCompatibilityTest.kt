package ch.trancee.kompact.gradle

import ch.trancee.kompact.gradle.fixture.KotlinPropertyFixtures
import org.gradle.api.GradleException
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KompactGradlePluginCompatibilityTest {
    @Test
    fun mapsSupportedKotlinTargetsToGeneratedSourceSets() {
        assertNull(actualOutputName(Target("common", "common")))
        assertEquals("jvm", actualOutputName(Target("jvm", "jvm")))
        assertEquals("jvm", actualOutputName(Target("android", "androidJvm")))
        assertEquals("ios", actualOutputName(Target("iosArm64", "native")))
        assertEquals("ios", actualOutputName(Target("iosSimulatorArm64", "native")))
        assertEquals("androidArm64", actualOutputName(Target("androidNativeArm64", "native")))
    }

    @Test
    fun rejectsUnsupportedNativeTargets() {
        val failure = invocationFailure<GradleException> { actualOutputName(Target("linuxX64", "native")) }

        assertTrue(failure.message.orEmpty().contains("does not support Kotlin/Native target 'linuxX64'"))
    }

    @Test
    fun rejectsUnsupportedKotlinPlatforms() {
        val failure = invocationFailure<GradleException> { actualOutputName(Target("js", "js")) }

        assertTrue(failure.message.orEmpty().contains("does not support Kotlin target 'js'"))
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
    fun resolvesCompilerOptionProvidersAndUsesDefaultsForAbsentValues() {
        val project = ProjectBuilder.builder().build()
        val present =
            KompactGradlePlugin().compilerOptionVersion(
                PropertyReceiver(project.providers.provider { "2.3" }),
                "value",
                "2.4",
                compatibility,
            ) as Provider<*>
        val absent =
            KompactGradlePlugin().compilerOptionVersion(
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
            invocationFailure<GradleException> {
                KompactGradlePlugin().compilerOptionVersion(
                    PropertyReceiver(Any()),
                    "value",
                    "2.4",
                    compatibility,
                )
            }

        assertTrue(failure.message.orEmpty().contains("expected Kotlin compiler option 'value'"))
    }

    @Test
    fun addsExpectActualCompilerArgumentToKotlinCompilerOptions() {
        val project = ProjectBuilder.builder().build()
        val arguments = project.objects.listProperty(String::class.java)

        KompactGradlePlugin().addExpectActualCompilerArgument(
            PropertyReceiver(arguments),
            compatibility,
        )

        assertEquals(listOf("-Xexpect-actual-classes"), arguments.get())
    }

    @Test
    fun rejectsCompilerOptionsWithoutAListProperty() {
        val failure =
            invocationFailure<GradleException> {
                KompactGradlePlugin().addExpectActualCompilerArgument(
                    PropertyReceiver(Any()),
                    compatibility,
                )
            }

        assertTrue(failure.message.orEmpty().contains("expected Kotlin compiler freeCompilerArgs"))
    }

    private fun actualOutputName(target: Target): String? =
        KompactGradlePlugin().actualOutputName(target, compatibility)

    private inline fun <reified T : Throwable> invocationFailure(action: () -> Any?): T =
        assertFailsWith<T> { action() }

    data class Target(
        val name: String,
        val platformType: String,
    )

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

    private companion object {
        const val compatibility = "Detected Kotlin Gradle Plugin 2.4.20 with KSP 2.3.12."
    }
}
