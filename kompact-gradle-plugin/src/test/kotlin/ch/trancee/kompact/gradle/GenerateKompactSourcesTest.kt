package ch.trancee.kompact.gradle

import com.google.devtools.ksp.impl.KSPLoader
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GenerateKompactSourcesTest {
    @Test
    fun reportsWhenKspProcessorProviderIsMissing() {
        val projectDirectory = Files.createTempDirectory("kompact-missing-ksp-provider")
        try {
            val task = configuredTask(projectDirectory, includeProcessor = false)

            val failure = assertFailsWith<GradleException> { task.generate() }

            assertTrue(failure.message.orEmpty().contains("No KSP processor providers"))
        } finally {
            projectDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun reportsWhenKsp2LoaderIsMissing() {
        val projectDirectory = Files.createTempDirectory("kompact-missing-ksp-loader")
        try {
            val task = configuredTask(projectDirectory, includeEngineLoader = false)

            val failure = assertFailsWith<GradleException> { task.generate() }

            assertTrue(failure.message.orEmpty().contains("KSPLoader.loadAndRunKSP"))
        } finally {
            projectDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun reportsKspProcessorFailuresWithTheTestedVersionPair() {
        val projectDirectory = Files.createTempDirectory("kompact-ksp-processor-failure")
        try {
            val task = configuredTask(projectDirectory, useTestLoader = true)

            val failure = withLoaderBehavior("throw") {
                assertFailsWith<GradleException> { task.generate() }
            }

            assertTrue(failure.message.orEmpty().contains("KSP2 failed"))
            assertTrue(failure.message.orEmpty().contains("Kotlin Gradle Plugin"))
            assertTrue(failure.cause is IllegalStateException)
        } finally {
            projectDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun reportsIncompatibleKsp2ReturnTypes() {
        val projectDirectory = Files.createTempDirectory("kompact-ksp-return-type-failure")
        try {
            val task = configuredTask(projectDirectory, useTestLoader = true)

            val failure = withLoaderBehavior("wrong-type") {
                assertFailsWith<GradleException> { task.generate() }
            }

            assertTrue(failure.message.orEmpty().contains("returned an incompatible type"))
        } finally {
            projectDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun reportsNonzeroKsp2ExitCodes() {
        val projectDirectory = Files.createTempDirectory("kompact-ksp-exit-code-failure")
        try {
            val task = configuredTask(projectDirectory, useTestLoader = true)

            val failure = withLoaderBehavior("failure") {
                assertFailsWith<GradleException> { task.generate() }
            }

            assertTrue(failure.message.orEmpty().contains("exit code 1"))
        } finally {
            projectDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun reportsWhenTheWorkDirectoryCannotBeCreated() {
        val projectDirectory = Files.createTempDirectory("kompact-work-directory-failure")
        try {
            val blockingFile = Files.writeString(projectDirectory.resolve("not-a-directory"), "file")
            val task = createUnconfiguredGenerateKompactSourcesTask(projectDirectory)
            task.outputDirectory.set(projectDirectory.resolve("generated").toFile())
            task.workDirectory.set(blockingFile.resolve("work").toFile())

            val failure = assertFailsWith<GradleException> { task.generate() }

            assertTrue(failure.message.orEmpty().contains("Could not create the Kompact codegen directory"))
        } finally {
            projectDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun reportsWhenGeneratedOutputCannotBeCleared() {
        val projectDirectory = Files.createTempDirectory("kompact-output-directory-failure")
        val outputDirectory = Files.createDirectories(projectDirectory.resolve("generated"))
        Files.writeString(outputDirectory.resolve("existing.kt"), "content")
        try {
            Files.setPosixFilePermissions(
                outputDirectory,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE),
            )
            val task = createUnconfiguredGenerateKompactSourcesTask(projectDirectory)
            task.outputDirectory.set(outputDirectory.toFile())
            task.workDirectory.set(projectDirectory.resolve("work").toFile())

            val failure = assertFailsWith<GradleException> { task.generate() }

            assertTrue(failure.message.orEmpty().contains("Could not clear the Kompact codegen directory"))
        } finally {
            Files.setPosixFilePermissions(
                outputDirectory,
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                ),
            )
            projectDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun generatesSourcesIntoCommonJvmIosAndAndroidNativeDirectories() {
        val projectDirectory = Files.createTempDirectory("kompact-generation-task")
        try {
            val task = configuredTask(projectDirectory)

            task.generate()
            task.generate()

            assertGeneratedSource(projectDirectory, "common", "PacketSchemaViewGen.kt")
            assertGeneratedSource(projectDirectory, "common", "ScalarSchemaGenEncoder.kt")
            assertGeneratedSource(projectDirectory, "jvm", "PacketSchemaViewGenJvm.kt")
            assertGeneratedSource(projectDirectory, "jvm", "ScalarSchemaGenJvm.kt")
            assertGeneratedSource(projectDirectory, "ios", "PacketSchemaViewGenIos.kt")
            assertGeneratedSource(projectDirectory, "ios", "ScalarSchemaGenIos.kt")
            assertGeneratedSource(projectDirectory, "androidArm64", "PacketSchemaViewGenAndroidArm64.kt")
            assertGeneratedSource(projectDirectory, "androidArm64", "ScalarSchemaGenAndroidArm64.kt")
        } finally {
            projectDirectory.toFile().deleteRecursively()
        }
    }

    private fun fixtureDirectory(resourcePath: String): File =
        Path.of(requireNotNull(javaClass.getResource("/$resourcePath")).toURI()).toFile()

    private fun classpathEntry(className: String): File =
        File(Class.forName(className).protectionDomain.codeSource.location.toURI())

    private fun configuredTask(
        projectDirectory: Path,
        includeProcessor: Boolean = true,
        includeEngineLoader: Boolean = true,
        useTestLoader: Boolean = false,
    ): GenerateKompactSources {
        val project =
            ProjectBuilder
                .builder()
                .withProjectDir(projectDirectory.toFile())
                .build()
        project.repositories.mavenCentral()
        val versions = KompactPluginVersions.load(javaClass.classLoader)
        val dependencies =
            buildList {
                add("com.google.devtools.ksp:symbol-processing-api:${versions.ksp}")
                add("com.google.devtools.ksp:symbol-processing-common-deps:${versions.ksp}")
                if (includeEngineLoader) {
                    add("com.google.devtools.ksp:symbol-processing-aa-embeddable:${versions.ksp}")
                }
                add("org.jetbrains.kotlin:kotlin-stdlib:${versions.kotlin}")
                add("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:${versions.coroutines}")
            }
        val engineClasspath =
            project.configurations
                .detachedConfiguration(*dependencies.map(project.dependencies::create).toTypedArray())
                .apply { isTransitive = false }
                .files
        val processorClasspath =
            if (includeProcessor) {
                listOf(
                    classpathEntry("ch.trancee.kompact.ksp.KompactSymbolProcessorProvider"),
                    classpathEntry("com.squareup.kotlinpoet.FileSpec"),
                )
            } else {
                emptyList()
            }
        val kspClasspath =
            buildList {
                if (useTestLoader) add(classpathEntry(KSPLoader::class.java.name))
                addAll(engineClasspath)
            }
        val task = project.tasks.register("generateKompactSources", GenerateKompactSources::class.java).get()
        task.moduleName.set("coverage-test")
        task.kotlinPluginVersion.set(versions.kotlin)
        task.kspVersion.set(versions.ksp)
        task.projectDirectory.set(projectDirectory.toFile())
        task.outputDirectory.set(projectDirectory.resolve("generated").toFile())
        task.workDirectory.set(projectDirectory.resolve("work").toFile())
        task.kspClasspath.from(kspClasspath)
        task.processorClasspath.from(processorClasspath)
        task.commonClasspath.from(engineClasspath)
        task.commonSourceRoots.from(
            fixtureDirectory("kmp-consumer/src/commonMain/kotlin"),
            Path
                .of(requireNotNull(System.getProperty("kompact.repository.root")))
                .resolve("kompact/src/commonMain/kotlin/ch/trancee/kompact/annotations")
                .toFile(),
        )
        task.processorOptions.put("kompact.generate", "kmp")
        task.languageVersion.set("2.4")
        task.apiVersion.set("2.4")
        task.allWarningsAsErrors.set(false)
        task.logLevel.set(0)
        return task
    }

    private inline fun <T> withLoaderBehavior(
        behavior: String,
        action: () -> T,
    ): T {
        val propertyName = "kompact.test.ksp.loader.behavior"
        val previousValue = System.getProperty(propertyName)
        System.setProperty(propertyName, behavior)
        return try {
            action()
        } finally {
            if (previousValue == null) {
                System.clearProperty(propertyName)
            } else {
                System.setProperty(propertyName, previousValue)
            }
        }
    }

    private fun assertGeneratedSource(
        projectDirectory: Path,
        sourceSet: String,
        fileName: String,
    ) {
        val generated = projectDirectory.resolve("generated/$sourceSet/example/$fileName")
        assertTrue(Files.exists(generated), "Expected generated source $generated.")
    }
}

internal fun createUnconfiguredGenerateKompactSourcesTask(projectDirectory: Path): GenerateKompactSources =
    ProjectBuilder
        .builder()
        .withProjectDir(projectDirectory.toFile())
        .build()
        .tasks
        .register("generateKompactSources", GenerateKompactSources::class.java)
        .get()
