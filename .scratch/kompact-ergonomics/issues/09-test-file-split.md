Type: grilling
Status: resolved
Blocked by: —  (note 2026-09-05: tickets 01, 02, 04, and 05 settled the runtime + framing public surface. New public symbols to test: `readScalar(raw, off, type: ScalarType)` (ticket 01) + `readScalarAsLong` (ticket 02 rename) + `…OrThrow` wrappers (tickets 04 + 05) + `Kompact.Result` namespace (ticket 04) + `getOrElse`/`map` extensions (tickets 04 + 05) + `NestedRegionResult` (ticket 05) + `KompactFraming.readNested` / `readNestedOrThrow` / `readLengthPrefixOrThrow` (ticket 05). The split boundary (per-accessor vs. per-result-kind) should plan for these new symbols. If splitting per-accessor, the long-band test file is `KompactRuntimeReadScalarAsLongTest.kt`; the framing test file is `KompactFramingReadNestedTest.kt`.)
# 09 — Test file split: `KompactRuntimeCheckedReadTest` is 441 lines

## Question

`kompact/src/commonTest/kotlin/ch/trancee/kompact/runtime/KompactRuntimeCheckedReadTest.kt`
is **441 lines** of `@Test` methods covering seven distinct
checked read accessors:

- `readBool`
- `readScalar` (widths 8, 16, 32; signed + unsigned)
- `readScalarLong` (widths 32, 64; signed + unsigned; the 1..31
  overlap with `readScalar` is intentional per the v1 spec)
- `readFloat`
- `readDouble`
- Boundary hardening tests (F-002 / F-003)

CONSTITUTION.md rule **D9** says:

> Maintained source/test file D <=300 lines, R <=500; split by
> responsibility/layer/platform.

441 lines exceeds the 300-line default and is 41 lines under the
500-line hard limit. The file is trending toward the limit as
new accessors (e.g. the `ScalarType`-shaped `readScalar` from
ticket 01) are added. A new maintainer opening the test
directory sees a wall of `@Test` methods in one file.

The decision: do we split the file now, and if yes, by what
boundary?

- **By accessor** — one file per accessor
  (`KompactRuntimeReadBoolTest`,
  `KompactRuntimeReadScalarTest`, …). Maximum clarity; the
  file name announces the surface. Eight files (one per
  accessor + one for boundary hardening).
- **By width band** — `ReadScalarIntTest` (1..32),
  `ReadScalarLongTest` (1..64), `ReadFloatTest`, `ReadDoubleTest`,
  `ReadBoolTest`. Mirrors the result type split (IntResult vs
  LongResult).
- **By result kind** — one file per result value class
  (`ReadBoolTest`, `ReadScalarIntTest`, `ReadScalarLongTest`,
  `ReadFloatTest`, `ReadDoubleTest`, `ReadBytesTest`,
  `ReadShortTest`). The result kind is the consumer-facing
  surface; tests grouped by the kind of value they exercise
  is the most natural navigation.
- **Don't split yet** — wait for ticket 01 to land (the
  accessor surface may change) and split in the same commit
  that introduces the new accessors. The 441-line file stays
  as-is until then.

## Context for the claiming session

- `kompact/src/commonTest/kotlin/ch/trancee/kompact/runtime/KompactRuntimeCheckedReadTest.kt`
  — the 441-line file.
