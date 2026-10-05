//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactRepeatWorkspace](index.md)

# KompactRepeatWorkspace

class [KompactRepeatWorkspace](index.md)(checkpointStorage: [IntArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int-array/index.html))

Caller-owned sparse-index storage for one variable- or fixed-width repeat.

The workspace retains checkpointStorage and the buffer bound by the last successful repeat read. Keep that buffer unchanged while using the index. Reusing this workspace replaces its current repeat binding.

#### Parameters

common

| | |
|---|---|
| checkpointStorage | caller-owned slots for sparse bit-offset checkpoints |

## Constructors

| | |
|---|---|
| [KompactRepeatWorkspace](-kompact-repeat-workspace.md) | [common]<br>constructor(checkpointStorage: [IntArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int-array/index.html)) |

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [common]<br>object [Companion](-companion/index.md) |

## Properties

| Name | Summary |
|---|---|
| [capacity](capacity.md) | [common]<br>val [capacity](capacity.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)<br>Number of sparse checkpoints that fit in the supplied storage. |
| [count](count.md) | [common]<br>var [count](count.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)<br>Element count from the last successful repeat read. |
| [endBit](end-bit.md) | [common]<br>var [endBit](end-bit.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)<br>Exclusive bit offset after the last repeat element. |
| [startBit](start-bit.md) | [common]<br>var [startBit](start-bit.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)<br>Inclusive bit offset of the first repeat element. |