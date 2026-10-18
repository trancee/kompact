# ADR-0008: Sequential framed generated views

- Status: Accepted. [ADR-0009](0009-common-only-code-generation.md) partially
  supersedes it.
- Date: 2026-09-27
- Tags: schema, wire, codegen
- Partially superseded by: [ADR-0009](0009-common-only-code-generation.md).
  Generated views are single common declarations, the plugin registers only
  `commonMain` sources, and fixed-layout schemas also generate `<Name>View`.
- Amended by: [ADR-0011](0011-inferred-nested-fields-and-checked-fixed-decode.md).
  The processor infers nested fields from the property type, and `isNested` is
  removed.
- Amended by: [ADR-0013](0013-typed-status-and-caller-contract-errors.md).
  Cursor and generated-holder operations return `KompactStatus`, and trailing
  framed data reports `TrailingData`.

## Context

A fixed-layout `bitOffset` cannot locate fields that follow a variable-length
payload. The runtime already writes little-endian 8-, 16-, and 32-bit length
and count prefixes. Changing how those prefixes are read would break existing
frames. Copying a nested payload, or decoding a list eagerly, would also make
large messages expensive.

## Decision

`@KompactModel(framed = true)` selects sequential framing. Every field declares
one distinct `order`, and the orders are contiguous from zero. Fixed-layout
models keep their offset-based value-class generator unchanged. A framed
schema `Packet` generates `PacketView`, so the view does not collide with the
source declaration.

A framed model generates a bounded regular class with these members:

- `raw`, `start`, and `end`
- typed properties
- `create` and `copy`
- `decode`, which returns a typed result

Direct frame reads report malformed UTF-8 as `InvalidUtf8`, and the `decode`
entry point returns it as a typed failure.

A nested model and a blob slice borrow the original array. A repeated view
validates its boundaries once and decodes each element when the caller
accesses it. It does not build a list of decoded values. A `ByteArray` blob
property returns an explicit copy on access. Its companion property
`<field>Slice` returns the bytes without copying. Repeated variable-width
elements offer `getElementSlice(index)`, which returns the borrowed payload
without its prefix as a typed result.

Construction uses the existing forward writer and a bounded nested append
operation. The generated code validates inputs within the declared bounds.
Truncation, invalid prefixes, and trailing data return typed decode failures.
Borrowed variable-length fields must start on a byte boundary. The generator
rejects parameterized nested schema classes and mutable framed setters. It
resolves repeated element types from KSP generic arguments, not from field
names.

## Alternatives considered

The project rejected three alternatives:

- Reinterpret `bitOffset` as an order. This breaks released scalar layouts.
- Materialize nested arrays or lists. This simplifies generated accessors, but
  it breaks the borrowed and lazy contract.
- Look up fields by name at run time. This moves schema errors from generation
  to run time.

## Consequences

Borrowed slices reflect changes to the caller's array. Callers that need
ownership must copy. A repeated view keeps the exact bit range that it
validated at construction, so a prefix changed later cannot make lazy access
cross into the next field. Changing a variable-length prefix while a view is in
use is unsupported, because the change invalidates the sparse element index.

Variable repeats use O(count / 64) checkpoint memory and O(count) validation
time, and indexed access scans at most 63 prefixes. Fixed-width repeats use
constant index memory.

Direct constructors and the explicit `getOrThrow()` convenience throw typed
decode exceptions on invalid frames. Callers that handle untrusted input use
`decode`, and then `getResult(index)` for repeated elements that decode
lazily. A scalar repeat whose width is not a whole number of bytes cannot come
before a borrowed variable payload, because its final alignment depends on the
encoded count.

The standard KSP2 Gradle integration still has no stable connection to common
metadata sources. Kompact therefore provides a separate Gradle plugin,
`ch.trancee.kompact.codegen`. One cacheable task calls `KSPCommonConfig`, then
routes the generated common and platform sources into the KMP source sets. The
TestKit fixture of the plugin verifies compilation for JVM, Android JVM, iOS
Arm64, iOS Simulator Arm64, and Android Native Arm64. It also verifies
configuration-cache reuse and build-cache restoration from a relocated
project. The adapter uses reflection to call the `KSPLoader` entry point of
KSP2, and it takes the common analysis libraries from the metadata compile
task of KGP. Any KSP or KGP upgrade must keep this integration test passing.
The common-schema generation research records the exact classpath and the
compatibility evidence.

