package ch.trancee.mutation

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Writes the MutFlow adapter rendered by [MutationAdapterSource] into a generated Kotlin source root. */
@CacheableTask
abstract class GenerateMutationAdapterTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testSources: ConfigurableFileCollection

    @get:Input
    abstract val adapterPackage: Property<String>

    @get:Input
    abstract val adapterClassName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val packageName = adapterPackage.get()
        val className = adapterClassName.get()
        val sources =
            testSources.asFileTree
                .matching { include("**/*.kt") }
                .files
                .sortedBy { it.invariantSeparatorsPath }
                .map { it.readText() }
        val root = outputDirectory.get().asFile
        root.deleteRecursively()
        val target = root.resolve(packageName.replace('.', '/')).resolve("$className.kt")
        target.parentFile.mkdirs()
        target.writeText(MutationAdapterSource.render(packageName, className, sources))
    }
}
