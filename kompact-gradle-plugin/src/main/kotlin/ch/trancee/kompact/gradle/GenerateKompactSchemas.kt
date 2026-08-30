package ch.trancee.kompact.gradle

import ch.trancee.kompact.processor.KompactRegistryValidator
import ch.trancee.kompact.processor.KompactSymbolProcessorProvider
import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPCommonConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Target
import com.google.devtools.ksp.symbol.KSNode
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

@CacheableTask
public abstract class GenerateKompactSchemas
@Inject
constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {
    @get:Incremental
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val sourceFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val registryFile: RegularFileProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val compatibilityBaseline: RegularFileProperty

    @get:Input public abstract val requireCompatibilityBaseline: Property<Boolean>
    @get:Input public abstract val namespace: Property<String>
    @get:Input public abstract val maxPacketBytes: Property<Int>
    @get:Input public abstract val languageVersion: Property<String>
    @get:Input public abstract val apiVersion: Property<String>

    @get:CompileClasspath public abstract val compileClasspath: ConfigurableFileCollection
    @get:Classpath public abstract val workerClasspath: ConfigurableFileCollection

    @get:OutputDirectory public abstract val kotlinOutputDirectory: DirectoryProperty
    @get:OutputDirectory public abstract val cOutputDirectory: DirectoryProperty
    @get:OutputDirectory public abstract val descriptorOutputDirectory: DirectoryProperty
    @get:OutputDirectory public abstract val reportOutputDirectory: DirectoryProperty
    @get:LocalState public abstract val cacheDirectory: DirectoryProperty
    @get:Internal public abstract val projectDirectoryInput: DirectoryProperty

    @TaskAction
    public fun generate(inputChanges: InputChanges) {
        val registryProblems =
            KompactRegistryValidator.validate(
                currentJson = registryFile.get().asFile.readText(),
                baselineJson = compatibilityBaseline.orNull?.asFile?.readText(),
                requireBaseline = requireCompatibilityBaseline.get(),
            )
        if (registryProblems.isNotEmpty()) {
            throw GradleException(
                registryProblems.joinToString(separator = "\n") { "${it.code} ${it.message}" }
            )
        }
        val stage =
            temporaryDir.resolve("stage").apply {
                deleteRecursively()
                mkdirs()
            }
        clearPublishedOutputs()
        val queue =
            workerExecutor.processIsolation { isolation ->
                isolation.classpath.from(workerClasspath)
            }
        queue.submit(KompactKspWorkAction::class.java) { parameters ->
            parameters.sourcePaths.set(
                sourceFiles.files
                    .map { if (it.isDirectory) it.absolutePath else it.parentFile.absolutePath }
                    .distinct()
                    .sorted()
            )
            parameters.libraryPaths.set(compileClasspath.files.map(File::getAbsolutePath).sorted())
            parameters.projectDirectory.set(projectDirectoryInput.get().asFile.absolutePath)
            parameters.stageDirectory.set(stage.absolutePath)
            parameters.cacheDirectory.set(cacheDirectory.get().asFile.absolutePath)
            parameters.namespace.set(namespace)
            parameters.maxPacketBytes.set(maxPacketBytes)
            parameters.languageVersion.set(languageVersion)
            parameters.apiVersion.set(apiVersion)
            parameters.incremental.set(inputChanges.isIncremental)
            if (inputChanges.isIncremental) {
                val changes =
                    inputChanges.getFileChanges(sourceFiles).filter { it.file.extension == "kt" }
                parameters.modifiedSourcePaths.set(
                    changes.filter { it.fileType != FileType.MISSING }.map { it.file.absolutePath }
                )
                parameters.removedSourcePaths.set(
                    changes.filter { it.fileType == FileType.MISSING }.map { it.file.absolutePath }
                )
            } else {
                parameters.modifiedSourcePaths.set(emptyList())
                parameters.removedSourcePaths.set(emptyList())
            }
        }
        queue.await()

        replaceDirectory(stage.resolve("kotlin"), kotlinOutputDirectory.get().asFile)
        replaceDirectory(stage.resolve("resources/c"), cOutputDirectory.get().asFile)
        replaceDirectory(
            stage.resolve("resources/descriptors"),
            descriptorOutputDirectory.get().asFile,
        )
        val reportDirectory = reportOutputDirectory.get().asFile.apply { mkdirs() }
        val descriptorDirectory = descriptorOutputDirectory.get().asFile
        val canonicalDescriptors =
            descriptorDirectory
                .walkTopDown()
                .filter { it.isFile && it.extension == "json" }
                .sortedBy { it.relativeTo(descriptorDirectory).invariantSeparatorsPath }
                .map(File::readText)
                .toList()
        val proposal =
            KompactRegistryValidator.propose(
                currentJson = registryFile.get().asFile.readText(),
                canonicalDescriptors = canonicalDescriptors,
            )
        reportDirectory.resolve("kompact-registry.proposed.json").writeText(proposal.json)
        if (proposal.problems.isNotEmpty() || !proposal.matchesCheckedInRegistry) {
            kotlinOutputDirectory.get().asFile.deleteRecursively()
            cOutputDirectory.get().asFile.deleteRecursively()
            descriptorOutputDirectory.get().asFile.deleteRecursively()
            val details =
                proposal.problems.joinToString(separator = "\n") { "${it.code} ${it.message}" }
            throw GradleException(
                if (details.isEmpty())
                    "KOMPACT-KSP-1006 checked-in registry differs from proposed registry"
                else details
            )
        }
    }

    private fun clearPublishedOutputs() {
        listOf(
                kotlinOutputDirectory.get().asFile,
                cOutputDirectory.get().asFile,
                descriptorOutputDirectory.get().asFile,
                reportOutputDirectory.get().asFile,
            )
            .forEach(File::deleteRecursively)
    }

    private fun replaceDirectory(source: File, destination: File) {
        destination.deleteRecursively()
        if (source.exists()) source.copyRecursively(destination, overwrite = true)
        else destination.mkdirs()
    }
}

