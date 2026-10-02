//[kompact](../../../index.md)/[ch.trancee.kompact.annotations](../index.md)/[KompactModel](index.md)

# KompactModel

[common]\
@[Target](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.annotation/-target/index.html)(allowedTargets = [[AnnotationTarget.CLASS](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.annotation/-annotation-target/-c-l-a-s-s/index.html)])

annotation class [KompactModel](index.md)(val mutable: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false, val framed: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false)

Marks a Kompact binary schema. The default fixed layout generates platform value classes; framed generates bounded regular classes for sequential fields.

The processor treats the annotated declaration as source schema metadata. Fixed-layout schemas generate platform value classes over a `ByteArray`; framed schemas generate bounded view classes. This annotation is retained only at source level and is not a runtime dependency. mutable applies to fixed-layout models; mutable framed models are rejected.

## Properties

| Name | Summary |
|---|---|
| [framed](framed.md) | [common]<br>val [framed](framed.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false |
| [mutable](mutable.md) | [common]<br>val [mutable](mutable.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false |