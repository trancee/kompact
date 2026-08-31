# Schema authoring and generated API reference

This page describes schema declarations and their generated Kotlin and C99 surface in Kompact `0.1.0-SNAPSHOT`.

## Schema declaration rules

A Kompact schema is a declaration-only interface in `commonMain`:

- the interface has `@KompactSchema`;
- its name ends in `Schema`; generation removes that suffix;
- every property has `@KompactField`;
- every body bit belongs to one field or one `@KompactReserved` range;
- fields and reserved ranges do not overlap;
- generated visibility follows interface visibility;
- nested schema references are same-namespace, exact-version, and acyclic.

Stable schema, field, semantic, reserved-range, and enum-entry names match `[a-z][a-z0-9_]*`.

## Annotations

All annotations have `SOURCE` retention.

### `@KompactSchema`

Target: class.

| Parameter | Type | Constraint |
| --- | --- | --- |
| `registryName` | `String` | Stable schema name; must equal the registry entry. |
| `id` | `Int` | `1..4095`; zero is reserved. |
| `version` | `Int` | `0..15`; new versions are sequential. |

### `@KompactField`

Target: property.

| Parameter | Type | Default | Constraint |
| --- | --- | --- | --- |
| `stableName` | `String` | Required | Stable field name. |
| `semanticType` | `String` | Required | Stable semantic name. |
| `bitOffset` | `Int` | Required | Non-negative, body-relative bit offset. |
| `bitWidth` | `Int` | Required | Positive and compatible with the property's logical type. |
| `unit` | `String` | `""` | Empty omits the descriptor property. Otherwise case-sensitive. |
| `scaleNumerator` | `String` | `"1"` | Canonical decimal integer; reduced with the denominator. |
| `scaleDenominator` | `String` | `"1"` | Canonical positive decimal integer. |
| `offsetNumerator` | `String` | `"0"` | Canonical decimal integer; reduced with the denominator. |
| `offsetDenominator` | `String` | `"1"` | Canonical positive decimal integer. |
| `minimum` | `String` | `""` | Empty or a canonical decimal integer. |
| `maximum` | `String` | `""` | Empty or a canonical decimal integer. |

### `@KompactReserved`

Repeatable class annotation.

| Parameter | Type | Constraint |
| --- | --- | --- |
| `stableName` | `String` | Stable reserved-range name. |
| `bitOffset` | `Int` | Non-negative, body-relative bit offset. |
| `bitWidth` | `Int` | Positive. |

New writers clear reserved ranges. Checked wrapping rejects nonzero reserved bits.

### `@KompactEnum` and `@KompactCode`

`@KompactEnum(bitWidth)` targets an enum class. `bitWidth` is `1..32` and must equal the field width.

Every enum entry has `@KompactCode(stableName, code)`. Codes are explicit, unique, non-negative, and fit the enum width. Gaps are allowed. Checked wrapping and writes reject undeclared codes.

### `@KompactBytes`

`@KompactBytes(count)` targets a property. `count` is positive and the field width is exactly `count * 8`.

Generated Kotlin and C APIs use indexed byte access. They do not copy an unaligned byte sequence into another array.

### `@KompactArray`

`@KompactArray(count)` targets a property. `count` is positive. The field width is `count * elementBitWidth`.

Arrays can contain fixed-size scalars or nested schemas. `@KompactOptional` can wrap scalar, byte-sequence, or scalar-array fields. Generated indexed operations validate each index before access or mutation.

### `@KompactOptional`

Targets a property. The first bit is presence; the remaining fixed slot stores the value. An absent value has an all-zero slot. `Optional<Optional<T>>` and optional nested schemas are rejected.

### `@KompactNested`

Target: property.

| Parameter | Type | Constraint |
| --- | --- | --- |
| `registryName` | `String` | Stable name of the referenced schema. |
| `schemaId` | `Int` | Exact referenced schema ID. |
| `version` | `Int` | Exact referenced layout version. |

The nested value embeds only the referenced body, without another envelope. Its field width equals the referenced body size.

## Kotlin carrier mapping

