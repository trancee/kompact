# Code generation and build integration audit

## Architecture

The generation path has three pieces:

1. `kompact-ksp` parses/validates declarations and generates source.
2. `kompact-gradle-plugin` invokes KSP2 programmatically and wires common and
   platform source roots.
3. `kompact-ksp-integration` compiles generated code in a JVM fixture.

The separate Gradle adapter is a deliberate response to KSP common-metadata
integration limitations and is documented in ADR-0008 and
`docs/research/ksp-kmp-generation.md`.

## Material findings

### Reflection-based Gradle/KSP integration is a version-sensitive seam

`KompactGradlePlugin.kt` reflects over KGP extension/source-set/compiler
objects, including unchecked casts; `GenerateKompactSources.runKsp` reflects
into KSP2's internal `KSPLoader`. ADR-0008 acknowledges this coupling. TestKit
currently demonstrates the pinned version's happy path, cache/configuration
reuse and relocation behavior, but no negative test covers missing/retyped
KGP getters or a KSP internal-entrypoint mismatch. Decide the minimum upgrade
contract and failure diagnostics, and whether version-upgrade tests are
sufficient or an alternative integration is warranted.

Evidence: `kompact-gradle-plugin/src/main/kotlin/ch/trancee/kompact/gradle/KompactGradlePlugin.kt`,
`GenerateKompactSources.kt`,
`kompact-gradle-plugin/src/test/kotlin/ch/trancee/kompact/gradle/KompactGradlePluginIntegrationTest.kt`,
and `docs/adr/0008-framed-generated-views.md`.

### Unsupported targets are rejected, but the rejection contract is untested

The plugin maps JVM and selected Native targets, then throws for unsupported
Native/JS/Wasm types. Integration fixtures cover the five supported targets,
not rejection behavior or diagnostic wording. Add a release decision/test
only if the supported-target error is intended as a stable consumer contract.

Evidence: `KompactGradlePlugin.kt` (`actualOutputName`) and the KMP TestKit
fixture under `kompact-gradle-plugin/src/test/resources/kmp-consumer`.

### Some diagnostic and fallback choices are unpinned

The processor's round-safety and deterministic-error behavior have focused
tests, which is a strength. The audit did not find explicit coverage for the
aggregating KSP dependency fallback when `containingFile` is null, nor for
whether zero-field models should warn or error. Unannotated model properties
are skipped. Decide whether these are intentional documented behaviors or
diagnostic gaps, and test the chosen contract.

Evidence: `kompact-ksp/src/main/kotlin/ch/trancee/kompact/ksp/KompactSymbolProcessor.kt`,
`KompactModelParser.kt`, and
`kompact-ksp/src/test/kotlin/ch/trancee/kompact/ksp/KompactSymbolProcessorFlowTest.kt`.

### Cache behavior has useful happy-path evidence, with narrower edit coverage

`GenerateKompactSources` disables KSP incremental processing and relies on
Gradle task inputs/outputs and build cache. TestKit covers source deletion,
relocated build-cache restoration, up-to-date rebuilds, configuration-cache
reuse, and all supported target routes. Partial schema edits and concurrency
were not observed in the reviewed fixture. This is a test-scope decision, not
evidence of a current defect.

### The JVM integration test exercises a different path

`kompact-ksp-integration` directly configures KSP in a JVM test fixture. It
validates generated code compilation/round-trip but does not exercise the
custom plugin's multiplatform routing. The TestKit consumer is the evidence
for that route. Release gates should keep the roles clear and avoid treating
one as proof of the other.
