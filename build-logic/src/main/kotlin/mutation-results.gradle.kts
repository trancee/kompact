import ch.trancee.mutation.GenerateMutationAdapterTask
import ch.trancee.mutation.MutationResultsExtension
import ch.trancee.mutation.MutationResultsTask

// Applied conditionally by modules that run MutFlow (see .omp/AGENT-USAGE.md).

val mutationTesting =
    extensions.create<MutationResultsExtension>("mutationTesting").apply {
        adapterClassName.convention("GeneratedMutationTest")
        includes.convention(adapterPackage.zip(adapterClassName) { pkg, cls -> listOf("$pkg.$cls") })
    }
// Modules add this task's output to the compilation that MutFlow runs against mutated code.
tasks.register<GenerateMutationAdapterTask>("generateMutationAdapter") {
    description = "Generates one MutFlow adapter that wraps every behavior test of this module"
    testSources.from(mutationTesting.adapterSources)
    adapterPackage.set(mutationTesting.adapterPackage)
    adapterClassName.set(mutationTesting.adapterClassName)
    outputDirectory.set(layout.buildDirectory.dir("generated/mutationAdapter/kotlin"))
}
val includesOverride =
    providers.gradleProperty("mutationTest.includes").map { patterns -> patterns.split(',').map(String::trim) }

val mutationResults =
    tasks.register<MutationResultsTask>("mutationResults") {
        group = "verification"
        description = "Writes schema 2 mutation results and a readable summary from current JVM JUnit reports"
        resultsFile.convention(layout.buildDirectory.file("reports/mutation-results.json"))
        summaryFile.convention(layout.buildDirectory.file("reports/mutation-results.md"))
    }
val prepareMutationResults =
    tasks.register("prepareMutationResults") {
        doLast {
            delete(
                mutationResults
                    .get()
                    .resultsFile
                    .get()
                    .asFile,
                mutationResults
                    .get()
                    .summaryFile
                    .get()
                    .asFile,
            )
        }
    }
tasks.matching { it.name.startsWith("compile") }.configureEach {
    mustRunAfter(prepareMutationResults)
}

// KMP creates its dedicated mutation tasks after evaluation.
afterEvaluate {
    val mutationTestFramework = findProperty("mutationTest.junitFramework")?.toString() ?: "junit6"
    require(mutationTestFramework in setOf("junit4", "junit6")) {
        "mutationTest.junitFramework must be 'junit4' or 'junit6'"
    }
    val isKmp = plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
    val selectedTests =
        tasks.withType<Test>().matching {
            if (isKmp) it.name.startsWith("mutflow") && it.name.endsWith("Test") else it.name == "test"
        }
    require(!selectedTests.isEmpty()) {
        "No supported JVM mutation test task found; enable mutflow and configure a JVM target"
    }
    selectedTests.configureEach {
        dependsOn(prepareMutationResults)
        if (mutationTestFramework == "junit6") {
            useJUnitPlatform()
        } else {
            useJUnit()
        }
        reports.junitXml.required.set(true)
        testLogging.showStandardStreams = true
    }
    gradle.taskGraph.whenReady {
        if (hasTask(mutationResults.get())) {
            val mutationTestIncludes = includesOverride.orElse(mutationTesting.includes).get()
            require(mutationTestIncludes.isNotEmpty() && mutationTestIncludes.all { it.isNotEmpty() }) {
                "Set mutationTesting.includes or pass -PmutationTest.includes as nonempty comma-separated test patterns"
            }
            // Allow XML collection, then restore the failure exit status after writing JSON.
            selectedTests.forEach { testTask ->
                testTask.ignoreFailures = true
                mutationTestIncludes.forEach { testTask.filter.includeTestsMatching(it) }
            }
        }
    }
    mutationResults.configure {
        dependsOn(selectedTests)
        junitReports.from(
            selectedTests.map {
                it.reports.junitXml.outputLocation
                    .get()
                    .asFile
            },
        )
    }
}
