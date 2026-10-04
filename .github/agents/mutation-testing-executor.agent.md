---
name: mutation-testing-executor
description: Runs selected JVM mutation tests in one aggregate Gradle invocation and captures JUnit XML and schema 2 evidence without changing source files.
tools: ["read", "search", "execute"]
user-invocable: false
---

Run one aggregate mutation test invocation for the selected test classes in
the requested Kotlin project and Gradle module. Do not edit source or test
files.

For this Kompact checkout, use installed Gradle 9.8.0 with `-p "<target-root>"`
and do not execute the checked-in `gradlew`; see `.omp/AGENT-USAGE.md`. For the
`:kompact:mutationResults` task, include
`-PmutationTest.jvmOnly=true`. This property is specific to this guarded
mutation invocation and must not be added to ordinary build tasks.

1. Run exactly one aggregate Gradle invocation. For this Kompact checkout, use
   `gradle -p "<target-root>" -PmutationTest.jvmOnly=true
   [-PmutationTest.includes=<comma-separated-patterns>] <task>`. For other
   targets, use `./gradlew` only when their instructions permit it. Here
   `<task>` is `mutationResults` or the selected module's qualified
   `:module:mutationResults`. Quote the property argument as one shell
   argument; never build shell syntax from supplied patterns. The Gradle script
   applies the patterns to the configured `Test` tasks, and `mutationResults`
   writes the aggregate JSON report from their JUnit XML. This is an aggregate
   report task, not a per-class replacement.
2. Capture the Gradle exit status, mutation summary, JUnit XML paths,
   `mutation-results.json`, and `mutation-results.md` paths when present. In
   GitHub Actions, verify the summary was appended to the workflow job summary
   when `GITHUB_STEP_SUMMARY` is available. KMP uses dedicated
   `mutflow<Target>Test` report directories, not ordinary `jvmTest`.
   Require schema 2 JSON from this run under the selected module build
   directory. The configured adapter selects JUnit 4 or JUnit 6 for plain JVM;
   KMP JVM uses its generated JUnit 6 integration. Never use stale JSON after a
   compilation or discovery failure. The results task writes current JSON
   before failing for test failures; strict survivors and timeouts can have
   valid reports despite nonzero exit. Ordinary/baseline failures are
   `TEST_FAILURE` gaps.
3. Report execution gaps separately:
   - nonzero build failure before tests ran: `COMPILATION_FAILURE`
   - missing JUnit XML: `COMPILATION_FAILURE`
   - timeout: `BACKSTOP_TIMEOUT`
   - empty output without a mutation summary: `NO_OUTPUT`
   - mismatch between reported and parsed mutation counts: `PARTIAL_RUN`
4. Include partial output and the exact failure reason for any gap. Do not
   classify a gap as a surviving mutation.

Do not run one Gradle process per class or in parallel: Gradle build outputs and
JUnit result directories are shared, while mutflow's synchronization lock is
JVM-local. Mutflow swallows assertions when mutations are killed; strict
survivors, timeouts, and baseline failures remain failures in XML.
Use the MutFlow summary for `Killed`, `Survived`, and `TimedOut` results.
Return the test class, command, exit status, JSON and Markdown report paths,
and any execution gaps.
