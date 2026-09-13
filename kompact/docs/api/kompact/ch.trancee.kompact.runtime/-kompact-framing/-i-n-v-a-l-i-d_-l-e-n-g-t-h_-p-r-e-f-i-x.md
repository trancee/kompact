//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactFraming](index.md)/[INVALID_LENGTH_PREFIX](-i-n-v-a-l-i-d_-l-e-n-g-t-h_-p-r-e-f-i-x.md)

# INVALID_LENGTH_PREFIX

[common]\
const val [INVALID_LENGTH_PREFIX](-i-n-v-a-l-i-d_-l-e-n-g-t-h_-p-r-e-f-i-x.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)

Sentinel returned by [readLengthPrefix](read-length-prefix.md) when `bitWidth` is invalid or the prefix field overruns `raw` — the sentinel [INVALID_LENGTH_PREFIX](-i-n-v-a-l-i-d_-l-e-n-g-t-h_-p-r-e-f-i-x.md) isolates failure from success without scattering bare sentinel values across call sites. This is the only value [readLengthPrefix](read-length-prefix.md) returns on failure; it is never a valid (non-negative) byte count.