//[kompact](../../../index.md)/[ch.trancee.kompact.annotations](../index.md)/[KompactField](index.md)

# KompactField

@[Target](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.annotation/-target/index.html)(allowedTargets = [[AnnotationTarget.PROPERTY](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.annotation/-annotation-target/-p-r-o-p-e-r-t-y/index.html)])

annotation class [KompactField](index.md)(val bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), val bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), val signed: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false, val lengthPrefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 8, val isNested: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false, val repeatCountWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 8, val enumWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0, val defaultValue: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html) = &quot;&quot;)

Documents a property's bit position and width in the packed `ByteArray`.

The Kompact KSP processor reads these to generate the backing read/write logic (Tickets 04, 05, 06, 09). Offsets are LSB-first (Ticket 01) and must not overlap (Ticket 06: the processor enforces this; dense packing is not required — gap bits are permitted for future expansion).

The length-prefix / nesting / repeat / enum members are v1 schema metadata consumed by codegen; they carry safe defaults so a plain `@KompactField(bitOffset, bitWidth)` scalar declaration remains valid.

#### Parameters

common

| | |
|---|---|
| bitOffset | zero-based LSB-first start bit of the field |
| bitWidth | number of bits occupied by the field (1..64; for 32-bit use 32) |
| signed | true for two's-complement, false for unsigned magnitude (v1-spec-04: type set) |
| lengthPrefixWidth | fixed-width LE byte-count prefix width in {8,16,32}         used when the field is a string/blob/nested/repeat (Ticket 05) |
| isNested | true when the field is a length-delimited composite region |
| repeatCountWidth | fixed-width LE count prefix width in {8,16,32}         for repeated fields |
| enumWidth | bit width of an enum/ordinal (0 = not an enum) |
| defaultValue | string-encoded default used by the generated ctor/accessor         when the backing region is absent or zero-filled (Ticket 04) |

## Properties

| Name | Summary |
|---|---|
| [bitOffset](bit-offset.md) | [common]<br>val [bitOffset](bit-offset.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [bitWidth](bit-width.md) | [common]<br>val [bitWidth](bit-width.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [defaultValue](default-value.md) | [common]<br>val [defaultValue](default-value.md): [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html) |
| [enumWidth](enum-width.md) | [common]<br>val [enumWidth](enum-width.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0 |
| [isNested](is-nested.md) | [common]<br>val [isNested](is-nested.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false |
| [lengthPrefixWidth](length-prefix-width.md) | [common]<br>val [lengthPrefixWidth](length-prefix-width.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 8 |
| [repeatCountWidth](repeat-count-width.md) | [common]<br>val [repeatCountWidth](repeat-count-width.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 8 |
| [signed](signed.md) | [common]<br>val [signed](signed.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false |