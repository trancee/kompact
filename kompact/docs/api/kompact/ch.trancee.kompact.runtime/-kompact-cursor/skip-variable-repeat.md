//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactCursor](index.md)/[skipVariableRepeat](skip-variable-repeat.md)

# skipVariableRepeat

[common]\
fun [skipVariableRepeat](skip-variable-repeat.md)(countPrefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), elementPrefixWidth: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), workspaceCapacity: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [KompactStatus](../-kompact-status/index.md)

Validate and skip a variable-width repeat without binding workspace state.

[workspaceCapacity](skip-variable-repeat.md) must provide one slot per 64-element block, rounded up. An insufficient capacity returns [KompactStatus.WORKSPACE_TOO_SMALL](../-kompact-status/-w-o-r-k-s-p-a-c-e_-t-o-o_-s-m-a-l-l/index.md).