| Kotlin property type | Logical type | Width |
| --- | --- | --- |
| `Boolean` | Boolean | 1 |
| `Byte`, `Short`, `Int`, `Long` | Signed two's-complement integer | `2..carrier bits` |
| `UByte`, `UShort`, `UInt`, `ULong` | Unsigned integer | `1..carrier bits` |
| Annotated enum | Enum | `1..32` |
| `Float` | IEEE binary32 | 32 |
| `Double` | IEEE binary64 | 64 |
| `ByteArray` with `@KompactBytes` | Fixed byte sequence | `8 * count` |
| Scalar with `@KompactArray` | Fixed array | `count * element width` |
| Schema interface with `@KompactNested` | Nested schema | Referenced body width |

## Generated Kotlin declarations

For `SwitchPacketSchema`, generation creates:

- `SwitchPacket`, a stateless facade;
- `SwitchPacketView`, a read-only value class;
- `SwitchPacketWriter`, a mutable value class when the registry status is `active`.

### Facade constants

| Constant | Type | Meaning |
| --- | --- | --- |
| `SCHEMA_ID` | `Int` | Registry schema ID. |
| `LAYOUT_VERSION` | `Int` | Layout version. |
| `BODY_BIT_SIZE` | `Int` | Declared body bits. |
| `PACKET_BYTE_SIZE` | `Int` | Exact envelope-plus-body transport size. |
| `DESCRIPTOR_SHA256` | `String` | SHA-256 of canonical descriptor bytes. |

### Facade functions

| Function | Result | Behavior |
| --- | --- | --- |
| `wrap(packet)` | `KompactDecodeResult<View>` | Validates minimum envelope length, identity, version, exact packet length, transport-tail bits, and the complete body. |
| `initialize(packet)` | `KompactDecodeResult<Writer>` | Active schemas only. Requires exact length, clears the packet, and writes the envelope. |
| `edit(packet)` | `KompactDecodeResult<Writer>` | Active schemas only. Runs `wrap` before returning a writer over the same array. |

Validation returns the first failure in protocol order. Body validation follows increasing bit offsets, descends into nested values, and visits array indices in ascending order.

### View members

- scalar fields: direct `val` properties;
- bytes and arrays: indexed functions;
- optional scalar: `hasX` and `xOr(defaultValue)`;
- optional bytes or arrays: `hasX` and `xOr(index, defaultValue)`;
- nested fields: parent-prefixed flattened members;
- nested arrays: flattened members with every required index parameter;
- `contentEquals` and `contentHashCode`: explicit packet-content comparison.

Views are live and non-owning. Direct getters do not repeat semantic validation. External mutation after `wrap` violates the trusted-view contract.

### Writer members

Active schemas generate `writeX` functions, optional `clearX` functions, and `view()`. Fallible writes return `KompactWriteError?`; `null` means success. A rejected write leaves packet bits unchanged.

Decode-only schemas generate the facade constants, `wrap`, and the view. They do not generate `initialize`, `edit`, writer declarations, or write functions.

## Generated C99 declarations

Each schema version creates `<stable_name>_v<version>.h`, which includes `kompact_runtime.h`.

The schema header provides:

- include and runtime-interface compatibility guards;
- generator and descriptor-fingerprint macros;
- schema ID, layout version, body-bit, packet-byte, field-offset, field-width, count, and enum-code constants;
- pointer-only view handles;
- pointer-only writer handles for active schemas;
- checked `wrap`, `initialize`, and `edit` functions according to lifecycle status;
- direct scalar getters;
- status-returning indexed operations;
- optional `has_`, `_or`, `write_`, and `clear_` functions;
- parent-prefixed flattened nested accessors;
- writer-to-view conversion for active schemas.

C functions return `kompact_status_t`. A failed factory, indexed operation, or write leaves packet bits and caller output storage unchanged.

See the [runtime API reference](runtime-api.md) for shared statuses and low-level operations. See [How to generate Kotlin and C99 interfaces](../how-to/generate-kotlin-and-c.md) for the production workflow.
