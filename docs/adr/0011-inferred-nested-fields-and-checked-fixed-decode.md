# ADR-0011: Inferred nested fields and checked fixed-layout decode

- Status: Accepted
- Date: 2026-10-18
- Tags: api, bc-break, codegen
- Amends: [ADR-0008](0008-framed-generated-views.md) (nested framed fields no
  longer need an annotation flag) and
  [ADR-0009](0009-common-only-code-generation.md) (fixed-layout views gain a
  checked `decode` factory)

## Context

`@KompactField` still had three parameters that repeated information, or
promised behavior that the processor did not have:

- `isNested` had to be `true` exactly when the property type was a
  `@KompactModel(framed = true)` class. The processor already resolved that
  declaration to check it. The flag only restated the type and added failure
  modes ("must set isNested=true", "cannot be marked nested").
- `enumWidth` was validated but never used in generated code. Kompact has no
  enum field type, and [ADR-0010](0010-minimal-checked-result-surface.md)
  removed the unused enum error.
- `defaultValue` was documented as unsupported by code generation, and framed
  schemas rejected it.

Fixed-layout views also had only one way to wrap received bytes: the
`<Name>View(raw)` constructor. It throws `IllegalArgumentException` when the
buffer is shorter than the layout. Framed views already offered a checked
`decode` factory that returns `KompactFrameResult`. Fixed-layout schemas had no
matching typed path for untrusted input.

Kompact is pre-1.0 ([ADR-0007](0007-pre-1-release-versioning.md)), so a minor
release may contain breaking changes.

## Decision

1. Remove `isNested`, `enumWidth`, and `defaultValue` from `@KompactField`.
   The remaining parameters are `bitOffset`, `bitWidth`, `signed`,
   `lengthPrefixWidth`, `repeatCountWidth`, and `order`.
2. The processor infers nesting from the property type. A type that is not a
   scalar, `String`, `ByteArray`, or `List` resolves as follows. The same rules
   apply recursively to `List` elements and type arguments.
   - A `@KompactModel(framed = true)` class is a nested field.
   - Any other `@KompactModel` class is a processor error: "Nested field type
     `X` must be a @KompactModel(framed = true) class".
   - Any other type stays unsupported, and the validators report it.
3. Drop the validator rules that only the removed parameters needed:
   - the range check on the enum width
   - the "nested field needs a length prefix" check, because nested types are
     framed-only and framed prefixes must be 8, 16, or 32
   - the framed consistency rules for `isNested`, `enumWidth`, and
     `defaultValue`

   Rules that the type expresses stay. Fixed layouts reject nested model
   types, and framed schemas reject parameterized nested models.
4. The companion of every generated fixed-layout `<Name>View` with at least one
   field, and of its `Mutable<Name>View` sibling, gains
   `decode(raw: ByteArray): KompactFrameResult<…>`. When `raw.size` is smaller
   than the byte length of the layout, it returns
   `KompactFrameResult.Failure(KompactDecodeError.BoundsError)`. Otherwise it
   returns `KompactFrameResult.Success`, which wraps `raw` without copying. The
   throwing constructor stays for trusted buffers.

## Alternatives considered

- Keep `isNested` as an optional override. Rejected: the flag can never
  legally differ from the type, so it can only be redundant or wrong.
- Keep `enumWidth` and `defaultValue` as reserved parameters. Rejected:
  reserved annotation members are public API, and they suggest behavior that
  the processor does not implement. A later change can bring them back with
  real meaning.
- Add a result type for fixed layouts instead of reusing `KompactFrameResult`.
  Rejected: one checked-decode result type for fixed and framed views keeps
  call sites the same.

## Consequences

- The annotation API is smaller, and schemas cannot express contradictory
  nesting.
- To migrate, delete the `isNested = …`, `enumWidth = …`, and
  `defaultValue = …` arguments from `@KompactField`. A field typed as a
  fixed-layout `@KompactModel` now fails with an explicit message instead of
  "unsupported type".
- Callers can decode untrusted fixed-layout input without exception handling.
  Fixed and framed views share the same shape of `decode` entry point.
- No wire bytes change.
