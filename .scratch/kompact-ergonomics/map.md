Status: wayfinder:map
Type: wayfinder:map

# Map: kompact ergonomics

> **Wayfinding operations** (this repo's local-markdown tracker):
> map is this file; child tickets live at
> `issues/NN-<slug>.md`, numbered from `01`, with a `Type:` line
> (`research` / `prototype` / `grilling` / `task`) and a `Status:` line
> (`claimed` / `resolved`). Blocking is a `Blocked by: NN, NN` line near
> the top of each ticket. The frontier is the open, unblocked, unclaimed
> children, first by number wins. See `docs/agents/issue-tracker.md`.

## Destination

Reach a v1 consumer surface of `:kompact` that is as easy to use as
possible with the least impact on complexity. "Easy to use" means a
newcomer reading the README can write a packed frame, read it back with
typed results, and recover from malformed input without learning the
internal packed-Long encoding. "Least impact on complexity" means we
simplify the surface without removing the v1 capability set the locked
spec commits to: signed/unsigned scalars 1–64, booleans, IEEE-754
floats, length-delimited strings/blobs/nested/repeated, the seven
specialized result value classes, and the `@KompactModel` / `@KompactField`
schema annotations.

The blast radius is everything in this repository except the locked
v1 spec tickets 01–13 (those carry user-decided scope and may not be
reopened by this effort — see `Out of scope`). The public ABI is free
to change; the library has not been released. Commits land on
`feat/laguna` behind the BCV gate (`apiCheck` on macOS, `jvmTest` on
Linux), with the klib golden regenerated via the
`regen-goldens.yml` workflow.

## Notes

- **Consult first**: `docs/architecture.md` (the current design rationale
  and the packed-Long layout that several tickets challenge), the v1
  spec map at `.scratch/kompact-spec/map.md` (so we don't accidentally
  drift from the locked decisions), and the recent code-review report
  on `feat/laguna` (the user-facing review that surfaced several of
  these threads; see the prior review history for the P1/P2/P3
  findings).
- **Standing preference**: when a ticket resolves with a rename or
  refactor that breaks the public ABI, regenerate the goldens
  (`apiCheck` + `regen-goldens.yml`) in the same commit. Do not leave
  the gate red between commits.
- **Tone**: the user asked us to "challenge everything." The route
  favors the smallest simplification that makes the surface easier,
  even at the cost of an internal rename. Do not preserve complexity
  for symmetry's sake.
- **What "resolved" means here**: a ticket is resolved when the
  decision is recorded on the ticket under `## Answer`, the ticket
  status is `resolved`, the map's Decisions-so-far is updated with
  a one-line gist + the ticket link, and any newly-surfaced ticket
  has been created and wired. A follow-up implementation commit is
  expected but is not part of resolving the decision ticket itself.

## Decisions so far

<!-- one line per closed ticket, enough to judge relevance, then zoom
     the link for the detail. Append here as tickets resolve. -->

- [Data clump → `ScalarType` value class](issues/01-data-clump-scalartype.md): `value class ScalarType(bitWidth, signed)` with named companion constants (`INT_8`…`UINT_64` + `BOOL` + `of(w, signed)`); adopted by `readScalar`, `readScalarLong`, and `writeScalar`. The reader split (`readScalar` 1..32 / `readScalarLong` 1..64) is kept — the value class doesn't change the two `Int`/`Long` result lanes. No enum axis: enums are read via `readScalar` and inspected for `UnknownEnumCode` on the result. Unblocks tickets 02, 04, 09.
- [`readScalarLong` → `readScalarAsLong`](issues/02-readscalarlong-naming.md): pure rename; the `As` prefix makes the result type (`LongResult`) unambiguous and matches Kotlin's "as" pattern (`getOrThrow`, `as`, `asReversed`). `readScalar` (implicit-`Int` variant) stays. Writer side unchanged. Doc edit in `docs/api-reference.md#checked-typed-read-accessors`; tutorial not affected.
- [Throw helpers mirror the decode pattern](issues/03-throw-helper-naming.md): rename `throwSmallFailure` → `throwDecodeErrorFromSmallBits`, `throwLongFailure` → `throwDecodeErrorFromLong`, `throwDoubleFailure` → `throwDecodeErrorFromDouble`. Three-helper split kept (each picks the right decoder). Internal-only; no public-API change, no BCV regen, no test changes. 3 helper declarations + 14 call sites (7 in `jvmMain` + 7 in `iosMain`).
- [Result ergonomics layer: all three additions](issues/04-result-ergonomics-layer.md): (1) `…OrThrow` function-level wrappers on `KompactRuntime` (5 new public fns: `readBoolOrThrow`, `readScalarOrThrow`, `readScalarAsLongOrThrow`, `readFloatOrThrow`, `readDoubleOrThrow`); (2) `Kompact.Result` namespace re-exporting the seven result value classes as `typealias` members; (3) `getOrElse` / `map` extension functions on the seven result classes (14 new fns) mirroring stdlib's `Result<T>`. All additive. The seven specialized result value classes and the four-member public surface stay unchanged. Unblocks ticket 05.
- [Framing: full ergonomics pair + count-prefix `…OrThrow`](issues/05-readnested-typed-result.md): add `KompactFraming.readNestedOrThrow(raw, off, prefixBitWidth): Pair<Int, Int>` (throws on overrun), `KompactFraming.readNested(raw, off, prefixBitWidth): NestedRegionResult` (typed result), and `KompactFraming.readLengthPrefixOrThrow(raw, off, bitWidth): Int` (count-prefix throw-on-failure). `NestedRegionResult` is a new `value class NestedRegionResult(val startBit: Int, val bitLength: Int)` (zero-alloc, ticket 03 pattern) with `isSuccess`/`isFailure`/`error`/`getOrThrow()` and `getOrElse`/`map` extensions, plus `Kompact.Result.NestedRegion` typealias. The existing `nestedRegionOrNull` demotes to `internal inline` (hot-path internal; the new functions delegate to it). 6 source consumers in `KompactFramingTest.kt` + `KompactWriterTest.kt` switch to the new public form.
- [Annotations hidden behind `@RequiresOptIn`](issues/06-annotation-visibility.md): `@KompactModel` and `@KompactField` move from the public `ch.trancee.kompact.runtime` package to a preview `ch.trancee.kompact.annotations` package, gated by a `@KompactPreview` `@RequiresOptIn(level = WARNING)` annotation. Consumers opt in with `@OptIn(KompactPreview::class)`. Reversible when the locked `:kompact-ksp` processor (ticket 12) ships. `KompactFieldV1SurfaceTest` moves with the annotations and opts in. `VehicleTelemetry` example updates the import path and opts in. No runtime/ABI impact (annotations are SOURCE-retained; `@RequiresOptIn` is compile-time only). No BCV regen.
- [`VehicleTelemetry` example: realign to public API](issues/07-vehicletelemetry-alignment.md): the example's getter bodies use the new public checked accessors (`readScalar(raw, bitOffset, type: ScalarType).getOrThrow()` for the 4-bit and 10-bit fields, `readBool(raw, bitOffset).getOrThrow()` for the 1-bit flag). The annotations stay (via `@file:OptIn(KompactPreview::class)` from ticket 06). The example matches the tutorial (`docs/getting-started.md`) and the API reference. The codegen-output reference (raw `readBits` shape) moves to prose in `docs/architecture.md`. Test (`VehicleTelemetryTest`) is unchanged: the getters still return the same primitives; the wire bytes (`0xA5 0x40`) and round-trip values (5, 10, true) stay.
- [CI: Linux catches JVM API drift](issues/08-ci-ergonomics.md): fold `:kompact:jvmApiCheck` into the existing `jvm-test` job (Ubuntu, JDK 21). The job is renamed `jvmTest + jvmApiCheck (Linux)` and runs both tasks (`:kompact:jvmTest :kompact:jvmApiCheck`); the two tasks share the JVM compile, so the wall-clock cost is dominated by the API comparison. The macOS `api-check` job stays as the final gate (full `apiCheck` = `jvmApiCheck` + `klibApiCheck`). A Linux contributor who breaks the JVM API now gets the red on the PR *before* the macOS job is scheduled. `regen-goldens.yml` stays on `feat/laguna` (manual `workflow_dispatch` via the GitHub UI).
- [Test file split: per-accessor, all in one commit](issues/09-test-file-split.md): seven new test files replace the 441-line `KompactRuntimeCheckedReadTest.kt`: `KompactRuntimeReadBoolTest`, `KompactRuntimeReadScalarTest`, `KompactRuntimeReadScalarAsLongTest` (renamed from `…ReadScalarLong…` per ticket 02), `KompactRuntimeReadFloatTest`, `KompactRuntimeReadDoubleTest`, `KompactRuntimeBoundsHardeningTest` (F-002/F-003), and `KompactFramingReadNestedTest` (new from ticket 05). Each per-accessor file tests the accessor + its `…OrThrow` companion + the matching result class's `getOrElse`/`map` extensions. Total: ~63 tests, ~630 lines; the largest file is ~220 lines (under D9's 300-line default). The implementation commit moves the 46 existing tests to the new files (with the new `ScalarType` / `readScalarAsLong` signatures from tickets 01 + 02), adds the new tests for the new public symbols, and deletes the old file.
- [Docs layer: add Dokka, keep tutorial/README/ci/index as-is](issues/10-docs-structure-review.md): add the `dokka` Gradle plugin to `:kompact` (Markdown output via `dokkaGfm`, output to `docs/api/`) and replace the per-function tables in `docs/api-reference.md` with a pointer to the generated output. The narrative content (the "Long" suffix explanation, the writer's growable-buffer note, the cross-references to the architecture doc) stays hand-maintained in `docs/api-reference.md` as the curated overview. The tutorial (`docs/getting-started.md`), `docs/ci.md`, and `docs/README.md` stay as hand-maintained pages. The implementation is a follow-up commit (this ticket records the *decision*; the build wiring is separate, after the tickets 01 + 02 + 04 + 05 + 07 land).
- [KompactWriter.bitCursor: demote to `internal`](issues/11-bitcursor-visibility.md): a repo-wide grep confirms `bitCursor` is read+written only inside `KompactWriter.kt`'s own methods; no test, example, or consumer reads it. The documented "nested/repeat assembly" rationale is satisfied by `writeNested`/`writeRepeated` (compute-first, no back-patch); the future KSP processor won't consult the cursor. Demote `bitCursor` to `internal` (the writer's internal bookkeeping); reject the speculative `position()`/`mark()`/`seek()` richer API. The klib golden (regenerated on macOS) will no longer list it.

## Frontier

> All 11 design tickets (01–11) are resolved. The wayfinder frontier is
> **exhausted**. The next phase is the **implementation commit** — the single
> atomic commit that applies tickets 01 + 02 + 04 + 05 + 06 + 11 to the source,
  splits the tests per ticket 09, and regenerates the goldens. It is tracked
  by the implementation todo (below), not by a wayfinder ticket. The klib
  golden regen is the final macOS-gated step (dispatch `regen-goldens.yml`
  via the GitHub web UI).

## Implementation

> The implementation commit is **not** a wayfinder ticket — it is the
> mechanical realization of tickets 01 + 02 + 04 + 05 + 06 + 11 (decided)
> + ticket 09 (split plan). One atomic commit. Tracked by the `todo`
> tool list, not by a ticket file.

The 9-step plan from ticket 09's `## Answer`, with the source-edit
precedence that the decisions impose:

1. **Source: scalars** — ticket 01 (`ScalarType` value class +
  companion constants `INT_8`…`UINT_64` + `BOOL` + `of`); ticket
  02 (`readScalarLong` → `readScalarAsLong`). `readScalar(raw, off,
  type: ScalarType): IntResult`, `readScalarAsLong(raw, off, type:
  ScalarType): LongResult`, `KompactWriter.writeScalar(type, value)`.
  The two Int/Long result lanes are unchanged.
2. **Source: result ergonomics** — ticket 04 (`readBoolOrThrow` /
  `readScalarOrThrow` / `readScalarAsLongOrThrow` / `readFloatOrThrow`
  / `readDoubleOrThrow`; `getOrElse`/`map` extensions on the 7 result
  classes; `Kompact.Result` namespace). Ticket 03 pattern (zero-alloc
  value-class wrapping a Long) applies to `NestedRegionResult`.
3. **Source: framing** — ticket 05 (`readLengthPrefixOrThrow`,
  `readNested`, `readNestedOrThrow`; `nestedRegionOrNull` → internal;
  `NestedRegionResult` value class + `isSuccess`/`isFailure`/`error`/
  `getOrThrow()` + `getOrElse`/`map`; `Kompact.Result.NestedRegion`).
4. **Source: writer** — ticket 11 (`bitCursor` → `internal`; drop the
  "Exposed so nested/repeat assembly..." KDoc). `writeScalar` takes
  `ScalarType`.
5. **Source: annotations** — ticket 06 (`@KompactModel`/`@KompactField`
  → `ch.trancee.kompact.annotations`; `@KompactPreview`
  `@RequiresOptIn(WARNING)`; `KompactFieldV1SurfaceTest` opts in).
6. **Source: example** — ticket 07 (`VehicleTelemetry` getters use
  `readScalar(raw, off, type).getOrThrow()` / `readBool(raw, off)
  .getOrThrow()`; annotations opt in).
7. **Tests** — ticket 09 (7 files, ~63 tests; relocate 46 existing +
  add `…OrThrow` / `getOrElse` / `map` / framing tests; update tutorial
  + example tests for the new signatures; delete the old file).
8. **Golden** — regen JVM golden (`apiDump`) on Linux (host-capable);
  regen klib golden on macOS via `regen-goldens.yml` web-UI dispatch
  (Linux cannot infer it). Verify `jvmApiCheck` + `jvmTest` green on
  Linux; `apiCheck` green on macOS.
9. **Commit + push** — Conventional Commit ("refactor:" scopes the
  signature rename + surface addition); push to `feat/laguna`; CI green
  (Linux `jvmTest + jvmApiCheck` + macOS `apiCheck`).

Sizing: ~6 source edits (3 files × 2 platforms for the actuals) + 1
annotation file + 1 example (3 platform actuals) + 7 test files + 2
goldens. The source edits are mechanical (signature changes + additive
declarations); the test files are the bulk (rewrite the read tests + new
ergonomics tests). The klib golden is the final macOS-gated step.

## Not yet specified

<!-- in-scope fog not yet sharp enough to ticket. Revisit after the
     frontier advances; graduate each patch into its own ticket when it
     becomes specifiable. -->
<!-- 2026-09-05: the only fogged item — "test data-class proliferation" —
     graduated to ticket 09 (resolved). The frontier is exhausted; all
     11 design tickets decided. The implementation commit (applying
     tickets 01 + 02 + 04 + 05 + 06 + 11 to the source + splitting
     tests per ticket 09 + regenerating goldens) is the next phase,
     tracked by the implementation todo below, not by a wayfinder
     ticket. Revisit this section when the implementation surfaces
     new in-scope fog. -->
<!-- (empty)

## Out of scope

- **Reopening v1 spec tickets 01–13.** The spec map at
  `.scratch/kompact-spec/map.md` is locked (see its "Destination:
  locked" section). The signed/unsigned scalar range, the LSB-first
  bit order, the length-prefix contract, the value-class
  representation, the runtime error model, the versioning rules,
  the testing model, the perf-evidence plan, the module split, and
  the KMP publication wiring are all settled decisions. If a
  ticket's resolution *exposes* a need to reopen one, that becomes
  a fresh wayfinder effort, not a follow-up here.
- **The `KompactProcessor` KSP validator** (Ticket 06, deferred).
  Building a code generator is its own effort; the annotation
  visibility decision in ticket 6 either confirms or rules that out
  as a follow-up.
- **C / C99 header generation, BLE transport, iOS Swift / Objective-C
  API generation** — explicitly out of scope in the v1 spec map
  ("Out of scope" section, same source).
- **Changing the `kotlin.mpp.applyDefaultHierarchyTemplate=false`
  wiring** in `gradle.properties` or the intermediate `iosMain`
  source set. The intermediate source set is what makes
  expect/actual value classes for iOS work; it is part of the
  locked v1 publication wiring and not in this effort's blast
  radius.
- **Replacing `@JvmInline` on the JVM actuals.** The hand-written
  common API prohibition (PROMPT §1) does not apply to generated
  JVM actuals, per the v1 spec map's `@JvmInline` reconciliation
  note. The current shape is correct and not in this effort's scope.
