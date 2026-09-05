Type: research
Status: resolved

# 06 — `@KompactModel` / `@KompactField`: ship a processor, hide them, or keep as-is?

## Question

The annotations `@KompactModel` (class-target, source-retained) and
`@KompactField(bitOffset, bitWidth, lengthPrefixWidth, isNested,
repeatCountWidth, enumWidth, defaultValue, isVersionField)` are
declared in `kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactAnnotations.kt`
and pinned by `KompactFieldV1SurfaceTest` (compile-time contract that
all eight members exist). They are documented in
`docs/api-reference.md#annotations` and referenced in the bundled
`VehicleTelemetry` example.

The v1 spec map's ticket 02 ("Code generation — KSP 2.3.9+")
records the *decision* that a KSP processor will generate the
view-class bodies from the annotations, but the processor has
**never been implemented** in this repository. The annotation
surface therefore sits in the public API with no consumer
producers: a newcomer reading the README sees the annotations,
adds them to a model, and gets nothing (the annotations are
source-retained; no runtime effect; no codegen).

Three options to grill:

1. **Ship a minimal KSP processor** in a new `:kompact-ksp` module
   (per ticket 12, "Module split & publication — split modules,
   KSP-safe jar") that generates the `expect value class` +
   `@JvmInline actual` / plain `actual` from a `@KompactModel`
   value class declaration with `@KompactField` properties. This
   makes the annotation surface a real ergonomic affordance: a
   consumer writes the model class, the KSP processor generates
   the read/write bodies, and the runtime's `KompactRuntime` /
   `KompactWriter` calls stay zero-alloc.
2. **Hide the annotations** from the public API until the
   processor lands. Move them to an `internal` package
   (`ch.trancee.kompact.annotations.internal`) and gate the
   public surface on `@RequiresOptIn` or a `Beta` annotation so
   consumers don't accidentally use them. The runtime stays
   usable by hand-written `KompactRuntime.readBits` calls (the
   way `VehicleTelemetry` works today).
3. **Keep as-is.** The annotations are documented as
   compile-time-only schema metadata; a newcomer reading the
   API reference sees the note "no processor ships in this
   repository today" and either writes a hand-written view class
   or waits for the processor. Accept the documentation
   friction.

The decision: which of the three, and if (1), what is the
minimum viable processor scope (just `readBits`-shaped getters,
or full Ticket 04+05+07 generation including length prefixes and
nested)?

## Context for the claiming session

- `kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactAnnotations.kt` —
  the two annotation declarations (lines ~11–49).
- `kompact/src/commonTest/kotlin/ch/trancee/kompact/runtime/KompactFieldV1SurfaceTest.kt`
  — the compile-time contract test.
- `.scratch/kompact-spec/issues/02-generation-strategy.md` — the
  locked decision (KSP 2.3.9+, KSP emits whole `value class` source
  files into commonMain).
- `.scratch/kompact-spec/issues/12-module-split-and-publication.md`
  — the module split (`:kompact` runtime + `:kompact-ksp` processor
  + optional gradle plugin).
- `.scratch/kompact-spec/issues/03-value-class-representation.md`
  — the expect/actual value-class pattern the processor would
  generate.
- `.scratch/kompact-spec/research/ksp-kmp-generation.md` — the
  research notes on KSP generation across KMP targets.
- The annotations are referenced in the API reference
  (`docs/api-reference.md#annotations`) and in
  `VehicleTelemetry.kt` (the bundled example uses
  `@KompactModel` + `@KompactField`).

## Open sub-questions

1. **Scope of the minimal processor (option 1)** — the v1 type
   set is large (signed/unsigned scalars 1–64, booleans, IEEE
   floats, strings, blobs, nested, repeated, versioning). A
   processor that handles the full set is a large piece of work.
   A minimum viable processor that handles only the
   fixed-width scalar case (the same case `VehicleTelemetry`
   exercises today) is a much smaller lift and would make
   *that* model ergonomic. Which scope is the right MVP for this
   effort's blast radius?
2. **Module split** — option 1 implies a new `:kompact-ksp`
   module. The current `settings.gradle.kts` includes only
   `:kompact`. Adding a new module is a non-trivial change to
   the build. Is that within this effort's blast radius, or is
   it a fresh effort?
3. **`Beta` / `@RequiresOptIn` semantics (option 2)** — Kotlin's
   `@RequiresOptIn` is the idiomatic gate. The annotations
   *aren't* experimental in the language sense (they're stable
   source-retained metadata), so the gate would be a
   "we reserve the right to change the schema metadata" notice.
   Worth it, or over-engineered?
4. **If option 3 (keep as-is)** — is the friction
   (newcomer-added-but-no-processor) the right paper cut, or
   should the docs be louder about the hand-written
   `VehicleTelemetry` pattern being the intended path until the
   processor lands?

## What "resolved" looks like

- The chosen path is recorded under `## Answer` with a one-line
  rationale.
- If option 1 is chosen, the MVP scope and the module-split
  decision are recorded.
- If option 2 is chosen, the `@RequiresOptIn` shape and the
  migration plan (how to handle the existing `VehicleTelemetry`
  example) are recorded.
- The decision is propagated to ticket 07
  (`VehicleTelemetry` example alignment) and to
  `docs/api-reference.md#annotations` (or a follow-up doc
  commit).

## Answer

