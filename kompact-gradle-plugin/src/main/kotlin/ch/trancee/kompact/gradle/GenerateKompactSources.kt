package ch.trancee.kompact.gradle

import com.google.devtools.ksp.processing.KSPCommonConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.util.ServiceLoader
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.work.NormalizeLineEndings

@CacheableTask
internal abstract class GenerateKompactSources : DefaultTask() {
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:NormalizeLineEndings
    abstract val commonSourceRoots: ConfigurableFileCollection

    @get:Classpath
    abstract val commonClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val kspClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val processorClasspath: ConfigurableFileCollection

    @get:Input
    abstract val moduleName: Property<String>

    @get:Input
    abstract val processorOptions: MapProperty<String, String>

    @get:Input
    abstract val languageVersion: Property<String>

    @get:Input
    abstract val apiVersion: Property<String>

    @get:Input
    abstract val allWarningsAsErrors: Property<Boolean>

    @get:Internal
    abstract val projectDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:LocalState
    abstract val workDirectory: DirectoryProperty

    @get:Internal
    abstract val logLevel: Property<Int>

    @TaskAction
    fun generate() {
        val outputRoot = outputDirectory.get().asFile
        deleteDirectory(outputRoot)
        val workRoot = workDirectory.get().asFile
        deleteDirectory(workRoot)

        val kspOutputRoot = workRoot.resolve("ksp")
        val kspCacheDirectory = workRoot.resolve("caches")
        val kotlinOutput = kspOutputRoot.resolve("kotlin")
        val classOutput = kspOutputRoot.resolve("classes")
        val resourceOutput = kspOutputRoot.resolve("resources")
        val commonSources = commonSourceRoots.files.toList()
        val commonLibraries = commonClasspath.files.toList()
        listOf(kotlinOutput, classOutput, resourceOutput, kspCacheDirectory).forEach(::createDirectory)

        val config =
            KSPCommonConfig.Builder().apply {
                moduleName = this@GenerateKompactSources.moduleName.get()
                sourceRoots = commonSources
                commonSourceRoots = commonSources
                libraries = commonLibraries
                friends = emptyList()
                processorOptions = this@GenerateKompactSources.processorOptions.get().toMap()
                projectBaseDir = this@GenerateKompactSources.projectDirectory.get().asFile
                outputBaseDir = kspOutputRoot
                cachesDir = kspCacheDirectory
                classOutputDir = classOutput
                kotlinOutputDir = kotlinOutput
                resourceOutputDir = resourceOutput
                incremental = false
                incrementalLog = false
                incrementalLogGraphOrigin = null
                modifiedSources = emptyList()
                removedSources = emptyList()
                changedClasses = emptyList()
                languageVersion = this@GenerateKompactSources.languageVersion.get()
                apiVersion = this@GenerateKompactSources.apiVersion.get()
                allWarningsAsErrors = this@GenerateKompactSources.allWarningsAsErrors.get()
                mapAnnotationArgumentsInJava = false
                experimentalPsiResolution = false
                targets = emptyList()
            }.build()

        runKsp(config)
        routeGeneratedSources(kotlinOutput, outputRoot)
    }

    private fun runKsp(config: KSPCommonConfig) {
        val engineUrls = kspClasspath.files.map { it.toURI().toURL() }.toTypedArray()
        val processorUrls = processorClasspath.files.map { it.toURI().toURL() }.toTypedArray()
        URLClassLoader(engineUrls, ClassLoader.getPlatformClassLoader()).use { engineClassLoader ->
            URLClassLoader(processorUrls, engineClassLoader).use { processorClassLoader ->
                val providerType = engineClassLoader.loadClass(PROCESSOR_PROVIDER_CLASS)
                val providers = ServiceLoader.load(providerType, processorClassLoader).toList()
                if (providers.isEmpty()) {
                    throw GradleException("No KSP processor providers were found in the Kompact processor classpath.")
                }

                val configBytes =
                    ByteArrayOutputStream().use { bytes ->
                        ObjectOutputStream(bytes).use { it.writeObject(config) }
                        bytes.toByteArray()
                    }
                val exitCode =
                    try {
                        engineClassLoader
                            .loadClass(KSP_LOADER_CLASS)
                            .getMethod(
                                "loadAndRunKSP",
                                ByteArray::class.java,
                                List::class.java,
                                Int::class.java,
                            ).invoke(null, configBytes, providers, logLevel.get()) as Int
                    } catch (failure: InvocationTargetException) {
                        throw GradleException("KSP2 failed while processing commonMain schemas.", failure.targetException)
                    } catch (failure: ReflectiveOperationException) {
                        throw GradleException(
                            "The configured KSP version does not expose the expected KSP2 programmatic entry point.",
                            failure,
                        )
                    }
                if (exitCode != KSP_SUCCESS_EXIT_CODE) {
                    throw GradleException("KSP2 commonMain processing failed with exit code $exitCode.")
                }
            }
        }
    }

    private fun routeGeneratedSources(
        kotlinOutput: File,
        outputRoot: File,
    ) {
        if (!kotlinOutput.exists()) return
        kotlinOutput
            .walkTopDown()
            .filter(File::isFile)
            .forEach { source ->
                if (source.extension != "kt") {
                    throw GradleException("Kompact KSP generated an unsupported file: ${source.name}")
                }
                val destination = outputRoot.resolve(sourceSetFor(source)).resolve(kotlinOutput.toPath().relativize(source.toPath()).toString())
                createDirectory(destination.parentFile)
                source.copyTo(destination, overwrite = true)
            }
    }

    private fun sourceSetFor(file: File): String =
        when {
            file.name.endsWith("GenJvm.kt") -> "jvm"
            file.name.endsWith("GenIos.kt") -> "ios"
            file.name.endsWith("GenAndroidArm64.kt") -> "androidArm64"
            file.name.endsWith("GenEncoder.kt") -> "common"
            file.name.endsWith("Gen.kt") -> "common"
            else -> throw GradleException("Kompact KSP generated an unexpected Kotlin file: ${file.name}")
        }

    private fun deleteDirectory(directory: File) {
        if (directory.exists() && !directory.deleteRecursively()) {
            throw GradleException("Could not clear the Kompact codegen directory: ${directory.absolutePath}")
        }
    }

    private fun createDirectory(directory: File) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw GradleException("Could not create the Kompact codegen directory: ${directory.absolutePath}")
        }
    }

    private companion object {
        const val PROCESSOR_PROVIDER_CLASS = "com.google.devtools.ksp.processing.SymbolProcessorProvider"
        const val KSP_LOADER_CLASS = "com.google.devtools.ksp.impl.KSPLoader"
        const val KSP_SUCCESS_EXIT_CODE = 0
    }
}
