---
Type: feature
Status: proposed
Labels:
  - scope:codegen
  - scope:api
  - bc-break
  - wire
Blocked by:
  - "16 ADR-0006 codegen refactor (Y3 settled; var->val + builder + Mutable*)"
- "17 ADR-0006 acceptance (proposed -> accepted; D1/D2/D3 below)"
Decides:
  - "18 implementation slices below land after D1/D2/D3 are accepted"
---

# Ticket 18 — ADR-0006 codegen: immutable-by-default views + `Mutable*` + builder

## Goal
Implement ADR-0006 (immutable value-class views by default). The ratified v1 shape
(Y2a collapse, Y3 decided A) is on `feat/v1-refactor-ratified-shape`; ADR-0006 is the
follow-on codegen phase.

## Non-negotiable constraints
- Wire format byte-identical (encode/decode unchanged). ✓ ticket 07.
- Read hot path zero-alloc + checked getters unchanged (Ticket 03/08/10).
- `checkKotlinAbi` goldens re-locked after any codegen change.

## Current state (the foot-gun, per ADR-0001)
`ValueClassGenerator.buildActualProperty` (`var`, `mutable(true)`) emits a write-through
setter for every field. The committed example `VehicleTelemetry.kt` mirrors this
(`public actual var batteryStatus: Int` with `set(value) { writeBits(...) }`). Reads
go through `readCall` (raw `readBits`); writes through `writeCall` (`writeBits`).
`Companion.create` + `encode<ClassName>` already route construction through `KompactWriter`
(ticket 07 Decision 2 satisfied for construction).

## Ordered plan (TDD: red -> green -> refactor; one slice = one checkable test)

1. **Scaffold immutable-by-default codegen.** Add `mutable: Boolean = false` to the
   model spec; when `false`, `buildActualProperty` emits `val` (`.mutable(false)`) with
   NO setter; the `var`+setter stays gated behind `mutable == true`.
   - Test `process_mutableFalse_emitsValNoSetter` (red) → (green).
2. **`copy(...)` builder over `KompactWriter`.** When `mutable == false` (default), emit
   `fun copy(field = this.field, ...): ClassName = ClassName(encodeClassName(...))`.
   - Test: asserts `copy` signature + `encode` delegate (red -> green).
   - Round-trip test on the example: `copy(speed = x)` encodes the new value, preserves
     other fields' bits.
3. **`Mutable<ClassName>` opt-in sibling.** When `mutable == true`, emit
   `@KompactModelMutable MutableClassName(val raw: ByteArray)` with write-through `var`s
   mirroring today's behavior (the explicit escape hatch).
   - Test: asserts the sibling type + setters exist (red -> green).
4. **Remove the foot-gun from the default view.** Assert no `set` accessor on the default
   view (Decision 4 + ADR-0004 ticket-11: remove silent-truncation setters).
   - Test: `process_defaultView_hasNoWriteThroughSetter` (red -> green).
5. **Mirror the example.** Regenerate `VehicleTelemetry.kt` (common + jvm + ios) to the
   new default shape (`val` + `copy` + `MutableVehicleTelemetry` opt-in).
   NOTE: committed example mirrors the generator (not a task artifact) — update in lockstep
   with slices 1–4 so the generation tests + example never diverge.
6. **Gates.** `:kompact:check :kompact-ksp:check` (jvmTest + both `checkKotlinAbi`) +
   `spotlessKotlinCheck`, `--rerun-tasks`. `updateKotlinAbi`/`checkKotlinAbi` in SEPARATE
   gradlew invocations (combined => implicit-dependency validation error).
7. **Commit + push** `feat/adr-0006-immutable-views` (feature branch; NOT default).

## Gating decisions (human — ADR-0006 is `proposed`; these are human-only inputs)
- **D1**: remove write-through setters from the default view (source/MAJOR break;
  API is SNAPSHOT today, v1 boundary). Accept? (Recommendation: yes.)
- **D2**: builder shape on the default view — `copy(field = …)` (Kotlin-idiomatic,
  minimal surface) vs a dedicated `Builder` DSL. (Recommendation: `copy(...)`.)
- **D3**: opt-in flag on `@KompactModel` — `mutable: Boolean = false` (default immutable)
  vs a separate annotation. (Recommendation: `mutable: Boolean = false`.`)

## Risks
- Source break for consumers using `view.field = x` on the default view; migration =
  `copy(field = x)` or opt-in `Mutable<ClassName>`. Off read hot path.
- Generated surface grows by one `Mutable<ClassName>` type per model when requested;
  mitigation: emit only when `mutable == true` (ADR-0006 Risk).

## References
- `docs/adr/0006-immutable-default-models.md` (governing ADR)
- `docs/adr/0001-mutable-view-classes-with-write-through-setters.md` (superseded)
- `.scratch/kompact-spec/issues/07-write-builder-interface.md` (KompactWriter Decision 2)
- `kompact-ksp/.../gen/ValueClassGenerator.kt` (current generator)
