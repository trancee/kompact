# ADR-0001: Bitstream and scalar wire format

Status: accepted

## Context

Kompact must encode fixed-size BLE payloads without alignment padding and produce identical results from common Kotlin on Android and iOS and from generated C99 helpers. Host byte order, Kotlin `Byte` signedness, C signed-shift behavior, enum declaration order, and NaN payload differences cannot define the wire format.

## Decision

A bit position `p` addresses byte `p / 8` and bit `p % 8` within that byte. Bit zero is the least-significant bit of byte zero. Field bit `i` maps to stream bit `bitOffset + i`, so a field's first bit is its value's least-significant bit. Fields may begin at any bit and cross byte boundaries. The format adds no alignment or implicit padding.

Scalar representations are:

- Boolean fields have width 1. Zero is false and one is true.
- Unsigned integer fields have widths from 1 through 64 and represent `0..2^width-1`.
- Signed integer fields have widths from 2 through 64 and use exact-width two's complement. Readers sign-extend into the selected Kotlin or C carrier.
- Enum fields have an explicit width from 1 through 32. Every entry has a unique, explicit, non-negative code that fits the width. Code gaps are valid. Checked wrapping rejects undeclared codes.
- Floating fields have width 32 or 64 and encode IEEE-754 binary32 or binary64 raw bits at any bit offset. Readers accept every bit pattern. Writers map every NaN to positive quiet NaN `0x7FC00000` or `0x7FF8000000000000`; infinities and signed zero retain their raw representations.
- Reserved ranges contain zero. New writers clear them, and checked wrapping rejects nonzero reserved bits.

Width zero, out-of-bounds ranges, and values outside the declared representation are invalid. A writer validates the complete operation before mutation, changes only the target field, and preserves every other bit. Checked wrapping validates enum and reserved-bit constraints once. Scalar getters then use only deterministic byte, mask, shift, combine, and sign-extension operations.

Kotlin converts each source byte with `toInt() and 0xFF` before shifting. C helpers use unsigned exact-width operations. Neither implementation may rely on host byte order or signed right shifts.

## Alternatives

MSB-first numbering was rejected because it complicates the direct mask-and-shift mapping without improving this protocol. Byte alignment was rejected because it violates the payload-size objective. Kotlin enum ordinals and inferred widths were rejected because source edits could silently change the wire contract. Tolerating unknown enum codes or nonzero reserved bits was rejected because each fixed layout has an explicit version. Preserving arbitrary NaN payloads on write was rejected because it permits multiple emitted encodings for the same semantic NaN.

## Risks

Canonical NaN writes discard NaN sign and payload information. Strict enum and reserved-bit validation requires a new layout version for extensions that use those codes or bits. A caller can mutate a shared `ByteArray` after checked wrapping and violate validated invariants; the generated-interface decision must define aliasing and trust rules. Allocation and latency claims for 64-bit and cross-byte operations still require target-specific measurements.

## Migration

No released wire format exists. Once a layout version ships, these scalar rules are immutable for that version. Any incompatible change creates a new layout version, and decoders must dispatch versions explicitly rather than reinterpret existing payloads.
