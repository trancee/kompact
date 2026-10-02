package ch.trancee.kompact.gradle

import java.lang.reflect.InvocationTargetException
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider

public class KompactGradlePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (!target.pluginManager.hasPlugin(KOTLIN_MULTIPLATFORM_PLUGIN)) {
            throw GradleException("Apply the Kotlin Multiplatform plugin before ch.trancee.kompact.codegen.")
        }

        val kotlin = target.extensions.findByName("kotlin")
            ?: throw GradleException("The Kotlin Multiplatform plugin did not create its 'kotlin' extension.")
        val sourceSets = namedObjects(kotlin, "sourceSets")
        val commonMain = sourceSets.findByName("commonMain")
            ?: throw GradleException("The Kotlin Multiplatform plugin did not create the 'commonMain' source set.")
        val commonMainKotlin = kotlinSources(commonMain)
        val versions = KompactPluginVersions.load(javaClass.classLoader)
        val commonOutput = target.layout.buildDirectory.dir("generated/kompact/main")
        val workDirectory = target.layout.buildDirectory.dir("kspCaches/kompact/commonMain")
        val engineClasspath =
            target.configurations.detachedConfiguration(
                target.dependencies.create(
                    "com.google.devtools.ksp:symbol-processing-api:${versions.ksp}",
                ),
                target.dependencies.create(
                    "com.google.devtools.ksp:symbol-processing-common-deps:${versions.ksp}",
                ),
                target.dependencies.create(
                    "com.google.devtools.ksp:symbol-processing-aa-embeddable:${versions.ksp}",
                ),
                target.dependencies.create(
                    "org.jetbrains.kotlin:kotlin-stdlib:${versions.kotlin}",
                ),
                target.dependencies.create(
                    "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:${versions.coroutines}",
                ),
            ).apply {
                isTransitive = false
            }
        val processorClasspath =
            target.configurations.detachedConfiguration(
                target.dependencies.create(
                    "ch.trancee.kompact:kompact-ksp:${versions.plugin}",
                ),
            )
        val defaultKotlinVersion =
            versions.kotlin
                .split('.', '-')
                .take(2)
                .joinToString(".")
        val compilerOptions = readKotlinProperty(kotlin, "compilerOptions")
        addExpectActualCompilerArgument(compilerOptions)

        val generateSources =
            target.tasks.register(
                "generateKompactSources",
                GenerateKompactSources::class.java,
            ) { task ->
                task.moduleName.set(target.name)
                task.projectDirectory.set(target.layout.projectDirectory)
                task.outputDirectory.set(commonOutput)
                task.workDirectory.set(workDirectory)
                task.kspClasspath.from(engineClasspath)
                task.processorClasspath.from(processorClasspath)
                task.commonClasspath.from(
                    target.provider {
                        val metadataCompileTask = target.tasks.getByName(COMMON_METADATA_COMPILE_TASK)
                        readKotlinProperty(metadataCompileTask, "libraries") as? FileCollection
                            ?: throw GradleException(
                                "Kompact code generation expected common metadata compilation libraries.",
                            )
                    },
                )
                task.commonSourceRoots.from(
                    target.provider {
                        commonMainKotlin.files.filterNot { source ->
                            source.toPath().normalize().startsWith(commonOutput.get().asFile.toPath().normalize())
                        }
                    },
                )
                task.processorOptions.put("kompact.generate", "kmp")
                task.languageVersion.set(
                    compilerOptionVersion(compilerOptions, "languageVersion", defaultKotlinVersion),
                )
                task.apiVersion.set(
                    compilerOptionVersion(compilerOptions, "apiVersion", defaultKotlinVersion),
                )
                task.allWarningsAsErrors.set(false)
                task.logLevel.set(LogLevel.entries.first { target.logger.isEnabled(it) }.ordinal)
            }

        commonMainKotlin.srcDir(generateSources.flatMap { it.outputDirectory.dir("common") })

        forEachObject(namedObjects(kotlin, "targets")) { kotlinTarget ->
            if (platformType(kotlinTarget) == COMMON_PLATFORM_TYPE) return@forEachObject
            val outputName = actualOutputName(kotlinTarget) ?: return@forEachObject
            val main = namedObjects(kotlinTarget, "compilations").findByName("main") ?: return@forEachObject
            val mainSourceSet = readKotlinProperty(main, "defaultSourceSet")
            kotlinSources(mainSourceSet).srcDir(generateSources.flatMap { it.outputDirectory.dir(outputName) })
        }
    }

    private fun actualOutputName(target: Any): String? {
        val targetName = readKotlinProperty(target, "name").toString()
        return when (platformType(target)) {
            COMMON_PLATFORM_TYPE -> null
            "jvm", "androidJvm" -> "jvm"
            "native" ->
                when {
                    targetName == ANDROID_NATIVE_ARM64_TARGET -> "androidArm64"
                    targetName.startsWith("ios") -> "ios"
                    else ->
                        throw GradleException(
                            "Kompact code generation does not support Kotlin/Native target '$targetName'.",
                        )
                }

            else ->
                throw GradleException(
                    "Kompact code generation does not support Kotlin target '$targetName'.",
                )
        }
    }

    private fun platformType(target: Any): String = readKotlinProperty(target, "platformType").toString()

    private fun kotlinSources(sourceSet: Any): SourceDirectorySet =
        readKotlinProperty(sourceSet, "kotlin") as? SourceDirectorySet
            ?: throw GradleException(
                "Kompact code generation expected a Gradle SourceDirectorySet for a Kotlin source set.",
            )

    private fun namedObjects(
        owner: Any,
        propertyName: String,
    ): NamedDomainObjectCollection<*> =
        readKotlinProperty(owner, propertyName) as? NamedDomainObjectCollection<*>
            ?: throw GradleException(
                "Kompact code generation expected '$propertyName' to be a named Gradle object collection.",
            )

    private fun forEachObject(
        objects: DomainObjectCollection<*>,
        action: (Any) -> Unit,
    ) {
        @Suppress("UNCHECKED_CAST")
        (objects as DomainObjectCollection<Any>).configureEach(Action(action))
    }

    private fun addExpectActualCompilerArgument(compilerOptions: Any) {
        val freeCompilerArgs =
            readKotlinProperty(compilerOptions, "freeCompilerArgs") as? ListProperty<*>
                ?: throw GradleException(
                    "Kompact code generation expected Kotlin compiler freeCompilerArgs to be a Gradle ListProperty.",
                )
        @Suppress("UNCHECKED_CAST")
        (freeCompilerArgs as ListProperty<String>).add(EXPECT_ACTUAL_CLASSES_ARGUMENT)
    }

    private fun compilerOptionVersion(
        compilerOptions: Any,
        propertyName: String,
        defaultVersion: String,
    ): Provider<String> {
        val version =
            readKotlinProperty(compilerOptions, propertyName) as? Provider<*>
                ?: throw GradleException(
                    "Kompact code generation expected Kotlin compiler option '$propertyName' to be a Gradle Provider.",
                )
        return version.map { it.toString() }.orElse(defaultVersion)
    }

    // KGP's plugin classes are isolated, so keep their types out of this plugin's classloader.
    private fun readKotlinProperty(
        receiver: Any,
        propertyName: String,
    ): Any {
        val getterName = "get${propertyName.replaceFirstChar(Char::uppercase)}"
        val getter =
            receiver.javaClass.methods.firstOrNull { method ->
                method.name == getterName && method.parameterCount == 0
            } ?: throw GradleException(
                "Kompact code generation is incompatible with ${receiver.javaClass.name}: " +
                    "missing $getterName().",
            )
        return try {
            requireNotNull(getter.invoke(receiver)) {
                "Kotlin Gradle property '$propertyName' was null."
            }
        } catch (failure: InvocationTargetException) {
            throw GradleException("Could not read Kotlin Gradle property '$propertyName'.", failure.targetException)
        } catch (failure: ReflectiveOperationException) {
            throw GradleException("Could not read Kotlin Gradle property '$propertyName'.", failure)
        }
    }

    private companion object {
        const val KOTLIN_MULTIPLATFORM_PLUGIN = "org.jetbrains.kotlin.multiplatform"
        const val COMMON_METADATA_COMPILE_TASK = "compileCommonMainKotlinMetadata"
        const val COMMON_PLATFORM_TYPE = "common"
        const val ANDROID_NATIVE_ARM64_TARGET = "androidNativeArm64"
        const val EXPECT_ACTUAL_CLASSES_ARGUMENT = "-Xexpect-actual-classes"
    }
}