public interface KompactKspWorkParameters : WorkParameters {
    public val sourcePaths: ListProperty<String>
    public val libraryPaths: ListProperty<String>
    public val projectDirectory: Property<String>
    public val stageDirectory: Property<String>
    public val cacheDirectory: Property<String>
    public val namespace: Property<String>
    public val maxPacketBytes: Property<Int>
    public val languageVersion: Property<String>
    public val apiVersion: Property<String>
    public val incremental: Property<Boolean>
    public val modifiedSourcePaths: ListProperty<String>
    public val removedSourcePaths: ListProperty<String>
}

public abstract class KompactKspWorkAction : WorkAction<KompactKspWorkParameters> {
    override fun execute() {
        val stage = File(parameters.stageDirectory.get()).apply { mkdirs() }
        val config =
            KSPCommonConfig.Builder()
                .apply {
                    moduleName = "kompact-common"
                    sourceRoots = parameters.sourcePaths.get().map(::File)
                    commonSourceRoots = sourceRoots
                    libraries = parameters.libraryPaths.get().map(::File)
                    projectBaseDir = File(parameters.projectDirectory.get())
                    outputBaseDir = stage
                    cachesDir = File(parameters.cacheDirectory.get()).apply { mkdirs() }
                    classOutputDir = stage.resolve("classes").apply { mkdirs() }
                    kotlinOutputDir = stage.resolve("kotlin").apply { mkdirs() }
                    resourceOutputDir = stage.resolve("resources").apply { mkdirs() }
                    languageVersion = parameters.languageVersion.get()
                    apiVersion = parameters.apiVersion.get()
                    incremental = parameters.incremental.get()
                    modifiedSources = parameters.modifiedSourcePaths.get().map(::File)
                    removedSources = parameters.removedSourcePaths.get().map(::File)
                    processorOptions =
                        mapOf(
                            "kompact.namespace" to parameters.namespace.get(),
                            "kompact.maxPacketBytes" to parameters.maxPacketBytes.get().toString(),
                        )
                    targets = listOf(Target("common", emptyMap()))
                }
                .build()
        val exitCode =
            KotlinSymbolProcessing(
                    config,
                    listOf(KompactSymbolProcessorProvider()),
                    WorkerKspLogger(),
                )
                .execute()
        if (exitCode != KotlinSymbolProcessing.ExitCode.OK) {
            throw GradleException("Kompact KSP processing failed: $exitCode")
        }
    }
}

private class WorkerKspLogger : KSPLogger {
    override fun logging(message: String, symbol: KSNode?) {
        println("[kompact] $message")
    }

    override fun info(message: String, symbol: KSNode?) {
        println("[kompact] $message")
    }

    override fun warn(message: String, symbol: KSNode?) {
        System.err.println("[kompact] warning: $message")
    }

    override fun error(message: String, symbol: KSNode?) {
        System.err.println("[kompact] error: $message")
    }

    override fun exception(e: Throwable) {
        throw GradleException("Kompact KSP processor failed", e)
    }
}
