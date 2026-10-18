import ch.trancee.mutation.MutationResultsTask

// Applied conditionally by modules that run MutFlow (see .omp/AGENT-USAGE.md).

val includesProperty = providers.gradleProperty("mutationTest.includes").orNull
val mutationTestIncludes =
    includesProperty?.split(',')?.map(String::trim) ?: listOf(
        "ch.trancee.kompact.runtime.KompactRuntimeMutationTest",
        "ch.trancee.kompact.runtime.KompactRuntimeLongBitsMutationTest",
    )
require(includesProperty == null || mutationTestIncludes.all { it.isNotEmpty() }) {
    "mutationTest.includes must contain nonempty comma-separated Gradle test patterns"
}

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
