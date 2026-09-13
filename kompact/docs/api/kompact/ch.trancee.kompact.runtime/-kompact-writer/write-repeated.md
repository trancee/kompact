//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactWriter](index.md)/[writeRepeated](write-repeated.md)

# writeRepeated

[common]\
fun [writeRepeated](write-repeated.md)(count: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), countWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 8, block: [KompactWriter](index.md).() -&gt; [Unit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-unit/index.html))

Writes a count-prefixed repeat: `<count><elem₀>…<elem_{count-1}>` where each element is produced by one invocation of [block](write-repeated.md) against this writer (Ticket 05). [countWidth](write-repeated.md) must be one of [KompactFraming.VALID_PREFIX_WIDTHS](../-kompact-framing/-v-a-l-i-d_-p-r-e-f-i-x_-w-i-d-t-h-s.md).