- `kompact/src/commonTest/kotlin/ch/trancee/kompact/runtime/`
  — the other test files in the same package:
  - `KompactFieldV1SurfaceTest.kt` (annotation compile-time contract)
  - `KompactFramingTest.kt`
  - `KompactResultCanonicalizationTest.kt`
  - `KompactResultTest.kt`
  - `KompactRuntimeLongBitsTest.kt` (raw primitive, not the
    checked accessors)
  - `KompactRuntimePropertyTest.kt` (round-trip + property-based)
  - `KompactRuntimeTest.kt` (raw primitive)
  - `KompactWriterTest.kt`
  - `GettingStartedTest.kt` (the tutorial's executable journey)
- CONSTITUTION.md rule D9 — the 300/500-line limit.
- The v1 spec map's ticket 10 ("Testing model") requires
  commonTest to cover all four categories (round-trip,
  property-based, cross-version compat, zero-alloc
  assertion). The split must preserve the category coverage;
  no `@Test` method should be lost.

## Open sub-questions

1. **What's the right boundary for the split?** The four
   options above are sketched. Is there a fifth (e.g. by
   accessor's `signed`/`unsigned` axis)?
2. **Should the split wait for ticket 01 (data clump /
   `ScalarType`)?** The accessor surface may change, and
   splitting now means the file names will need to be
   renamed in a follow-up commit. Co-splitting with ticket
   01 is cheaper.
3. **The boundary hardening tests (F-002 / F-003)** — are
   these per-accessor or per-runtime? They exercise
   `KompactRuntime.fits` and the bounds-check code paths
   across accessors, so they don't fit cleanly into a
   per-accessor file. A separate `KompactRuntimeBoundsHardeningTest`
   is the natural home.
4. **The `KompactRuntimeLongBitsTest` and `KompactRuntimeTest`**
   — these are *raw primitive* tests (not the checked
   accessors). Should they be merged into the new
   per-accessor files, or kept separate? The raw primitives
   are a different surface (no bounds check, no typed
   result) and a separate concern; keep separate is the
   simpler answer.

## What "resolved" looks like

- The chosen split boundary is recorded under `## Answer`
  with the list of new file names.
- The boundary-hardening test's new home is recorded.
- The decision is coordinated with ticket 01 (if 01's
  accessor surface changes, the new file names are noted
  in 01's resolution so the implementation commit can split
  and rename in one pass).

## Answer

**Decision: split by accessor, all tests in this one commit. Seven
new test files replace `KompactRuntimeCheckedReadTest.kt`
(which is deleted). Each file tests one accessor + its
`…OrThrow` companion + the matching result value class's
`getOrElse`/`map` extensions. The framing nested tests go in
a new `KompactFramingReadNestedTest.kt`.**

### The seven new files

1. **`KompactRuntimeReadBoolTest.kt`** — `readBool` (4 existing
   tests with updated signature if needed) + `readBoolOrThrow`
   (1 new test: bounds error throws) + `BooleanResult.getOrElse`
   + `BooleanResult.map` (2 new tests). ~7 tests, ~60 lines.
2. **`KompactRuntimeReadScalarTest.kt`** — `readScalar` (24
   existing tests with the new `type: ScalarType` signature
   from ticket 01) + `readScalarOrThrow` (1 new test) +
   `IntResult.getOrElse` + `IntResult.map` (2 new tests). ~27
   tests, ~220 lines.
3. **`KompactRuntimeReadScalarAsLongTest.kt`** — `readScalarAsLong`
   (renamed from `readScalarLong` per ticket 02; ~10 existing
   tests with the new `type: ScalarType` signature) +
   `readScalarAsLongOrThrow` (1 new test) +
   `LongResult.getOrElse` + `LongResult.map` (2 new tests; the
   `LongResult` sentinel-band failure case is a good place
   to assert that `getOrElse` returns the fallback for the
   sentinel). ~13 tests, ~110 lines.
4. **`KompactRuntimeReadFloatTest.kt`** — `readFloat` (existing
   tests) + `readFloatOrThrow` (1 new test) +
   `FloatResult.getOrElse` + `FloatResult.map` (2 new tests;
   the NaN-payload failure case is a good place to assert the
   fallback). ~4 tests, ~40 lines.
5. **`KompactRuntimeReadDoubleTest.kt`** — `readDouble` (existing
   tests) + `readDoubleOrThrow` (1 new test) +
   `DoubleResult.getOrElse` + `DoubleResult.map` (2 new tests).
   ~4 tests, ~40 lines.
6. **`KompactRuntimeBoundsHardeningTest.kt`** — F-002 / F-003
   cross-accessor boundary tests (4 existing tests; no new
   tests, just relocation). ~80 lines.
7. **`KompactFramingReadNestedTest.kt`** (new file) — the
   nested-region tests for the new `readNested` /
   `readNestedOrNull` (now `internal`) / `NestedRegionResult`
   + `NestedRegionResult.getOrElse` / `NestedRegionResult.map`
   / `readLengthPrefixOrThrow` (per ticket 05). ~6 tests,
   ~80 lines. The existing `KompactFramingTest.kt` stays
   (covers `readLengthPrefix` / `writeLengthPrefix` and the
   existing nested-region tests; the new file picks up the
   new public surface).

Total: 7 new files, ~63 tests, ~630 lines. The largest single
file (`KompactRuntimeReadScalarTest.kt` at ~220 lines) is under
CONSTITUTION D9's 300-line default and well under the 500-line
hard limit.

### Migration map (existing tests → new files)

| Existing test (line in `KompactRuntimeCheckedReadTest.kt`) | New file |
| --- | --- |
| `readBool_*` (4 tests, lines ~20–35) | `KompactRuntimeReadBoolTest.kt` |
| `readScalar_width8_*` (4 tests) | `KompactRuntimeReadScalarTest.kt` |
| `readScalar_width16_*` (4 tests) | `KompactRuntimeReadScalarTest.kt` |
| `readScalar_width32_*` (8 tests) | `KompactRuntimeReadScalarTest.kt` |
| `readScalarLong_width64_*` (renamed to `readScalarAsLong` per ticket 02; ~10 tests) | `KompactRuntimeReadScalarAsLongTest.kt` |
| `readFloat_*` (existing tests) | `KompactRuntimeReadFloatTest.kt` |
| `readDouble_*` (existing tests) | `KompactRuntimeReadDoubleTest.kt` |
| F-002 / F-003 boundary tests (4 tests) | `KompactRuntimeBoundsHardeningTest.kt` |

The new `…OrThrow` and `getOrElse` / `map` tests (one per
accessor / result kind) are added fresh, not migrated from
existing tests. The `Kompact.Result` namespace typealiases
are tested via the existing per-accessor tests (a single
`assertSame(BooleanResult, Kompact.Result.Boolean)` per
result kind, in the per-accessor file).

### What this does NOT change

- **The raw-primitive tests** (`KompactRuntimeTest.kt`,
  `KompactRuntimeLongBitsTest.kt`, `KompactRuntimePropertyTest.kt`)
  stay as-is. The split is for the *checked* accessors, not
  the raw primitives. The raw primitives have their own
  well-scoped test files.
- **`KompactResultTest.kt` and `KompactResultCanonicalizationTest.kt`**
  stay as-is. The new `getOrElse` / `map` extensions are
  tested in the per-accessor files (where the failure
  mode is most relevant), not in the result-class test
  files (which test the value class mechanics).
- **`KompactFieldV1SurfaceTest.kt`** stays as-is (tests the
  annotation compile-time contract from ticket 06).
- **`GettingStartedTest.kt`** stays as-is (the tutorial's
  executable journey; the `ScalarType` signature update
  per ticket 01 is the only edit needed there).
- **`VehicleTelemetryTest.kt`** stays as-is (the example's
  test; the getters use the new public API per ticket 07,
  but the test's write side is raw `writeBits`, which is
  unchanged).

### Propagation

- **`docs/ci.md`** — no change (the test files are
  implementation detail; the CI shape from ticket 08 already
  covers them).
- **Ticket 10** (Docs layer structure review) — no change
  (tests are not part of the user-facing docs layer).
- **The implementation commit** is one mechanical refactor
  + additive test commit:
  1. Apply tickets 01 + 02 + 04 + 05 + 07's source changes
     (the runtime / writer / framing / result / annotation
     public surfaces + the example realignment).
  2. Update the 46 existing test methods to use the new
     signatures (`readScalar(raw, off, type: ScalarType)`,
     `readScalarAsLong`, etc.).
  3. Create the 7 new test files (6 per-accessor runtime +
     1 framing).
  4. Add the new test methods (`…OrThrow`, `getOrElse`,
     `map`, `NestedRegionResult` extensions, framing nested).
  5. Delete the old `KompactRuntimeCheckedReadTest.kt`.
  6. Update `GettingStartedTest.kt` to use the new
     `ScalarType` signature (the tutorial's wire bytes
     `0xA5 0x40` stay pinned).
  7. Update `VehicleTelemetryTest.kt` if the example's
     getter shape changes (per ticket 07; the test reads
     via the getters, so the assertions stay the same).
  8. Regenerate the klib golden on macOS via the
     `regen-goldens.yml` workflow.
  9. Push to `feat/laguna`; CI should stay green.

## Comments
