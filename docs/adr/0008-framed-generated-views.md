# ADR-0008 — Sequential framed generated views

- **Status:** Accepted
- **Date:** 2026-09-27
- **Tags:** schema, wire, codegen

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
