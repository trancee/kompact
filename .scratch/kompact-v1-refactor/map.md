---
Type: map
Status: active
Labels:
  - wayfinder:map
Destination: Kompact v1.0 — refactor `main` baseline (ticket-08 packed-Long *Result
  types + ADR-0001 mutable views) to the ratified v1 shape + re-lock ABI + gates
---

# Wayfinder Map: Kompact v1.0 refactor (ADR-0005/0006 implementation)

## Destination

Refactor `main`'s v1 baseline to the ratified v1.0 shape and re-lock ABI + gates:

- [ADR-0005](https://github.com/trancee/kompact/pull/56) tiered results: zero-alloc
  `readScalar*` hot path (Tickets 03/10 gates) + opt-in `decodeFull*`
  diagnostics tier carrying offset-bearing `DetailedDecodeError`.
- [ADR-0006](https://github.com/trancee/kompact/pull/56) immutable-by-default view
  codegen (`val` + builder + opt-in `Mutable*`).
- collapse `ByteResult`/`ShortResult` onto `IntResult` (Y2a — ≤32-bit-int shape);
  5 scalar result types remain (see Frontier: Y3 Float/Double unification).
- re-lock `kompact` + `kompact-ksp` ABI goldens; re-green `checkKotlinAbi` +
  `koverVerify` (100% line+branch) + `jvmTest` + iosArm64 on JVM.

## Decisions so far

- v1 shape = ADR-0005 (accept) + ADR-0006 (accept) + ticket-05 framing (keep).
  See the planning wayfinder `.scratch/kompact-v1/issues/01-03`.
- **Double 64-bit (A1):** keep NaN-payload tagging on the `DoubleResult` hot path
  (the only zero-alloc option); `decodeFull` opt-in path boxes freely — no Double
  fragility on the diagnostics tier.
- **Collapse (Y2a, shipped):** dropped `ByteResult`/`ShortResult` — widths 8 and 16
  are subsumed by `IntResult` via `ScalarType` (`readScalar` already returned
  `IntResult` for 8/16-bit reads; only the type names are removed, no accessor
  returned them). Five scalar result types remain
  (`IntResult`/`LongResult`/`FloatResult`/`DoubleResult`/`BooleanResult`).
- **Float/Double encoding (Y3, decided A):** keep distinct encodings. `FloatResult`
  already rides the ≤32-bit packed-Long (int) shape (32 bits fit the value field;
  the `ok` bit never collides with the failure band); `DoubleResult` keeps
  NaN-payload tagging — the only zero-alloc option for 64-bit (sentinel band
  mandatory: every `Long` is a valid `Double`). Folding both into one NaN scheme is
  a breaking encoding change with no allocation win (both already zero-alloc on
  success+failure), so rejected. Template/drift reduction is still met by one
  codegen template over the 5 (4-shape) classes — no encoding swap required.
- two-tier `decodeFull*` for all scalar shapes: done, green.
- failure-kind mapping unified into internal `decodeError(kind, rawCode)`: done,
  green (no public/ABI change).

## Done (TDD: red -> green -> gates, committed)

1. **`decodeFullInt/Long/Float/Double/Boolean` + `DetailedResult<T>` /
   `DetailedDecodeError(error, offset, rawCode)`** — `KompactResultDecodeFullTest`
   (10 AAA scenarios). Gates: `:kompact:jvmTest :kompact:checkKotlinAbi`
   `:kompact-ksp:checkKotlinAbi :kompact-ksp:test`; `spotlessKotlinCheck` clean.
   (commit `9b19f90`)
2. **Unify the 3 per-shape failure decoders into `decodeError(kind, rawCode)`**
   (internal; packed-Long encodings unchanged → zero-alloc hot path unaffected) —
   `KompactResultDecodeErrorTest` (5 AAA). Regression-green across the runtime
   suite; `:kompact:checkKotlinAbi` green (golden untouched).
   (commit `de68ead`)
3. **Collapse `ByteResult`/`ShortResult` onto `IntResult` (Y2a)** — ≤32-bit-int
   value-shape collapse: drop the two redundant names (no accessor returned them;
   `readScalar` already covered 8/16/32-bit). Anchor tests
   `intResult_coversFullByteRange`/`intResult_coversShortRange`; ABI goldens
   re-locked (`jvm`/`android`/`klib`); `JvmCoveragePinning` pin added for
   `detailedError`'s `UnknownEnumCode` arm (1 pre-existing uncovered branch
   surfaced + closed by the full `:kompact:check` gate). Gates green:
   `:kompact:check` (jvmTest + `checkKotlinAbi` + `koverVerifyJvm` 100%
   line+branch) + `:kompact-ksp:check` + `spotlessKotlinCheck`. Legacy flat
   `kompact/api/kompact.api` golden deleted (stale, not a kabi input).

## Frontier (next material decision — needs maintainer call)

- **(Z) ADR-0006 codegen refactor** (`var`→`val` + builder + opt-in `Mutable*`)
  — Y3 settled (decided A) and the result surface is stable (5 scalar types), so
  ADR-0006 is now **planned** (no longer deferred). Ordered TDD plan + gating
  decisions live in `.scratch/kompact-spec/issues/18-adr-0006-codegen-plan.md`.
  Blocked on three human-only decisions:
  (D1) accept removing write-through setters from the default view (source/MAJOR break),
  (D2) builder shape (`copy(...)` vs a `Builder` DSL),
  (D3) opt-in flag (`@KompactModel mutable: Boolean = false` vs a separate annotation).
- **Original `$code-review` directive** — delivered: two-axis (Standards + Spec)
  review over `5a78cc4..HEAD` (0 functional bugs; 4 HARD doc gaps closed; Judgement
  smells deferred). Re-runnable over the `main` baseline on request.
- **Merge PR #60** (`feat/v1-refactor-ratified-shape`) — blocked on explicit approval
  (AGATES: no merge without approval). Branch is green at `b1b6cc8`.

## Out of scope (this wayfinder)

- Merge of `feat/v1-refactor-ratified-shape` onto `main` (awaiting explicit approval).
- Changing the wire format (ticket 05 framing — ratified, keep).
- C emission / extra Native targets / watchOS (v1 scope — see
  `.scratch/kompact-v1/map.md`).

## Verification

- Baseline green pre-refactor (`:kompact:checkKotlinAbi` +
  `:kompact-ksp` ABI/kover(100%)/test) — see planning wayfinder ticket 05.
- Each step: red -> implement -> green -> `updateKotlinAbi`/`checkKotlinAbi` ->
  `:kompact-ksp:koverVerifyJvm`/`:kompact:jvmTest` -> `spotlessKotlinCheck`.