**Decision: hide `@KompactModel` and `@KompactField` behind a
`@RequiresOptIn` gate. The annotations move from the public
`ch.trancee.kompact.runtime` package to a preview package
`ch.trancee.kompact.annotations`, marked with a single
`@RequiresOptIn(level = RequiresOptIn.Level.WARNING)` annotation
`@KompactPreview`. Consumers who want the annotations today
opt in with `@OptIn(KompactPreview::class)`; consumers who
don't opt in don't see the annotations (they're "preview" API
and don't appear in IDE completion). Reversible when the
processor ships — re-expose the annotations as public. The
runtime classes stay in `ch.trancee.kompact.runtime`; only the
annotation classes move.**

### Why this option

The locked v1 spec (tickets 02, 03, 12, 13) commits the project
to a future `:kompact-ksp` KSP processor, but the processor is
not part of *this* effort's blast radius. The annotations are
`SOURCE`-retained (zero runtime cost) and currently sit in the
public API as a forward-compatibility hook. A newcomer who
reads the docs and adds `@KompactField` to a model gets nothing
today — no runtime effect, no processor. The honest ergonomic
call is to *say so in the compiler* via `@RequiresOptIn`, not
in prose alone. The warning fires at compile time, not at code
review time.

Hiding the annotations is reversible: when the processor lands
(a separate effort), re-expose the annotations as public and
the opt-in gate is no longer needed. The `KompactFieldV1SurfaceTest`
stays as the compile-time contract test (it moves with the
annotations to the new package and opts in).

### What gets added

1. **`kompact/src/commonMain/kotlin/ch/trancee/kompact/annotations/KompactAnnotations.kt`** —
   the two annotation classes (`@KompactModel`, `@KompactField`)
   in the new preview package, with the same SOURCE retention
   and the same eight `@KompactField` members as the locked
   spec (per `KompactFieldV1SurfaceTest`'s compile-time contract).
2. **`@RequiresOptIn(level = RequiresOptIn.Level.WARNING)` annotation `KompactPreview`**
   — declared in the same file; applied to both annotation
   classes. The warning message references the missing KSP
   processor and the migration path (hand-written value class
   views, or `@OptIn(KompactPreview::class)` to silence).

### What gets removed

- **`kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactAnnotations.kt`**
  — the old location. The runtime package no longer carries
  the annotation classes.

### Sketch (for the implementation commit)

```kotlin
// kompact/src/commonMain/kotlin/ch/trancee/kompact/annotations/KompactAnnotations.kt

@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "The @KompactModel / @KompactField schema annotations are a " +
              "preview API. No KSP processor ships in this repository yet. " +
              "Either hand-write your value class view (see VehicleTelemetry) " +
              "or @OptIn(KompactPreview::class) to silence this warning."
)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)  // @RequiresOptIn requires BINARY
public annotation class KompactPreview

@KompactPreview
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class KompactModel

@KompactPreview
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
public annotation class KompactField(
    public val bitOffset: Int,
    public val bitWidth: Int,
    public val lengthPrefixWidth: Int = 8,
    public val isNested: Boolean = false,
    public val repeatCountWidth: Int = 8,
    public val enumWidth: Int = 0,
    public val defaultValue: String = "",
    public val isVersionField: Boolean = false,
)
```

### Propagation

- **`kompact/src/commonTest/.../KompactFieldV1SurfaceTest.kt`** —
  updates the import path from
  `ch.trancee.kompact.runtime.KompactField` to
  `ch.trancee.kompact.annotations.KompactField` and adds
  `@OptIn(KompactPreview::class)` to the probe class (or the
  test class). The test's purpose — pin the compile-time
  contract of `@KompactField`'s eight members — is unchanged.
- **`kompact/src/commonMain/.../generated/VehicleTelemetry.kt`**
  (and the jvmMain + iosMain actuals) — updates the import path
  and adds `@file:OptIn(KompactPreview::class)` (or
  `@OptIn(KompactPreview::class)` on the class). The example
  stays annotated; the warning is silenced for the example.
  Note in ticket 07's body (the example-alignment ticket) —
  07's "raw `readBits` shape" option is no longer the only
  choice, since the annotations can stay on the example.
- **Docs**:
  - `docs/api-reference.md#annotations` — the public-surface
    table for `@KompactModel` / `@KompactField` is replaced
    with a "Preview API" note pointing at the new package,
    the `@KompactPreview` opt-in, and the hand-written value
    class pattern.
  - `docs/architecture.md#value-class-representation` and the
    "what is and is not in this repository today" section —
    the annotation visibility decision is reflected.
  - `README.md` — the "No codegen yet" bullet updates to
    "Preview schema annotations (`@KompactModel` /
    `@KompactField`) require `@OptIn(KompactPreview::class)`.
    No KSP processor ships in this repository today; hand-write
    your value class view (see `VehicleTelemetry`)."
- **Ticket 07** (`VehicleTelemetry` example alignment) — the
  forward note from ticket 01 still applies. Ticket 06
  confirms that the example's annotations can stay (via
  `@OptIn`), so 07's decision is purely about the getter
  shape (raw `readBits` vs. public `readScalar` +
  `readScalarAsLong`). Note in 07's body; do not resolve.

### What this does NOT do

- **No `:kompact-ksp` module is created.** That is a separate
  effort; this ticket only hides the annotation surface that
  currently pretends to be public.
- **No BCV regen needed** — the annotations are SOURCE-retained
  and the preview opt-in is a compile-time feature; both have
  zero runtime/ABI impact. The public ABI (the runtime classes
  in `ch.trancee.kompact.runtime`) is unchanged.
- **No `KompactFieldV1SurfaceTest` removal** — the test
  continues to pin the compile-time contract of
  `@KompactField`'s eight members, just in the new package.

## Comments
