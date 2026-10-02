//[kompact](../../../index.md)/[ch.trancee.kompact.annotations](../index.md)/[KompactField](index.md)

# KompactField

@[Target](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.annotation/-target/index.html)(allowedTargets = [[AnnotationTarget.PROPERTY](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.annotation/-annotation-target/-p-r-o-p-e-r-t-y/index.html)])

annotation class [KompactField](index.md)(val bitOffset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0, val bitWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0, val signed: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false, val lengthPrefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 8, val isNested: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false, val repeatCountWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 8, val enumWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0, val defaultValue: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html) = &quot;&quot;, val order: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = -1)

Describes a property in a fixed-layout or sequential framed schema.

The Kompact KSP processor reads these to generate the backing read/write logic. Fixed-layout offsets are LSB-first and must not overlap; dense packing is not required, so gap bits are permitted.

Framed fields use order (contiguous from zero), not bitOffset. Scalar fields specify bitWidth; strings, blobs and nested fields use a prefix width. Repeats are `List<T>` with repeatCountWidth, plus bitWidth for scalar elements or lengthPrefixWidth for variable-length elements. Fixed-layout annotations retain their original bit-offset meaning.

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
| defaultValue | string-encoded default metadata; it is not currently         supported by code generation |
| order | zero-based sequential position in a framed schema; unused in fixed layouts |

## Properties

| Name | Summary |
|---|---|
| [bitOffset](bit-offset.md) | [common]<br>val [bitOffset](bit-offset.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0 |
| [bitWidth](bit-width.md) | [common]<br>val [bitWidth](bit-width.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0 |
| [defaultValue](default-value.md) | [common]<br>val [defaultValue](default-value.md): [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html) |
| [enumWidth](enum-width.md) | [common]<br>val [enumWidth](enum-width.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0 |
| [isNested](is-nested.md) | [common]<br>val [isNested](is-nested.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false |
| [lengthPrefixWidth](length-prefix-width.md) | [common]<br>val [lengthPrefixWidth](length-prefix-width.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 8 |
| [order](order.md) | [common]<br>val [order](order.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [repeatCountWidth](repeat-count-width.md) | [common]<br>val [repeatCountWidth](repeat-count-width.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 8 |
| [signed](signed.md) | [common]<br>val [signed](signed.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false |