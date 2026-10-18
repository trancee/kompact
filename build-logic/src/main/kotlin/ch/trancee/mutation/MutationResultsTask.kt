package ch.trancee.mutation

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Collects MutFlow JUnit XML reports into schema 2 JSON plus a readable Markdown summary. */
abstract class MutationResultsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val junitReports: ConfigurableFileCollection

    @get:OutputFile
    abstract val resultsFile: RegularFileProperty

    @get:OutputFile
    abstract val summaryFile: RegularFileProperty

    @TaskAction
    fun generateResults() {
        val xmlFiles =
            junitReports.files
                .flatMap { directory ->
                    directory
                        .walkTopDown()
                        .filter {
                            it.isFile && it.name.startsWith("TEST-") && it.extension == "xml"
                        }.toList()
                }.sortedBy { it.absolutePath }
        val reports = xmlFiles.map(JUnitMutationReportReader::read)
        val gaps = reports.flatMap { it.gaps }.toMutableList()
        if (xmlFiles.isEmpty()) {
            gaps += ExecutionGap("NO_OUTPUT", "No current JUnit XML reports were produced")
        }
        val results =
            MutationResultsParser.assembleResults(
                mutations = reports.flatMap { it.mutations },
                testMethods = reports.flatMap { it.testMethods }.distinct().sorted(),
                gaps = gaps,
                discoveredMutations = reports.sumOf { it.discoveredMutations },
            )
        val runFailed = reports.any { it.hasTestFailures } || gaps.isNotEmpty()
        val summary = MutationResultsSummary.render(results, runFailed)
        resultsFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(MutationResultsSerializer.toJson(results))
        }
        summaryFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("$summary\n")
        }
        logger.lifecycle(summary)
        logger.lifecycle("Readable report written to: ${summaryFile.get().asFile}")
        logger.lifecycle("JSON report written to: ${resultsFile.get().asFile}")
        appendGithubStepSummary(summary)
        if (runFailed) {
            throw GradleException(
                "Mutation run failed or was incomplete; inspect mutation-results.md, mutation-results.json, and JUnit XML",
            )
        }
    }

    private fun appendGithubStepSummary(summary: String) {
        if (System.getenv("GITHUB_ACTIONS") != "true") return

        val summaryPath = System.getenv("GITHUB_STEP_SUMMARY")
        if (summaryPath.isNullOrBlank()) {
            logger.warn(
                "GITHUB_STEP_SUMMARY is unavailable; the readable mutation report is at ${summaryFile.get().asFile}",
            )
            return
        }

        val githubSummaryFile = java.io.File(summaryPath)
        if (!githubSummaryFile.isAbsolute || !githubSummaryFile.isFile) {
            logger.warn(
                "GITHUB_STEP_SUMMARY must be an existing absolute file; " +
                    "the readable mutation report is at ${summaryFile.get().asFile}",
            )
            return
        }

        try {
            val separator = if (githubSummaryFile.length() == 0L) "" else "\n"
            githubSummaryFile.appendText("$separator$summary\n\n")
            logger.lifecycle("Mutation results added to the GitHub Actions job summary")
        } catch (failure: java.io.IOException) {
            logger.warn(
                "Could not update the GitHub Actions job summary; the readable mutation report is at ${summaryFile.get().asFile}",
                failure,
            )
        }
    }
}
