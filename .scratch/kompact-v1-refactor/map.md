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
- collapse the 7 `*Result` types to value-shape results (Major — staged to v1.x,
  see Frontier).
- re-lock `kompact` + `kompact-ksp` ABI goldens; re-green `checkKotlinAbi` +
  `koverVerify` + `test` on JVM + iosArm64.

## Decisions so far

- v1 shape = ADR-0005 (accept) + ADR-0006 (accept) + ticket-05 framing (keep).
  See the planning wayfinder `.scratch/kompact-v1/issues/01-03`.
- **Double 64-bit (A1):** keep NaN-payload tagging on the `DoubleResult` hot path
  (the only zero-alloc option); `decodeFull` opt-in path boxes freely — no Double
  fragility on the diagnostics tier.
- **Collapse staged (Y1):** keep the 7 typed `*Result` for v1.0; add the two-tier
  diagnostics path on top. Deferral is reversible (staging per ADR-0005 § Risks).
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

## Frontier (next gate — materially different, needs maintainer call)

- **(Y2) Collapse the 7 `*Result` types → value-shape results (ADR-0005 §2.3)**
  — Major public-API break: removes `ByteResult`/`ShortResult`/`IntResult`/
  `LongResult`/`FloatResult`/`DoubleResult`/`BooleanResult`; rewrites
  `ValueClassGenerator.kt` emit + generated `VehicleTelemetry`; re-locks
  `kompact` + `kompact-ksp` ABI; rewrites the 8 result test files.
  **Shape sub-fork:** (Y2a) widen views to `Int`/`Long` + cast (loses
  per-kind typing) vs (Y2b) keep typed wrappers, share one impl template (kills
  `expect`/`actual` drift but does not reduce the type count).
  STAGED to v1.x per Y1; **pull forward on request**.
- **(Z) ADR-0006 codegen refactor** (view `var`→`val`+builder+opt-in `Mutable*`)
  — separate ADR; gated on the result-type surface being settled (decide Y2
  first).

## Out of scope (this wayfinder)

- Pushing `feat/v1-refactor-ratified-shape` (awaiting approval).
- Changing the wire format (ticket 05 framing — ratified, keep).
- C emission / extra Native targets / watchOS (v1 scope — see
  `.scratch/kompact-v1/map.md`).

## Verification

- Baseline green pre-refactor (`:kompact:checkKotlinAbi` +
  `:kompact-ksp` ABI/kover(100%)/test) — see planning wayfinder ticket 05.
- Each step: red -> implement -> green -> `updateKotlinAbi`/`checkKotlinAbi` ->
  `:kompact-ksp:koverVerifyJvm`/`:kompact:jvmTest` -> `spotlessKotlinCheck`.
