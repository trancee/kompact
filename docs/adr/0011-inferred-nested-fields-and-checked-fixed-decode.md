# ADR-0011 — Inferred nested fields and checked fixed-layout decode

- **Status:** Accepted
- **Date:** 2026-10-18
- **Tags:** api, bc-break, codegen
- **Amends:** [ADR-0008](0008-framed-generated-views.md) (nested framed fields
  no longer need an annotation flag) and
  [ADR-0009](0009-common-only-code-generation.md) (fixed-layout views gain a
  checked `decode` factory)

## Context

`@KompactField` still carried three parameters that duplicated or promised
information the processor did not need:

- `isNested` had to be `true` exactly when the property type was a
  `@KompactModel(framed = true)` class. The processor already resolved that
  declaration to check it, so the flag only restated the type and created
  extra failure modes ("must set isNested=true", "cannot be marked nested").
- `enumWidth` was validated but never generated. Kompact has no enum field
  type ([ADR-0010](0010-minimal-checked-result-surface.md) removed the unused
  enum error).
- `defaultValue` was documented as unsupported by code generation, and framed
  schemas rejected it.

Separately, fixed-layout views had only one way to wrap received bytes: the
`<Name>View(raw)` constructor, which throws `IllegalArgumentException` when the
buffer is shorter than the layout. Framed views already offer a checked
`decode` factory that returns `KompactFrameResult`, so fixed-layout schemas had
no equivalent typed path for untrusted input.

Kompact is pre-1.0 ([ADR-0007](0007-pre-1-release-versioning.md)), so
breaking changes are allowed in a minor release.

## Decision

1. Remove `isNested`, `enumWidth`, and `defaultValue` from `@KompactField`.
   The remaining parameters are `bitOffset`, `bitWidth`, `signed`,
   `lengthPrefixWidth`, `repeatCountWidth`, and `order`.
2. Nesting is inferred from the property type. A type that is not a scalar,
   `String`, `ByteArray`, or `List` resolves as follows (recursively for `List`
   elements and type arguments):
   - a `@KompactModel(framed = true)` class is a nested field;
   - any other `@KompactModel` class is a processor error: "Nested field type
     `X` must be a @KompactModel(framed = true) class";
   - anything else stays unsupported and is reported by the validators.
3. Drop the validator rules that the removed parameters made necessary: the
   enum-width range check, the "nested field needs a length prefix" check
   (nested types are framed-only, and framed prefixes must be 8, 16, or 32),
   and the framed
   `isNested`/`enumWidth`/`defaultValue` consistency rules. Rules expressed
   through the type remain: nested model types are rejected in fixed layouts,
   and parameterized nested models are rejected in framed schemas.
4. The companion of every generated fixed-layout `<Name>View` with at least one
   field, and of its `Mutable<Name>View` sibling, gains
   `decode(raw: ByteArray): KompactFrameResult<…>`. It returns
   `KompactFrameResult.Failure(KompactDecodeError.BoundsError)` when
   `raw.size` is smaller than the layout's byte length. Otherwise it returns
   `KompactFrameResult.Success` wrapping `raw` without copying. The throwing
   constructor remains for trusted buffers.

## Alternatives considered

- **Keep `isNested` as an optional override.** Rejected: there is no case in
  which the flag may legally differ from the type, so it can only be redundant
  or wrong.
- **Keep `enumWidth` and `defaultValue` as reserved parameters.** Rejected:
  reserved annotation members are public API that suggests behavior the
  processor does not implement. They can be reintroduced with real semantics.
- **Add a fixed-layout result type instead of reusing `KompactFrameResult`.**
  Rejected: one checked-decode result type across fixed and framed views keeps
  call sites uniform.

## Consequences

- Smaller annotation API; schemas cannot express contradictory nesting.
- Migration: delete `isNested = …`, `enumWidth = …`, and `defaultValue = …`
  arguments from `@KompactField`. A field typed as a fixed-layout
  `@KompactModel` now fails with an explicit message instead of "unsupported
  type".
- Untrusted fixed-layout input can be decoded without exception handling, and
  fixed and framed views share the same `decode` entry point shape.
- No wire bytes change.
