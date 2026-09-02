# Code Review — Kompact KMP Bootstrap (Phase 1–3 + Ticket 13)

Two-axis review (Standards + Spec) over the staged bootstrap. All findings
resolved; see "Verification" for the green gates.

## Spec reviewer
- **VehicleTelemetry bit-14 field `isMalfunctioning`**: confirmed spec-faithful.
  Matches the PROMPT.md 2-byte VehicleTelemetry sketch (Enum + Int + Boolean)
  and the layout KDoc ("Is Engine Malfunction Active"). Used identically across
  `commonMain` / `jvmMain` / `iosMain` sources and both BCV ABI goldens
  (`kompact.api`, `kompact.klib.api`); `apiCheck` green. No change required.
- **`expect value class` (no `@JvmInline` in common)**: correct per PROMPT §1.
  `@JvmInline` JVM actual + plain iOS actual per Ticket 03 (zero-allocation
  wrapping on JVM; plain actual on iOS). No change.
- **`build() ByteArray` producer + zero-copy read views**: correct per PROMPT §1
  #2/#3 — producer serializes into a buffer, consumer wraps it with no heap
  allocation on read. No change.

## Standards reviewer
- **P0 — `readBits`/`writeBits` doc `1..64` vs `Int` (≤31-bit) cap**: FIXED.
  Aligned the `KompactRuntime` KDoc and `@KompactField(bitWidth = ...)` doc to
  the honest `1..31` ceiling (PROMPT §2 pins `readBits → Int`).
- **P0 — property test coverage gap at the Int-unsigned boundary**: FIXED.
  Widened `bitWidth` from `nextInt(1, 11)` to `nextInt(1, 32)` (exercises 1..31)
  and bound the value with `nextLong(0, 1L shl bitWidth).toInt()` so the max
  value at `bitWidth = 31` (`0x7FFFFFFF`) round-trips correctly (avoids the
  `nextInt(0, 1 shl 31)` overflow trap).
- **`Int`-backed enum (`batteryStatus: Int`)**: overridden by PROMPT §1
  (minimal shared surface, no `enum class` in common; Enum is modeled as Int).
  Spec-faithful; no change.
- **`bitOffset` + `bitWidth` field-pair annotation**: overridden by spec/PROMPT
  (Phase 1–3 fixed-width surface). No change.
- **`KompactRuntime` name**: overridden by PROMPT §1 (explicit symbol). No change.
- **Builder mutates receiver**: addressed via the read-view KDoc
  ("Read-only by design"); there is no mutable public write path on the view.
- **No KSP generator in Phase 2**: correct — hand-written `SOURCE`-retention
  annotations; KSP deferred (standard `ksp{}` for KMP is an unstable seam per
  Ticket 13). No change.
- **AAA test structure**: `Act` is captured in a local `val` before every
  `assertEquals` across `KompactRuntimeTest`, `KompactRuntimePropertyTest`, and
  `VehicleTelemetryTest`. Inspector reports 0 warnings.

## Resolved findings
| ID  | Axis       | Finding                                              | Resolution                                  |
|-----|------------|------------------------------------------------------|---------------------------------------------|
| P0  | Standards  | `readBits`/`writeBits` doc `1..64` exceeds Int cap   | `1..31`; property test covers near-cap      |
| P0  | Standards  | property test skipped the 31-bit boundary            | `nextInt(1,32)` + `nextLong(0, 1L shl w)`   |
| —   | Spec       | bit-14 field name                                    | `isMalfunctioning` confirmed spec-faithful  |

## Verification (all green)
- `./gradlew :kompact:apiDump :kompact:apiCheck :kompact:jvmTest :kompact:iosSimulatorArm64Test`
  → BUILD SUCCESSFUL (apiCheck green against regenerated goldens).
- `jvmTest` + `iosSimulatorArm64Test`: 26 tests, inspector 0 warnings.
- `:kompact:generatePomFileForJvmPublication :kompact:generateMetadataFileForJvmPublication`
  → valid `pom-default.xml` (`ch.trancee.kompact:kompact-jvm:0.1.0-SNAPSHOT`,
  Apache-2.0, `trancee`/Philipp Grosswiler, GitHub SCM) + Gradle module metadata.
- `compileKotlinIosArm64` ✓; iOS test execution via simulator (green).
