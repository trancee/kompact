//[kompact](../../../index.md)/[ch.trancee.kompact.annotations](../index.md)/[KompactModel](index.md)

# KompactModel

[common]\
@[Target](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.annotation/-target/index.html)(allowedTargets = [[AnnotationTarget.CLASS](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.annotation/-annotation-target/-c-l-a-s-s/index.html)])

annotation class [KompactModel](index.md)(val mutable: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false, val framed: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false)

Marks a regular class as a Kompact binary schema.

The processor treats the annotated declaration as source schema metadata and generates one common `<Name>View` per schema. The default fixed layout generates a `@JvmInline` value class over a `ByteArray`; framed generates a bounded regular view class for sequential fields. This annotation is retained only at source level and is not a runtime dependency. mutable adds a `Mutable<Name>View` sibling for fixed-layout models; mutable framed models are rejected.

## Properties

| Name | Summary |
|---|---|
| [framed](framed.md) | [common]<br>val [framed](framed.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false |
| [mutable](mutable.md) | [common]<br>val [mutable](mutable.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false |