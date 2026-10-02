//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactRepeatedView](index.md)/[getElementSlice](get-element-slice.md)

# getElementSlice

[common]\
fun [getElementSlice](get-element-slice.md)(index: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [KompactFrameResult](../-kompact-frame-result/index.md)&lt;[KompactByteSlice](../-kompact-byte-slice/index.md)&gt;

Borrow a length-prefixed element payload without copying; fixed-width elements return a typed failure.