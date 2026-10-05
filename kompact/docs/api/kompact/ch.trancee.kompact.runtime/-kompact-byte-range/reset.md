//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactByteRange](index.md)/[reset](reset.md)

# reset

[common]\
fun [reset](reset.md)(buffer: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), start: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0, end: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = buffer.size): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)

Rebind this range; invalid byte bounds leave its previous binding intact.