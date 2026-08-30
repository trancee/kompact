# ADR-0003: Fixed aggregate and optional layouts

Status: accepted

## Context

Kompact v1 needs fixed byte sequences, arrays, optional values, and reusable nested schemas without losing compile-time offsets or adding alignment padding. Kotlin and C must derive the same element positions, absence encoding, total body size, and validation behavior. Variable-size values remain outside v1.

## Decision

Kompact v1 supports this recursive, fixed-size type grammar:

- `Bytes<N>` has positive length and bit size `8 * N`. Logical byte `j` occupies bits `offset + 8 * j` through `offset + 8 * j + 7` under ADR-0001's LSB-first rule.
- `Array<N, T>` has positive count and bit size `N * bitSize(T)`. Element `i` begins at `offset + i * bitSize(T)`.
- `Optional<T>` has bit size `1 + bitSize(T)`. Its presence bit comes first, followed immediately by the fixed value slot. `Optional<Optional<T>>` is invalid.
- `Nested<Schema, Version>` embeds only the exact child body. It carries no child envelope. Its size comes from the protocol registry, and the parent descriptor fixes the child schema ID and layout version.

Arrays may contain any fixed-size scalar, byte sequence, array, optional value, or nested schema. Schema references must be acyclic. A child wire or semantic change requires a new child version and a new parent layout version.

Every value may begin at any bit offset. Aggregates, elements, and nested bodies have no alignment padding. Array indices increase toward higher stream offsets. Every body bit belongs to a field or an explicit reserved range; generation rejects overlaps and implicit gaps.

An absent optional value has presence zero and an all-zero value slot. Setting a field to absent clears the entire slot. Checked wrapping rejects an absent field whose slot contains any nonzero bit. Presence one validates and decodes the slot under the wrapped type's normal rules.

Checked wrapping validates the entire fixed body once, including enum codes, reserved ranges, optional slots, every array element, and every nested body. Direct getters and indexed reads do not repeat semantic validation. Array indices, size multiplication, offset addition, and packet-size calculations are checked for overflow before generation or access.

The wire format permits finite bodies addressable by non-negative Kotlin `Int` bit offsets. Each protocol registry records a lower maximum packet byte size, and generation rejects schemas whose envelope plus body exceeds it. A zero-bit top-level body is valid and produces an envelope-only packet.

An unaligned fixed byte sequence remains a non-owning value. Its generated Kotlin interface must expose indexed computed access rather than allocate a shifted `ByteArray` copy. The generated Kotlin and C interface decisions will define accessor names and representations without changing this layout.

Variable-length arrays, variable-length byte sequences, strings, and recursive schema cycles are not supported in v1.

## Alternatives

Byte-aligning aggregates was rejected because it introduces up to seven padding bits before each value. Byte-stride array elements were rejected because narrow elements would consume more bits than declared. Nested envelopes were rejected because the parent already fixes the child identity and version. A schema-wide optional bitmap was rejected because it couples local fields to a global order without saving bits. Sentinel absence values were rejected because they remove valid values and do not apply uniformly. Ignored optional slots and implicit gaps were rejected because they permit multiple encodings of one semantic payload.

## Risks

Validating every element and nested body makes checked wrapping proportional to schema size even though later reads are direct. Unaligned byte-sequence access requires shift and combine operations for each logical byte. Body-only nesting couples a parent version to each child version. Deep but acyclic aggregate composition can increase generated code size and validation depth; performance and code-size budgets must cover representative nested schemas. The non-owning buffer can still be mutated after validation, so the generated-interface contract must define aliasing and concurrency limits.

## Migration

No released aggregate layout exists. After release, changing count, element type, optionality, child version, offset, reserved coverage, or aggregate composition creates a new parent layout version. Variable-size values require a future wire-format decision and cannot be introduced by reinterpreting a v1 fixed aggregate.
