//[kompact](../../../../index.md)/[ch.trancee.kompact.runtime](../../index.md)/[KompactFrame](../index.md)/[Companion](index.md)/[decode](decode.md)

# decode

[common]\
fun [decode](decode.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), start: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0, end: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = raw.size): [KompactFrameResult](../../-kompact-frame-result/index.md)&lt;[KompactFrame](../index.md)&gt;

Validate a bounded region and return a reader without copying. Direct reads may throw; use the block overload when malformed input should be returned as a result.

[common]\
fun &lt;[T](decode.md)&gt; [decode](decode.md)(raw: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), start: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0, end: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = raw.size, block: ([KompactFrame](../index.md)) -&gt; [T](decode.md)): [KompactFrameResult](../../-kompact-frame-result/index.md)&lt;[T](decode.md)&gt;

Parse an untrusted region and translate only decoder failures into typed results.