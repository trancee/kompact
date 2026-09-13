import org.jetbrains.dokka.gradle.formats.DokkaFormatPlugin
import org.jetbrains.dokka.gradle.internal.InternalDokkaGradlePluginApi

plugins {
    id("org.jetbrains.dokka")
}

/**
 * Convention plugin that registers the Dokka "markdown" (GFM) format, producing
 * `:dokkaGeneratePublicationMarkdown` instead of the default `:dokkaGeneratePublicationHtml`.
 *
 * Class name is intentionally different from the file name (`dokka-markdown`)
 * to avoid Kotlin DSL generated-accessor collision (which would produce
 * `DokkaMarkdownPlugin`).
 *
 * Ref: https://www.kotlinlang.org/docs/dokka-gradle.html#formats
 */
@OptIn(InternalDokkaGradlePluginApi::class)
abstract class DokkaMarkdownFormatPlugin : DokkaFormatPlugin(formatName = "markdown") {
    override fun DokkaFormatPlugin.DokkaFormatPluginContext.configure() {
        project.dependencies {
            dokkaPlugin(dokka("gfm-plugin"))
            formatDependencies.dokkaPublicationPluginClasspathApiOnly.dependencies.addLater(
                dokka("gfm-template-processing-plugin"),
            )
        }
    }
}

apply<DokkaMarkdownFormatPlugin>()

// Suppress default HTML Dokka generation — only Markdown is committed.
tasks
    .matching {
        it.name == "dokkaGeneratePublicationHtml" ||
            it.name == "dokkaHtml" ||
            it.name == "dokkaHtmlMultiModule"
    }.configureEach {
        enabled = false
    }
