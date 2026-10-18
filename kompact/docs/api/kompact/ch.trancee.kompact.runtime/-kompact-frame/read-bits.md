//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactFrame](index.md)/[readBits](read-bits.md)

# readBits

[common]\
fun [readBits](read-bits.md)(width: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)

Read 1..64 low bits in wire order, without crossing this frame's boundary.

#### Throws

| | |
|---|---|
| [IllegalArgumentException](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-illegal-argument-exception/index.html) | if [width](read-bits.md) is not in 1..64. |
| [KompactDecodeException](../-kompact-decode-exception/index.md) | with [KompactDecodeError.BoundsError](../-kompact-decode-error/-bounds-error/index.md) if fewer than [width](read-bits.md) bits remain. |