# Design and trade-offs

Kompact offers two ways to describe where fields live on the wire. This page
explains why both exist, what each asks of the caller, and where ownership
remains. For signatures, use the
[API reference](api-reference.md); for implementation steps, use the
[how-to guides](how-to/README.md).

## Fixed positions or sequential fields

A fixed-layout message assigns every field a stable bit offset. This is a
natural fit for small records whose fields never move. The reader can go
straight to a field, and the generated model can validate its minimum buffer
size once when the view is created.

A string, blob, nested message, or repeated value does not have a fixed size.
Kompact's framed layout therefore reads fields in order. Variable-length
payloads carry explicit byte-length prefixes; repeated fields carry a count
prefix, and variable-size elements also carry their own length prefix. The
reader consumes each bounded region before continuing to the next field.

This is a deliberate tradeoff, not an offset-table format. Earlier fields can
change the position of later fields, so framed schemas declare a contiguous
`order` instead of absolute offsets. Framing makes those boundaries explicit,
but does not provide random access or make schema changes automatically
compatible.

Use a [fixed-layout schema](how-to/define-message.md) when offsets are stable.
Use a [sequential framed schema](how-to/define-framed-schema.md) when fields
have variable size.

## Keep a decode inside its boundary

Bytes from a transport or file may be truncated or malformed. Checked scalar
reads report a typed error, and the framed reader validates prefixes and field
bounds as it advances. Generated framed decoding also checks that the frame
matches the declared schema; an application that needs a fallback or retry
policy must choose it at its own boundary.

The API keeps checked reads, throwing conveniences, and raw bit primitives
distinct. A checked read preserves failure as a result; `getOrThrow()` and
direct `KompactFrame` reads make throwing behavior explicit. Raw bit operations
do not validate bounds and are intended for layouts whose bounds are already
known.

See [How to handle malformed input](how-to/handle-decode-errors.md) for
recovery choices and the [API reference](api-reference.md) for the exact
result and error types.

## Borrowed views keep the owner visible

Generated views and byte slices refer to the caller's `ByteArray`; they do not
create an independent snapshot. This avoids eagerly copying nested payloads
or materializing every repeated value, but the array's owner remains
responsible for its lifetime and mutation.

If another reference changes the array, a view observes that change. Keep the
array unchanged while using a borrowed slice or lazy repeated view. Copy the
bytes when the data needs independent ownership. See the
[BLE boundary guide](how-to/integrate-ble.md) for the same rule when a
transport may retain a buffer.

## Result representations and performance evidence

The checked read API has concrete result types rather than one generic
`Result<T>`. Several scalar results use value classes; `LongResult` is a
regular class so it can represent every `Long` value without reserving a
sentinel. Those are representation choices for encoding values and errors in concrete
types; they do not establish how much work or allocation a particular call
site performs.

They are not a guarantee that every call shape is allocation-free. Kotlin may
box a value class at nullable, generic, or interface boundaries, and actual
runtime behavior depends on the compiler and platform. The repository does
not publish a measured latency or allocation budget. The
[allocation and boxing research note](research/allocation-boxing-measurement.md)
describes the evidence required before making such a performance claim.

## Schema evolution is application policy

Framed schemas are positional and strict: field order and declared types form
the wire contract, and fields are required. Appending a field is not
automatically compatible because an older reader may reject trailing data and
a newer reader may expect a field that an older sender omits. Kompact does not
add a schema-version discriminator; applications that need mixed versions
must define one and choose a migration policy.

For the framing decision and its constraints, see
[ADR-0008](adr/0008-framed-generated-views.md). For the deferred versioning
surface, see [ADR-0002](adr/0002-defer-versioning-surface-to-v2.md).
