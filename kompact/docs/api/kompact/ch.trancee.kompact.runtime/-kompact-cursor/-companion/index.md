//[kompact](../../../../index.md)/[ch.trancee.kompact.runtime](../../index.md)/[KompactCursor](../index.md)/[Companion](index.md)

# Companion

[common]\
object [Companion](index.md)

## Properties

| Name | Summary |
|---|---|
| [STATUS_BAD_LENGTH_PREFIX](-s-t-a-t-u-s_-b-a-d_-l-e-n-g-t-h_-p-r-e-f-i-x.md) | [common]<br>const val [STATUS_BAD_LENGTH_PREFIX](-s-t-a-t-u-s_-b-a-d_-l-e-n-g-t-h_-p-r-e-f-i-x.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 5<br>A length prefix is malformed or cannot be represented by cursor offsets. |
| [STATUS_BOUNDS_ERROR](-s-t-a-t-u-s_-b-o-u-n-d-s_-e-r-r-o-r.md) | [common]<br>const val [STATUS_BOUNDS_ERROR](-s-t-a-t-u-s_-b-o-u-n-d-s_-e-r-r-o-r.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 3<br>An operation exceeded the cursor's bounded region. |
| [STATUS_INVALID_ARGUMENT](-s-t-a-t-u-s_-i-n-v-a-l-i-d_-a-r-g-u-m-e-n-t.md) | [common]<br>const val [STATUS_INVALID_ARGUMENT](-s-t-a-t-u-s_-i-n-v-a-l-i-d_-a-r-g-u-m-e-n-t.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 7<br>Mutable cursors or source and destination buffers alias incompatibly. |
| [STATUS_INVALID_BOUNDS](-s-t-a-t-u-s_-i-n-v-a-l-i-d_-b-o-u-n-d-s.md) | [common]<br>const val [STATUS_INVALID_BOUNDS](-s-t-a-t-u-s_-i-n-v-a-l-i-d_-b-o-u-n-d-s.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 1<br>A reset region was outside the representable buffer bit bounds. |
| [STATUS_INVALID_UTF8](-s-t-a-t-u-s_-i-n-v-a-l-i-d_-u-t-f8.md) | [common]<br>const val [STATUS_INVALID_UTF8](-s-t-a-t-u-s_-i-n-v-a-l-i-d_-u-t-f8.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 8<br>A string payload contains malformed UTF-8. |
| [STATUS_INVALID_VALUE](-s-t-a-t-u-s_-i-n-v-a-l-i-d_-v-a-l-u-e.md) | [common]<br>const val [STATUS_INVALID_VALUE](-s-t-a-t-u-s_-i-n-v-a-l-i-d_-v-a-l-u-e.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 4<br>A value cannot be represented by the requested wire width. |
| [STATUS_INVALID_WIDTH](-s-t-a-t-u-s_-i-n-v-a-l-i-d_-w-i-d-t-h.md) | [common]<br>const val [STATUS_INVALID_WIDTH](-s-t-a-t-u-s_-i-n-v-a-l-i-d_-w-i-d-t-h.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 2<br>A bit width or prefix width is unsupported. |
| [STATUS_OK](-s-t-a-t-u-s_-o-k.md) | [common]<br>const val [STATUS_OK](-s-t-a-t-u-s_-o-k.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0<br>Operation completed successfully. |
| [STATUS_TRUNCATED_INPUT](-s-t-a-t-u-s_-t-r-u-n-c-a-t-e-d_-i-n-p-u-t.md) | [common]<br>const val [STATUS_TRUNCATED_INPUT](-s-t-a-t-u-s_-t-r-u-n-c-a-t-e-d_-i-n-p-u-t.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 10<br>A repeated element is truncated or its payload crosses its region. |
| [STATUS_UNALIGNED_REGION](-s-t-a-t-u-s_-u-n-a-l-i-g-n-e-d_-r-e-g-i-o-n.md) | [common]<br>const val [STATUS_UNALIGNED_REGION](-s-t-a-t-u-s_-u-n-a-l-i-g-n-e-d_-r-e-g-i-o-n.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 6<br>A byte-oriented operation began at a non-byte-aligned position. |
| [STATUS_WORKSPACE_TOO_SMALL](-s-t-a-t-u-s_-w-o-r-k-s-p-a-c-e_-t-o-o_-s-m-a-l-l.md) | [common]<br>const val [STATUS_WORKSPACE_TOO_SMALL](-s-t-a-t-u-s_-w-o-r-k-s-p-a-c-e_-t-o-o_-s-m-a-l-l.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 9<br>The caller-owned repeat workspace has too few checkpoint slots. |