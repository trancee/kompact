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

/**
 * Internal compatibility helpers are shared with contract tests so supported KGP behavior and
 * actionable diagnostics remain verifiable without reflective access to private implementation details.
 */
public class KompactGradlePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (!target.pluginManager.hasPlugin(KOTLIN_MULTIPLATFORM_PLUGIN)) {
            throw GradleException("Apply the Kotlin Multiplatform plugin before ch.trancee.kompact.codegen.")
        }

        val versions = KompactPluginVersions.load(javaClass.classLoader)
        val kotlinPlugin = target.plugins.findPlugin(KOTLIN_MULTIPLATFORM_PLUGIN)
        val detectedKotlinVersion = kotlinPlugin?.javaClass?.`package`?.implementationVersion
            ?: "<unknown>"
        val compatibility = compatibilityDetails(versions, detectedKotlinVersion)
        val kotlin = target.extensions.findByName("kotlin")
            ?: throw GradleException(
                "The Kotlin Multiplatform plugin did not create its 'kotlin' extension. $compatibility",
            )
        if (detectedKotlinVersion != versions.kotlin) {
            throw GradleException("Kompact code generation is incompatible. $compatibility")
        }
        val sourceSets = namedObjects(kotlin, "sourceSets", compatibility)
        val commonMain = sourceSets.findByName("commonMain")
            ?: throw GradleException(
                "The Kotlin Multiplatform plugin did not create the 'commonMain' source set. $compatibility",
            )
        val commonMainKotlin = kotlinSources(commonMain, compatibility)
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
        val compilerOptions = readKotlinProperty(kotlin, "compilerOptions", compatibility)
        addExpectActualCompilerArgument(compilerOptions, compatibility)

        val generateSources =
            target.tasks.register(
                "generateKompactSources",
                GenerateKompactSources::class.java,
            ) { task ->
                task.moduleName.set(target.name)
                task.kotlinPluginVersion.set(versions.kotlin)
                task.kspVersion.set(versions.ksp)
                task.projectDirectory.set(target.layout.projectDirectory)
                task.outputDirectory.set(commonOutput)
                task.workDirectory.set(workDirectory)
                task.kspClasspath.from(engineClasspath)
                task.processorClasspath.from(processorClasspath)
                task.commonClasspath.from(
                    target.provider {
                        val metadataCompileTask = target.tasks.getByName(COMMON_METADATA_COMPILE_TASK)
                        readKotlinProperty(metadataCompileTask, "libraries", compatibility) as? FileCollection
                            ?: throw GradleException(
                                "Kompact code generation expected common metadata compilation libraries. " +
                                    compatibility,
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
                    compilerOptionVersion(compilerOptions, "languageVersion", defaultKotlinVersion, compatibility),
                )
                task.apiVersion.set(
                    compilerOptionVersion(compilerOptions, "apiVersion", defaultKotlinVersion, compatibility),
                )
                task.allWarningsAsErrors.set(false)
                task.logLevel.set(LogLevel.entries.first { target.logger.isEnabled(it) }.ordinal)
            }

        commonMainKotlin.srcDir(generateSources.flatMap { it.outputDirectory.dir("common") })

        forEachObject(namedObjects(kotlin, "targets", compatibility)) { kotlinTarget ->
            if (platformType(kotlinTarget, compatibility) == COMMON_PLATFORM_TYPE) return@forEachObject
            val outputName = actualOutputName(kotlinTarget, compatibility) ?: return@forEachObject
            val main =
                namedObjects(kotlinTarget, "compilations", compatibility).findByName("main")
                    ?: return@forEachObject
            val mainSourceSet = readKotlinProperty(main, "defaultSourceSet", compatibility)
            kotlinSources(mainSourceSet, compatibility).srcDir(
                generateSources.flatMap { it.outputDirectory.dir(outputName) },
            )
        }
    }

    internal fun actualOutputName(
        target: Any,
        compatibility: String,
    ): String? {
        val targetName = readKotlinProperty(target, "name", compatibility).toString()
        return when (platformType(target, compatibility)) {
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

    private fun platformType(
        target: Any,
        compatibility: String,
    ): String = readKotlinProperty(target, "platformType", compatibility).toString()

    internal fun kotlinSources(
        sourceSet: Any,
        compatibility: String,
    ): SourceDirectorySet =
        readKotlinProperty(sourceSet, "kotlin", compatibility) as? SourceDirectorySet
            ?: throw GradleException(
                "Kompact code generation expected a Gradle SourceDirectorySet for a Kotlin source set. " +
                    compatibility,
            )

    internal fun namedObjects(
        owner: Any,
        propertyName: String,
        compatibility: String,
    ): NamedDomainObjectCollection<*> =
        readKotlinProperty(owner, propertyName, compatibility) as? NamedDomainObjectCollection<*>
            ?: throw GradleException(
                "Kompact code generation expected '$propertyName' to be a named Gradle object collection. " +
                    compatibility,
            )

    private fun forEachObject(
        objects: DomainObjectCollection<*>,
        action: (Any) -> Unit,
    ) {
        @Suppress("UNCHECKED_CAST")
        (objects as DomainObjectCollection<Any>).configureEach(Action(action))
    }

    internal fun addExpectActualCompilerArgument(
        compilerOptions: Any,
        compatibility: String,
    ) {
        val freeCompilerArgs =
            readKotlinProperty(compilerOptions, "freeCompilerArgs", compatibility) as? ListProperty<*>
                ?: throw GradleException(
                    "Kompact code generation expected Kotlin compiler freeCompilerArgs to be a Gradle ListProperty. " +
                        compatibility,
                )
        @Suppress("UNCHECKED_CAST")
        (freeCompilerArgs as ListProperty<String>).add(EXPECT_ACTUAL_CLASSES_ARGUMENT)
    }

    internal fun compilerOptionVersion(
        compilerOptions: Any,
        propertyName: String,
        defaultVersion: String,
        compatibility: String,
    ): Provider<String> {
        val version =
            readKotlinProperty(compilerOptions, propertyName, compatibility) as? Provider<*>
                ?: throw GradleException(
                    "Kompact code generation expected Kotlin compiler option '$propertyName' to be a Gradle Provider. " +
                        compatibility,
                )
        return version.map { it.toString() }.orElse(defaultVersion)
    }

    private fun compatibilityDetails(
        versions: KompactPluginVersions,
        detectedKotlinVersion: String,
    ): String =
        "Detected Kotlin Gradle Plugin $detectedKotlinVersion with KSP ${versions.ksp}; " +
            "only tested pair is Kotlin Gradle Plugin ${versions.kotlin} and KSP ${versions.ksp}."

    // KGP's plugin classes are isolated, so keep their types out of this plugin's classloader.
    internal fun readKotlinProperty(
        receiver: Any,
        propertyName: String,
        compatibility: String,
    ): Any {
        val getterName = "get${propertyName.replaceFirstChar(Char::uppercase)}"
        val getter =
            receiver.javaClass.methods.firstOrNull { method ->
                method.name == getterName && method.parameterCount == 0
            } ?: throw GradleException(
                "Kompact code generation is incompatible with ${receiver.javaClass.name}: " +
                    "missing $getterName(). $compatibility",
            )
        return try {
            getter.invoke(receiver)
                ?: throw GradleException("Kotlin Gradle property '$propertyName' was null. $compatibility")
        } catch (failure: InvocationTargetException) {
            throw GradleException(
                "Could not read Kotlin Gradle property '$propertyName'. $compatibility",
                failure.targetException,
            )
        } catch (failure: ReflectiveOperationException) {
            throw GradleException("Could not read Kotlin Gradle property '$propertyName'. $compatibility", failure)
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
