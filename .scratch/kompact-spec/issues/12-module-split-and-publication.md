---
Type: grilling
Status: resolved
Labels:
  - scope:publication
  - scope:build
  - kind:packaging
Blocked by:
  - "02 generation strategy"
  - "10 cross-platform testing model"
Decides: []
---

# Ticket 12 — Module split & publication

## Question

The Destination requires locking the **publication shape**. Kompact is Kotlin Multiplatform (commonMain + jvmMain + iosArm64Main + iosSimulatorArm64Main actuals) with a KSP processor (ticket 02) and a commonTest testing model (tickets 10–11). How is it packaged and published? Informed by 02 + 10 + 11.

Decide:
- **Artifact shape**: single KMP library (common + platform actuals + KSP processor co-located) vs split into separate modules. Tradeoff: single = simplest publication & consumption for v1; split = smaller client classpath (JVM-only KSP processor isolated from the KMP runtime), but more modules to publish and version.
- **Published test/benchmark infra**: the commonTest tests (10) and benchmarks (11) are `testImplementation` / `benchmark` deps and do **not** ship as published API. Confirm.
- **KSP processor packaging & coherence**: the processor emits `expect` value-class source into commonMain (02); the published artifact must keep generated sources + KSP processor + runtime coherent. A KSP-safe processor jar + `multiplatformPublication` + `binary-compatibility-validator` (02 stub-source packaging). Exact gradle wiring is implementation detail.

Consequence: ticket 12's shape is the **last gating decision** before the destination spec locks.

## Answer

User decided: adopt the recommended option on all three forks.

**Artifact shape — split modules.**
`:kompact` (KMP runtime + commonMain Main API) + `:kompact-ksp` (JVM-only KSP processor with `KompactAnnotations.kt` packaged as ksp-stubs per ticket 02) + optional `:kompact-gradle-plugin`. Rationale: KSP processors are JVM-only (the KSP API runs on the JVM), and ticket 02's stub-source requirement keeps the processor's common annotations separate from the multiplatform runtime — so bundling a JVM-only processor into a multiplatform artifact is non-standard and conflicts with the stub wiring. Splitting lets each module publish by its own mechanism: runtime via `multiplatformPublication`; processor as a JVM jar.

**Published test/benchmark infra — do not ship.**
Tests (10) and benchmarks (11) remain in `commonTest` / `benchmark` (`testImplementation`), excluded from the published artifact. This is the standard KMP convention; shipping them as public API pollutes the API surface and invites version drift.

**KSP processor distribution — KSP-safe jar + multiplatformPublication.**
Consumers apply the processor jar via `ksp` against the published annotations; it emits the `expect`/`actual` value-class sources into `commonMain` (02, coherent with the runtime the consumer depends on). The runtime publishes via `multiplatformPublication` (metadata + `klib` `iosArm64` + `iosSimulatorArm64`), ABI baselined by `kotlinx binary-compatibility-validator`. A Gradle plugin wrapper adds machinery without v1 benefit.

**Wiring deferred.** The exact gradle `multiplatformPublication` / klib target coordinates / KSP-safe-jar coordinates / `binary-compatibility-validator` baselined-ABI are implementation detail, deferred. They are resolvable by a `wayfinder:research` subagent on request for current KMP publication best practices — not a blocking decision for the spec.

**Tradeoff accepted.** Split adds a `:kompact-ksp` module and a separate publication coordinate — marginally more publishing surface — but it is the *only* compliant shape given that the KSP processor is JVM-only and ticket 02 mandates stub-source packaging. Single-artifact (rejected) would force an unsupported mix of a JVM-only processor into a multiplatform publication.

**Consequences.**
- **Publication shape locked — the last gating decision.** Ticket 12 closes the Destination's open questions; the destination spec locks (see `map.md` §Destination: locked).
- Informed 12 by 02 (generation + stub packaging), 10 (commonTest lives in the runtime module, not published), 11 (benchmarks are test infra, not published).

## References
- ticket 02 (KSP generation; KompactAnnotations.kt as ksp-stubs)
- ticket 10 (commonTest harness — lives in the runtime module, not shipped)
- ticket 11 (benchmarks — `benchmark`, not shipped)
