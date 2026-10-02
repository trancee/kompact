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
| [contains](index.md#-1071816952%2FFunctions%2F-476770652) | [common]<br>open operator override fun [contains](index.md#-1071816952%2FFunctions%2F-476770652)(element: [T](index.md)): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [containsAll](index.md#707016467%2FFunctions%2F-476770652) | [common]<br>open override fun [containsAll](index.md#707016467%2FFunctions%2F-476770652)(elements: [Collection](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-collection/index.html)&lt;[T](index.md)&gt;): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [equals](index.md#-1417571092%2FFunctions%2F-476770652) | [common]<br>open operator override fun [equals](index.md#-1417571092%2FFunctions%2F-476770652)(other: [Any](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-any/index.html)?): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [get](get.md) | [common]<br>open operator override fun [get](get.md)(index: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [T](index.md) |
| [getElementSlice](get-element-slice.md) | [common]<br>fun [getElementSlice](get-element-slice.md)(index: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [KompactFrameResult](../-kompact-frame-result/index.md)&lt;[KompactByteSlice](../-kompact-byte-slice/index.md)&gt;<br>Borrow a length-prefixed element payload without copying; fixed-width elements return a typed failure. |
| [getResult](get-result.md) | [common]<br>fun [getResult](get-result.md)(index: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [KompactFrameResult](../-kompact-frame-result/index.md)&lt;[T](index.md)&gt;<br>Decode one element with a typed failure instead of throwing on malformed content. |
| [hashCode](index.md#-1257825670%2FFunctions%2F-476770652) | [common]<br>open override fun [hashCode](index.md#-1257825670%2FFunctions%2F-476770652)(): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [indexOf](index.md#650270306%2FFunctions%2F-476770652) | [common]<br>open override fun [indexOf](index.md#650270306%2FFunctions%2F-476770652)(element: [T](index.md)): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [isEmpty](index.md#-1372511710%2FFunctions%2F-476770652) | [common]<br>open override fun [isEmpty](index.md#-1372511710%2FFunctions%2F-476770652)(): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [iterator](index.md#1248735623%2FFunctions%2F-476770652) | [common]<br>open operator override fun [iterator](index.md#1248735623%2FFunctions%2F-476770652)(): [Iterator](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-iterator/index.html)&lt;[T](index.md)&gt; |
| [lastIndexOf](index.md#-720747284%2FFunctions%2F-476770652) | [common]<br>open override fun [lastIndexOf](index.md#-720747284%2FFunctions%2F-476770652)(element: [T](index.md)): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [listIterator](index.md#158404745%2FFunctions%2F-476770652) | [common]<br>open override fun [listIterator](index.md#158404745%2FFunctions%2F-476770652)(): [ListIterator](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-list-iterator/index.html)&lt;[T](index.md)&gt;<br>open override fun [listIterator](index.md#1759949543%2FFunctions%2F-476770652)(index: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [ListIterator](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-list-iterator/index.html)&lt;[T](index.md)&gt; |
| [subList](index.md#-1763229096%2FFunctions%2F-476770652) | [common]<br>open override fun [subList](index.md#-1763229096%2FFunctions%2F-476770652)(fromIndex: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), toIndex: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [List](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[T](index.md)&gt; |
| [toString](index.md#1396628617%2FFunctions%2F-476770652) | [common]<br>open override fun [toString](index.md#1396628617%2FFunctions%2F-476770652)(): [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html) |