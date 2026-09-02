---
Type: grilling
Status: open
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
- **Artifact shape**: single Kotlin Multiplatform library (common + platform actuals + KSP processor co-located) vs split into separate modules (runtime / annotations / processor / plugin). Tradeoff: single = simplest publication & consumption for v1; split = smaller client classpath (processor isolated from the runtime), but more modules to publish and version.
- **Published test/benchmark infra**: the commonTest tests (10) and the zero-alloc benchmarks (11) are `testImplementation` / `benchmark` deps and do **not** ship as published API. Confirm this is acceptable.
- **KSP processor packaging & coherence**: the processor emits `expect` value-class source into commonMain (02); the published artifact must keep generated sources + KSP processor + runtime coherent (the consumer applies KSP to the `com.example.kompact` annotations). A KSP-safe processor jar + `multiplatformPublication` (metadata + klib: iosArm64/iosSimulatorArm64) + `kotlinx binary-compatibility-validator`. (KMP publication wiring details — `multiplatformPublication`, klib targets, KSP-safe jar, Gradle plugin wrapper — can be gathered via a research subagent on request.)

Consequence: ticket 12's shape is the **last gating decision** before the destination spec locks and hands off to implementation.
