//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactCursor](index.md)/[readVariableRepeat](read-variable-repeat.md)

# readVariableRepeat

[common]\
fun [readVariableRepeat](read-variable-repeat.md)(countPrefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), elementPrefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), workspace: [KompactRepeatWorkspace](../-kompact-repeat-workspace/index.md)): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)

Validate a count-prefixed variable-width repeat and build its sparse index in caller-owned [workspace](read-variable-repeat.md) storage.

The workspace needs one checkpoint slot per 64-element block, rounded up. Insufficient capacity returns [STATUS_WORKSPACE_TOO_SMALL](-companion/-s-t-a-t-u-s_-w-o-r-k-s-p-a-c-e_-t-o-o_-s-m-a-l-l.md) without advancing this cursor or changing the workspace binding.