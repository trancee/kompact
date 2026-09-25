//[kompact](../../../../index.md)/[ch.trancee.kompact.runtime](../../index.md)/[BooleanResult](../index.md)/[Companion](index.md)

# Companion

[common]\
expect object [Companion](index.md)

[jvmCommon, native]\
actual object [Companion](index.md)

## Functions

| Name | Summary |
|---|---|
| [failure](failure.md) | [common, jvmCommon, native]<br>[common]<br>expect fun [failure](failure.md)(error: [KompactDecodeError](../../-kompact-decode-error/index.md)): [BooleanResult](../index.md)<br>[jvmCommon, native]<br>actual fun [failure](failure.md)(error: [KompactDecodeError](../../-kompact-decode-error/index.md)): [BooleanResult](../index.md) |
| [success](success.md) | [common, jvmCommon, native]<br>[common]<br>expect fun [success](success.md)(value: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)): [BooleanResult](../index.md)<br>[jvmCommon, native]<br>actual fun [success](success.md)(value: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)): [BooleanResult](../index.md) |