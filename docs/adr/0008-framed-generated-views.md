# ADR-0008 — Sequential framed generated views

- **Status:** Accepted
- **Date:** 2026-09-27
- **Tags:** schema, wire, codegen
- **Partially superseded by:** [ADR-0009](0009-common-only-code-generation.md)
  (generated views are single common declarations; the plugin registers only
  `commonMain` sources, and fixed-layout schemas also generate `<Name>View`)
- **Amended by:** [ADR-0011](0011-inferred-nested-fields-and-checked-fixed-decode.md)
  (nested fields are inferred from the property type; `isNested` is removed)
- **Amended by:** [ADR-0013](0013-typed-status-and-caller-contract-errors.md)
  (cursor and generated-holder operations return `KompactStatus`; trailing
  framed data reports `TrailingData`)

## Context

Fixed-layout `bitOffset` cannot locate fields after a variable-length payload.
The existing runtime already writes little-endian 8/16/32-bit length and count
prefixes; changing their interpretation would break existing frames. A copied
nested payload or eagerly decoded list would also make large messages expensive.

## Decision

`@KompactModel(framed = true)` selects sequential framing; every field declares
one distinct, contiguous `order` starting at zero. Fixed-layout models retain
their existing offset-based value-class generator unchanged. A framed schema
`Packet` generates `PacketView` to avoid colliding with the source declaration.
Framed models generate bounded regular classes with `raw`, `start`, and `end`, typed properties,
`create`, `copy`, and a typed-result `decode`. Direct frame reads distinguish
malformed UTF-8 as `InvalidUtf8`; the `decode` entry point returns it as a typed
failure. A nested model and blob slice
borrow the original array. A repeated view validates boundaries once and decodes
elements on access, without building a list of decoded values. A `ByteArray`
blob property copies explicitly on access; its `fieldSlice` companion is
zero-copy. Repeated variable-width elements offer `getElementSlice(index)` for
a typed-result borrowed payload, excluding its prefix. Construction uses the
existing forward writer and a bounded nested
append operation. Inputs are validated within the declared bounds; truncation,
invalid prefixes, and trailing data return typed decode failures. Byte-aligned
starts are required for borrowed variable-length fields.
Parameterized nested schema classes and mutable framed setters are rejected
at generation time; repeated element types are resolved from KSP generic
arguments rather than inferred from field names.

## Alternatives

Reinterpreting `bitOffset` as an order breaks released scalar layouts.
Materializing nested arrays or lists simplifies generated accessors but violates
the borrowed/lazy contract. A name-based dynamic lookup moves schema errors
from generation to runtime. These alternatives were rejected.

## Risks

Borrowed slices reflect mutations of the caller's array; callers needing
ownership must copy. A repeated view retains the exact bit range validated at
construction, so a later mutated prefix cannot make lazy access cross into a
following field. Changing a variable-length prefix while using the view is
unsupported because it invalidates the sparse element index. Variable repeats
use O(count / 64) checkpoint memory and
O(count) validation time; indexed access scans at most 63 prefixes. Fixed-width
repeats use constant index memory. Direct constructors and the explicit
`getOrThrow()` convenience throw typed decode exceptions on invalid frames;
untrusted callers should use `decode`, then `getResult(index)` for lazily
decoded repeated elements. A scalar repeat of non-byte width cannot
precede a borrowed variable payload because its final alignment depends on
the encoded count.

KSP2's standard Gradle integration still does not provide a stable
common-metadata source connection. Kompact therefore provides the separate
`ch.trancee.kompact.codegen` Gradle plugin: one cacheable task invokes
`KSPCommonConfig`, then routes the generated common and platform sources into
the KMP source sets. Its TestKit fixture verifies JVM, Android JVM, iOS Arm64,
iOS Simulator Arm64, and Android Native Arm64 compilation, configuration-cache
reuse, and relocated build-cache restoration. The adapter reflects into KSP2's
`KSPLoader` entry point and obtains the common analysis libraries from KGP's
metadata compile task, so KSP/KGP upgrades must keep this integration test
passing. See the common-schema generation research for the exact classpath and
compatibility evidence.

## Migration

No existing scalar wire bytes or annotation offsets change. Declare new
variable-length schemas with `framed = true`, contiguous `order`, and explicit
prefix widths; use `decode(...).error` or `getOrThrow()` rather than constructing
an untrusted view directly. The new public API is additive in the pre-1.0
`0.5.0` MINOR release. The sequential wire format remains unchanged.

## 2026-10-17 clarification: nested-write capacity diagnostics

An aligned caller-owned cursor can lack enough space for the nested length
prefix itself. `writeNested` previously classified this as an alignment error,
even though its position was byte-aligned. It now returns
`KompactStatus.BOUNDS_ERROR` for insufficient prefix or payload capacity, with the
original position as `errorBitOffset` and the payload byte length as
`errorDetail`. An unaligned position still returns `KompactStatus.UNALIGNED_REGION`
before checking capacity. Both failures preserve destination bytes, parent
position, and child binding.

Keeping the old classification would preserve a misleading diagnostic;
introducing a new status would unnecessarily expand the public API. Reusing
the existing bounds status matches other checked writes. Wire bytes and
signatures do not change. This is a compatible diagnostic correction for the
unreleased cursor API; callers must not interpret prefix exhaustion as a
request to realign their data. Shared boundary tests cover all supported
prefix widths and preserve the exact-fit and alignment cases.

## 2026-10-17 clarification: caller-owned fixed-repeat checkpoints

The caller-owned workspace contract requires `ceil(count / 64)` checkpoint
slots for both fixed and variable repeats. This is separate from the generated
fixed-width view's constant index memory described above. The fixed cursor
implementation previously visited every element merely to select each
64th offset.

Compute each fixed checkpoint directly from the validated repeat start,
checkpoint index, interval, and element width. Keep `Long` intermediate
arithmetic and finish every bounds/capacity check before storing checkpoints.
The [retained host comparison](../research/repeat-index-benchmark.md) records
the pre-change baseline, candidate, unchanged controls, and allocation probes.

Keeping the per-element loop adds measured work without improving validation.
Removing fixed checkpoints entirely would change caller-owned storage contents
and capacity diagnostics, so that alternative is rejected. Variable repeats
retain their two-pass validation/index construction because mutating
checkpoints during the first pass would break failure atomicity.

The main risk is incorrect arithmetic at block boundaries or touching unused
slots; focused common tests cover widths, counts, nonzero starts, exact bounds,
and truncated input. No signatures, status precedence, wire bytes, workspace
capacity, or bindings change, and callers need no migration. Host speedups are
not device timing or a universal allocation guarantee.

## 2026-10-17 clarification: checked aligned count-prefix reading

The fixed-repeat cursor already checks the count-prefix width (8, 16, or
32 bits), byte alignment, and prefix bounds before reading the count.
Read those bytes directly as unsigned little-endian values into a `Long`
instead of repeating generic bit-reader work. Preserve the existing
`Int.MAX_VALUE`, workspace-capacity, and payload-bounds checks in their
original order, before any checkpoint or binding mutation.

The generic reader remains correct but adds measured work inside complete
generated-holder decoding. Compiler flags and checkpoint-loop restructuring
were rejected as the fix: they either change deployment requirements or
regress other measured sizes. The
[follow-up measurements](../research/aligned-count-prefix-benchmark.md)
record the evidence and the near-budget large Native result.

Unsigned-byte masking and byte order are the principal risks; shared tests
cover every prefix byte, count boundaries, nonzero starts, exact capacity
diagnostics, truncation, and unchanged workspace storage. There is no API,
wire, status-precedence, capacity, or migration change.
