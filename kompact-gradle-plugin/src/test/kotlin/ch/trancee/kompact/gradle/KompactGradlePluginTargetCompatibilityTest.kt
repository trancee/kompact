package ch.trancee.kompact.gradle

import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KompactGradlePluginTargetCompatibilityTest {
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
        val failure = assertFailsWith<GradleException> { actualOutputName(Target("linuxX64", "native")) }

        assertTrue(failure.message.orEmpty().contains("does not support Kotlin/Native target 'linuxX64'"))
    }

    @Test
    fun rejectsUnsupportedKotlinPlatforms() {
        val failure = assertFailsWith<GradleException> { actualOutputName(Target("js", "js")) }

        assertTrue(failure.message.orEmpty().contains("does not support Kotlin target 'js'"))
    }

    @Test
    fun leavesCommonTargetsWithoutPlatformSources() {
        val project = ProjectBuilder.builder().build()
        val generateSources =
            project.tasks.register("generateKompactSources", GenerateKompactSources::class.java)

        KompactGradlePlugin().configureTargetSources(
            Target("common", "common"),
            compatibility,
            generateSources,
        )
    }

    @Test
    fun ignoresPlatformTargetsWithoutMainCompilation() {
        val project = ProjectBuilder.builder().build()
        val compilations =
            project.objects.domainObjectContainer(
                NamedItem::class.java,
                org.gradle.api.NamedDomainObjectFactory { name -> NamedItem(name) },
            )
        val generateSources =
            project.tasks.register("generateKompactSources", GenerateKompactSources::class.java)

        KompactGradlePlugin().configureTargetSources(
            TargetWithoutMainCompilation("jvm", "jvm", compilations),
            compatibility,
            generateSources,
        )

        assertTrue(compilations.isEmpty())
    }

    private fun actualOutputName(target: Target): String? =
        KompactGradlePlugin().actualOutputName(target, compatibility)

    data class Target(
        val name: String,
        val platformType: String,
    )

    data class TargetWithoutMainCompilation(
        val name: String,
        val platformType: String,
        val compilations: NamedDomainObjectCollection<NamedItem>,
    )

    data class NamedItem(
        private val itemName: String,
    ) : Named {
        override fun getName(): String = itemName
    }

    private companion object {
        const val compatibility = "Detected Kotlin Gradle Plugin 2.4.20 with KSP 2.3.12."
    }
}
