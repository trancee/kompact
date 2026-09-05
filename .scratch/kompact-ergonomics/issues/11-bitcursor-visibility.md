Type: grilling
Status: resolved
Blocked by: —  (note 2026-09-05: ticket 10 settled the docs layer — add Dokka in a follow-up commit (after the public surface from tickets 01 + 02 + 04 + 05 + 07 lands); `docs/api-reference.md` becomes the curated overview pointing at the generated `docs/api/`. The `bitCursor` row in the public-surface table is added or removed based on 11's decision; if 11 demotes `bitCursor` to `internal`, the row is removed from the curated overview and the generated reference will not include it (since `@internal` is excluded from the public output by default).)
# 11 — `KompactWriter.bitCursor`: ergonomic surface or implementation detail?

## Question

`KompactWriter` currently exposes a public read-only `var bitCursor: Int`
(`kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactWriter.kt:24`),
documented as: "Exposed so nested/repeat assembly can reason about
bit alignment without re-deriving it (PROMPT §1: forward-only)."

The decision: is `bitCursor` a real ergonomic surface that consumers
writing multi-step payloads will read, or an implementation detail
that should be `internal`? Three options to grill:

- **Keep `bitCursor` public** — the documented rationale (nested
  /repeat assembly) is real: a consumer writing `writeNested { ... }`
  followed by more `writeScalar` calls may want to inspect the
  cursor to know where they are. The public surface grows by
  one property, but the use case is genuine.
- **Demote `bitCursor` to `internal`** — the cursor is the
  writer's implementation detail; consumers write a multi-step
  payload by appending values and calling `build()`, not by
  inspecting the cursor. The "nested/repeat assembly" use case
  is satisfied by `writeNested { ... }` (the cursor is
  advanced *inside* the block; the consumer doesn't read it).
  Reducing the public surface to the minimum.
- **Replace `bitCursor` with a `position()` / `mark()` /
  `seek()` API** — if "inspect the cursor" is a real need,
  a richer API (cursor + markers + seeking) is more useful
  than a single read-only `var`. The writer becomes more
  powerful but the public surface grows.

This ticket is on the frontier now because the writer signature
stabilized after tickets 01 (`writeScalar(type, value)`) + 04 (the
`…OrThrow` pattern applies to readers, not writers) + 05
(NestedRegionResult is a read-side concern). The question can be
asked concretely without speculating about the writer's final
shape.

## Context for the claiming session

- `kompact/src/commonMain/kotlin/ch/trancee/kompact/runtime/KompactWriter.kt`
  — the `bitCursor` declaration (lines ~22–25):
  ```kotlin
  public var bitCursor: Int = 0
      private set
  ```
  Read-only (the setter is `private`); advanced by `writeBits` /
  `writeBitsLong` / `writeBool` / `writeScalar` / `writeString` /
  `writeBlob` / `writeNested` / `writeRepeated` (each of these
  appends and advances the cursor).
- The cursor is consulted internally by `ensureCapacityBits`
  (line ~125) and `appendBytes` (line ~133). Both are `private`.
  The consumer-visible uses of `bitCursor` are: (a) reading the
  current position to decide what to write next, (b) reading the
  current position to know the byte length of a nested region
  (e.g. for a length prefix the consumer wants to write later).
- The writer's `writeNested` signature:
  `writeNested(lengthPrefixWidth: Int = 16, block: KompactWriter.() -> Unit)`.
  The block runs against the *parent* writer (the cursor is
  advanced inside the block, but the consumer's writes are
  applied to the parent; the child writer is internal). A
  consumer who wants to know "how many bits did I write in this
  nested block?" would need to read the cursor, but the
  `writeNested` API doesn't expose that — the consumer is given
  a single `lengthPrefixWidth` and the block; the block's
  bit-length is computed internally. So the documented
  rationale (Ticket 07) for `bitCursor` is the *internal* use
  case, not the consumer's.
- The writer's `build(): ByteArray` returns the exact-length
  snapshot. The consumer who wants to know the bit-length of
  the accumulated payload reads the returned `ByteArray`'s
  `size * 8` (or the cursor before calling `build()`). The
  `bitCursor` is a convenient property for that.

## Open sub-questions

1. **Is the "nested/repeat assembly" use case real for
   consumers?** The internal use (the writer's own bookkeeping)
   is real, but the consumer's use is less clear. The current
   `writeNested` API doesn't expose the cursor inside the block.
   If a consumer wants to know "how much did I write?", they
   read the cursor. But the *common* pattern is "write a
   payload, call `build()`, get the bytes" — the cursor is
   an internal implementation detail of that pattern.
2. **Is the cursor a stable contract?** If a future KSP
   processor generates writer code (e.g. `writeScalar` calls
   for a `@KompactField`-annotated class), the generated code
   would not consult `bitCursor` — the layout is compile-time.
   The cursor is for *hand-written* multi-step payloads. As
   hand-written payloads become less common (the processor
   takes over), the cursor's audience shrinks.
3. **The `Kompact.Result.NestedRegion` typealias + the
   `readNested` typed result (ticket 05) cover the read side
   of nested regions.** The write side (`writeNested`) is a
   block, not a cursor-driven API. So the read/write symmetry
   is on the *block* pattern, not the cursor.

## What "resolved" looks like

- The chosen path is recorded under `## Answer` with a
  one-line rationale.
- The chosen path propagates to `docs/api-reference.md` (the
  public-surface table for `KompactWriter` lists or omits
  `bitCursor`).
- The chosen path propagates to the implementation commit:
  the visibility modifier on the declaration + any test
  updates + any doc updates.

## Answer

**Decision: demote `bitCursor` to `internal`.**

Rationale (three converging facts):

1. **No consumer reads it.** A repo-wide grep for `bitCursor` shows
  references only inside `KompactWriter.kt`'s own methods
  (`writeBits`/`writeBool`/`writeString`/`writeBlob`/`writeNested`/
  `writeRepeated`/`build`/`ensureCapacityBits`/`appendBytes`). The
  `GettingStartedTest` (the only consumer-facing writer test) calls
  `writeScalar`/`writeBool`/`writeString` + `build()` and never reads
  the cursor. The `VehicleTelemetry` example is a *reader*; it doesn't
  touch the writer at all.
2. **The documented rationale is internal.** The "nested/repeat
  assembly can reason about bit alignment" use case is satisfied by
  `writeNested(lengthPrefixWidth, block)` (compute-first, no back-
  patch) and `writeRepeated(count, countWidth, block)` — the
  consumer's bit-length is computed *inside* the block and never
  exposed. The consumer's pattern is "write a payload, call
  `build()`, get the bytes" — the cursor is the writer's internal
  bookkeeping.
3. **The future KSP processor won't use it.** A generated writer
  (from `@KompactField` annotations) computes the field layout at
  compile time; it calls `writeScalar`/`writeBool`/etc. directly and
  never inspects the cursor. The cursor's audience shrinks to zero
  as hand-written payloads disappear.

The "replace with a `position()` / `mark()` / `seek()` API" option is
**rejected** — speculative, no demonstrated consumer need, and it
grows the surface (3+ new functions) instead of shrinking it. The
map favors the "smallest simplification that makes the surface
easier."

### Propagation

This decision lands in the **implementation commit** (the single commit
that applies tickets 01 + 02 + 04 + 05 + 06 + 11 to the source):

- **Source**: `KompactWriter.kt` line 24 changes from `public var
  bitCursor` to `internal var bitCursor`. The KDoc drops the "Exposed
  so nested/repeat assembly..." sentence (it is now internal). No
  other source change — the property is read+written only within
  `KompactWriter`.
- **Docs**: remove the `bitCursor` row from `docs/api-reference.md`'s
  `KompactWriter` public-surface table.
- **Golden**: the post-implementation `kompact.klib.api` will not list
  `bitCursor` (regenerated on macOS via `regen-goldens.yml`).

### Override

If a streaming / multi-step writer use case emerges where a consumer
needs to read the cursor to "decide what to write next," re-open this
 ticket and keep `bitCursor` public. The `position()` richer API can
 then be revisited with a concrete consumer.

## Comments
