package ch.trancee.kompact.gradle

import ch.trancee.kompact.processor.KompactSymbolProcessorProvider
import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPCommonConfig
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

public class KompactPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<KompactExtension>("kompact")
        extension.registryFile.convention(
            project.layout.projectDirectory.file("kompact-registry.json")
        )

        var configured = false
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            configured = true
            configureKmpProject(project, extension)
        }
        project.afterEvaluate {
            if (!configured) {
                throw GradleException(
                    "KOMPACT-KSP-1001 ch.trancee.kompact requires Kotlin Multiplatform"
                )
            }
        }
    }

    private fun configureKmpProject(project: Project, extension: KompactExtension) {
        val kotlin = project.extensions.getByType<KotlinMultiplatformExtension>()
        val commonMain = kotlin.sourceSets.getByName("commonMain")
        val handwrittenSourceDirectories = commonMain.kotlin.srcDirs.toList()
        val generatedRoot =
            extension.namespace.flatMap { namespace ->
                project.layout.buildDirectory.dir("generated/kompact/$namespace")
            }
        val localStateRoot =
            extension.namespace.flatMap { namespace ->
                project.layout.buildDirectory.dir("kompact/$namespace")
            }
        val generation =
            project.tasks.register<GenerateKompactSchemas>("generateKompactSchemas") {
                group = "kompact"
                description = "Generates common Kotlin, C99 headers, and canonical descriptors"
                sourceFiles.from(handwrittenSourceDirectories)
                registryFile.set(extension.registryFile)
                compatibilityBaseline.set(extension.compatibilityBaseline)
                requireCompatibilityBaseline.set(extension.requireCompatibilityBaseline)
                namespace.set(extension.namespace)
                maxPacketBytes.set(extension.maxPacketBytes)
                languageVersion.convention("2.3")
                apiVersion.convention("2.3")
                compileClasspath.from(
                    project.configurations.findByName("commonMainCompileClasspath"),
                    project.configurations.findByName("commonMainCompileDependenciesMetadata"),
                    classLocation(Unit::class.java),
                )
                workerClasspath.from(
                    classLocation(KompactPlugin::class.java),
                    classLocation(KompactSymbolProcessorProvider::class.java),
                    classLocation(KotlinSymbolProcessing::class.java),
                    classLocation(KSPCommonConfig::class.java),
                    classLocation(Unit::class.java),
                )
                kotlinOutputDirectory.set(generatedRoot.map { it.dir("kotlin") })
                cOutputDirectory.set(generatedRoot.map { it.dir("c") })
                descriptorOutputDirectory.set(generatedRoot.map { it.dir("descriptors") })
                reportOutputDirectory.set(generatedRoot.map { it.dir("reports") })
                cacheDirectory.set(localStateRoot.map { it.dir("cache") })
                projectDirectoryInput.set(project.layout.projectDirectory)
            }
        commonMain.kotlin.srcDir(generation.flatMap(GenerateKompactSchemas::kotlinOutputDirectory))

        val check =
            project.tasks.register("checkKompactSchemas") { task ->
                task.group = "verification"
                task.description = "Validates Kompact schemas, registry, and compatibility baseline"
                task.dependsOn(generation)
            }
        project.tasks
            .matching { it.name == "check" }
            .configureEach { task -> task.dependsOn(check) }

        val cArchive =
            project.tasks.register<Zip>("packageKompactCHeaders") {
                group = "build"
                description = "Packages generated Kompact C99 headers"
                dependsOn(generation)
                from(generation.flatMap(GenerateKompactSchemas::cOutputDirectory))
                archiveClassifier.set(extension.cHeadersClassifier)
                destinationDirectory.set(project.layout.buildDirectory.dir("distributions"))
                isPreserveFileTimestamps = false
                isReproducibleFileOrder = true
            }
        val cHeaders =
            project.configurations.create("kompactCHeaders").also { configuration ->
                configuration.isCanBeConsumed = true
                configuration.isCanBeResolved = false
                configuration.attributes.attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    project.objects.named(Category::class.java, Category.DOCUMENTATION),
                )
                configuration.attributes.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME),
                )
                configuration.attributes.attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    project.objects.named(LibraryElements::class.java, "kompact-c-headers"),
                )
                configuration.outgoing.artifact(cArchive)
            }
        project.artifacts.add(cHeaders.name, cArchive)

        project.pluginManager.withPlugin("maven-publish") {
            project.afterEvaluate {
                if (extension.publishCHeaders.get()) {
                    project.extensions
                        .getByType<PublishingExtension>()
                        .publications
                        .withType(MavenPublication::class.java)
                        .matching { it.name == "kotlinMultiplatform" }
                        .configureEach { publication -> publication.artifact(cArchive) }
                }
            }
        }
    }

    private fun classLocation(type: Class<*>): File =
        File(type.protectionDomain.codeSource.location.toURI())
}
