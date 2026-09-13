//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactWriter](index.md)/[writeBlob](write-blob.md)

# writeBlob

[common]\
fun [writeBlob](write-blob.md)(countWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), bytes: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html))

Writes a length-prefixed blob: `<prefix><bytes>` (Ticket 05).