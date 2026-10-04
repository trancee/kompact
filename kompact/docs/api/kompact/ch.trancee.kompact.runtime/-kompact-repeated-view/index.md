//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactRepeatedView](index.md)

# KompactRepeatedView

[common]\
class [KompactRepeatedView](index.md)&lt;[T](index.md)&gt; : [AbstractList](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-abstract-list/index.html)&lt;[T](index.md)&gt; 

Read-only lazy list; fixed elements index directly, variable ones use sparse checkpoints.

Keep the backing wire bytes unchanged while using this view; changing a variable element's prefix invalidates the sparse index validated when the view was created.

## Properties

| Name | Summary |
|---|---|
| [size](size.md) | [common]<br>open override val [size](size.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |

## Functions

| Name | Summary |
|---|---|
| [get](get.md) | [common]<br>open operator override fun [get](get.md)(index: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [T](index.md) |
| [getElementSlice](get-element-slice.md) | [common]<br>fun [getElementSlice](get-element-slice.md)(index: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [KompactFrameResult](../-kompact-frame-result/index.md)&lt;[KompactByteSlice](../-kompact-byte-slice/index.md)&gt;<br>Borrow a length-prefixed element payload without copying; fixed-width elements return a typed failure. |
| [getResult](get-result.md) | [common]<br>fun [getResult](get-result.md)(index: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [KompactFrameResult](../-kompact-frame-result/index.md)&lt;[T](index.md)&gt;<br>Decode one element with a typed failure instead of throwing on malformed content. |