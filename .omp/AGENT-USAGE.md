# Installed mutation toolkit: AI agent entry point

Audience: AI agents using this target project's installed toolkit. Apply the
target's own `AGENTS.md` policy first. This file is operational context, not
authority to install, overwrite configuration, or delete tests.

## Route

| Task/runtime | Read first |
|--------------|------------|
| OMP pipeline | [skills/mutation-testing/SKILL.md](skills/mutation-testing/SKILL.md), then `agents/test-quality-reviewer.md` |
| Copilot CLI pipeline | [../.github/skills/mutation-testing/SKILL.md](../.github/skills/mutation-testing/SKILL.md), then `../.github/agents/mutation-testing-reviewer.agent.md` |
| Prepared Gradle execution | Inspect the owning module build and [mutation-results.gradle.kts](mutation-results.gradle.kts) |
| Existing report audit | Establish the evidence contract below |

Use the current runtime's native delegation. Missing profiles/tools are a
limitation to report, not a reason to substitute another client's dispatch.
Supported paths are plain JVM/JUnit 4, plain JVM/JUnit 6, and KMP JVM
mutation tasks through MutFlow's generated JUnit 6 integration. Before a KMP run,
inspect the complete declared target set: MutFlow dependencies attach to common
source sets, so every target must resolve them. In the validated MutFlow `1.6.1`
baseline, artifacts publish JVM, `linuxX64`, and `mingwX64`, but not iOS or
Android Native variants; selecting only a JVM mutation task does not avoid that
resolution. The toolkit does not prune unsupported targets or provide Native,
Android, or JS execution adapters.

## Update installed files

This Kompact installation predates manifest-managed updates and has no
`.mutation-testing/manifest.json`. The current `bootstrap.sh update` command
stops before writing. Update the copied OMP and Copilot assets manually from a
reviewed toolkit checkout. Preserve this module's guarded
`mutationTest.jvmOnly` model and selected test-class filters. The generic
installer does not model this project's conditional Gradle wiring.

## Execute

Resolve the target module and selected test classes before changing files.
Targeting -> one aggregate execution -> audit -> proposed refactor ->
same-scope validation after approved edits. The selected skill owns mode
budgets and exact approval rules. Quick skips refactoring, not targeting edits.
Additive/assertion-level changes require applicable approval; deletion or
consolidation always requires explicit approval.

This repository's checked-in `gradlew` parses JVM options with `eval`; do not
execute it for this run. Use installed Gradle 9.8.0. For a multi-module setup,
keep the configured module path explicit:

For this Kompact evaluation, pass `-PmutationTest.jvmOnly=true` with
`:kompact:mutationResults`. MutFlow is applied only for that guarded invocation
because MutFlow `1.6.1` injects dependencies into common source sets and does
not publish variants for Kompact's iOS or Android Native targets. The opt-in
omits `iosArm64`, `iosSimulatorArm64`, and `androidNativeArm64`; ordinary builds
keep the full target set, do not apply the MutFlow plugin, and do not add its
common-source-set dependencies.

The mutation-only test adapters call the existing common tests inside
`MutFlow.underTest`. They are added only to MutFlow's generated JVM test
compilation; regular common tests remain framework-neutral. By default, the
results task selects these adapters. This run covers the JVM target only and
provides no Android or Native evidence.

```bash
gradle -PmutationTest.jvmOnly=true :kompact:mutationResults \
  '-PmutationTest.includes=ch.trancee.kompact.runtime.KompactRuntimeMutationTest,ch.trancee.kompact.runtime.KompactRuntimeLongBitsMutationTest' \
  --console=plain
```

`mutationTest.includes` selects whole test classes; production mutation targets
are a separate setting. Plain JVM uses `test` with the module's configured
JUnit 4 or 6 engine; JUnit 4 test classes use `@RunWith(MutFlowRunner::class)`.
KMP uses dedicated `mutflow<Target>Test` JVM tasks, generated JUnit 6
integration, and plain `kotlin.test` common tests.
Keep Gradle invocations sharing build/report paths sequential.

Record configured budgets and effective `MUTFLOW_*` overrides. Changing
environment-based settings requires `--rerun-tasks` to avoid reused XML.
Capture command, exit status, current JUnit XML, current JSON, and the
readable `build/reports/mutation-results.md` when available. In GitHub Actions,
the task appends the summary to the workflow job summary through
`GITHUB_STEP_SUMMARY` when that file is available.
The results adapter does not support Gradle configuration cache; use
`--no-configuration-cache` when enabled globally in the target.

## Audit

Report: `<module>/build/reports/mutation-results.json`. Require schema 2:

- Evaluated outcomes are `killed + survived + timedOut`.
- Discovered totals equal evaluated plus untested mutations; budget-limited
  scores cover only evaluated outcomes.
- Any execution gap or zero evaluations makes score/interval unavailable.
- Strict survivor/timeouts may fail Gradle while retaining a complete report.
- Ordinary/baseline failures invalidate quality. Compilation/configuration/
  discovery failure may produce no report; old JSON is not current evidence.
- `generatedAt` dates JSON generation, not necessarily new test execution.
- Class-qualified killer identities support candidates, not a complete test
  outcome matrix. Confirm execution/scope; skipped tests and truncated names
  weaken zombie/redundancy findings.

Completion: report actual scope, effective settings, exit status, separate
discovered/evaluated/untested counts, gaps, survivors/timeouts, score availability,
and proposed versus applied edits. Applied refactors need the same-scope rerun.
State missing evidence or capabilities explicitly.