> Note added 2026-10-18. Since [ADR-0009](0009-common-only-code-generation.md),
> the plugin registers one generated directory with `commonMain` only, and it
> no longer routes platform sources. [ADR-0012](0012-kotlin-minor-line-compatibility.md)
> defines which Kotlin Gradle Plugin versions the plugin accepts.

## Migration

No existing scalar wire bytes or annotation offsets change. Declare new
variable-length schemas with `framed = true`, a contiguous `order`, and
explicit prefix widths. For untrusted input, call `decode(...)` and check
`error`, or call `getOrThrow()`, instead of constructing a view directly. The
new public API is additive in the pre-1.0 `0.5.0` MINOR release. The
sequential wire format stays unchanged.

## 2026-10-17 clarification: nested-write capacity diagnostics

An aligned cursor that the caller owns can lack enough space for the nested
length prefix itself. `writeNested` used to classify this case as an alignment
error, even though the position was byte-aligned. It now returns
`STATUS_BOUNDS_ERROR` when the prefix or the payload does not fit. It sets
`errorBitOffset` to the original position and `errorDetail` to the payload
byte length. An unaligned position still returns `STATUS_UNALIGNED_REGION`
before the capacity check. Both failures leave the destination bytes, the
parent position, and the child binding unchanged.

> Note added 2026-10-18. [ADR-0013](0013-typed-status-and-caller-contract-errors.md)
> replaced the `KompactCursor.STATUS_*` constants with the `KompactStatus` enum.
> The statuses in this section are now `KompactStatus.BOUNDS_ERROR` and
> `KompactStatus.UNALIGNED_REGION`.

Keeping the old classification would keep a misleading diagnostic. A new
status would grow the public API for no gain. Reusing the existing bounds
status matches the other checked writes. Wire bytes and signatures do not
change. This is a compatible correction of a diagnostic in the unreleased
cursor API. Callers must not read prefix exhaustion as a request to realign
their data. Shared boundary tests cover all supported prefix widths and keep
the exact-fit and alignment cases.

## 2026-10-17 clarification: caller-owned fixed-repeat checkpoints

The contract for a workspace that the caller owns requires `ceil(count / 64)`
checkpoint slots for both fixed and variable repeats. This requirement is
separate from the constant index memory of the generated fixed-width view
described above. The fixed cursor implementation used to visit every element
only to pick every 64th offset.

Compute each fixed checkpoint directly from the validated repeat start, the
checkpoint index, the interval, and the element width. Keep intermediate
arithmetic in `Long`, and finish every bounds and capacity check before
storing any checkpoint. The
[retained host comparison](../research/repeat-index-benchmark.md) records the
baseline before the change, the candidate, the unchanged controls, and the
allocation probes.

The per-element loop adds measured work and validates nothing extra.
Removing fixed checkpoints entirely would change the contents of storage that
the caller owns, and the capacity diagnostics, so that alternative is
rejected. Variable repeats keep their two-pass validation and index
construction. Writing checkpoints during the first pass would break failure
atomicity.

The main risk is wrong arithmetic at block boundaries, or touching unused
slots. Focused common tests cover widths, counts, nonzero starts, exact
bounds, and truncated input. Signatures, status precedence, wire bytes,
workspace capacity, and bindings do not change, and callers need no
migration. Host speedups are not device timings, and they are not a general
allocation guarantee.

## 2026-10-17 clarification: checked aligned count-prefix reading

The fixed-repeat cursor already checks the count-prefix width (8, 16, or 32
bits), the byte alignment, and the prefix bounds before it reads the count.
Read those bytes directly as unsigned little-endian values into a `Long`,
instead of repeating the work of the generic bit reader. Keep the existing
`Int.MAX_VALUE`, workspace-capacity, and payload-bounds checks in their
original order, before any checkpoint or binding changes.

The generic reader stays correct, but it adds measured work inside a complete
generated-holder decode. Compiler flags and a restructured checkpoint loop were
rejected as fixes. Compiler flags change deployment requirements, and the
restructured loop slows other measured sizes. The
[follow-up measurements](../research/aligned-count-prefix-benchmark.md) record
the evidence, including the large Native result that is close to the budget.

The main risks are unsigned-byte masking and byte order. Shared tests cover
every prefix byte, count boundaries, nonzero starts, exact capacity
diagnostics, truncation, and unchanged workspace storage. The API, the wire
format, status precedence, capacity, and migration do not change.
