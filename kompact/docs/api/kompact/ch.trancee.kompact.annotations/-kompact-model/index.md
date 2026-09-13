//[kompact](../../../index.md)/[ch.trancee.kompact.annotations](../index.md)/[KompactModel](index.md)

# KompactModel

[common]\
@[Target](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.annotation/-target/index.html)(allowedTargets = [[AnnotationTarget.CLASS](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.annotation/-annotation-target/-c-l-a-s-s/index.html)])

annotation class [KompactModel](index.md)

Marks a value class as a Kompact binary schema.

A Kompact schema is a multiplatform `value class` over a single `ByteArray`. The processor reads this annotation to validate field layout at compile time (Ticket 06) and is retained only at source level — it is compile-time metadata, not a runtime dependency (PROMPT §2).