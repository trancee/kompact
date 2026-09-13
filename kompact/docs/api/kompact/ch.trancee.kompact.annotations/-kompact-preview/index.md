//[kompact](../../../index.md)/[ch.trancee.kompact.annotations](../index.md)/[KompactPreview](index.md)

# KompactPreview

[common]\
@[RequiresOptIn](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-requires-opt-in/index.html)(message = &quot;Kompact codegen APIs are API-preview: expect binary/source changes across releases.&quot;, level = [RequiresOptIn.Level.WARNING](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-requires-opt-in/-level/-w-a-r-n-i-n-g/index.html))

@[Target](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.annotation/-target/index.html)(allowedTargets = [[AnnotationTarget.CLASS](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.annotation/-annotation-target/-c-l-a-s-s/index.html), [AnnotationTarget.FUNCTION](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.annotation/-annotation-target/-f-u-n-c-t-i-o-n/index.html), [AnnotationTarget.PROPERTY](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.annotation/-annotation-target/-p-r-o-p-e-r-t-y/index.html)])

annotation class [KompactPreview](index.md)

API-preview marker for the Kompact codegen surface (model layout contracts and field metadata). The surface is not yet binary/source-stable (Ticket 07): generated declarations carry this marker via `@file:OptIn(KompactPreview::class)`; hand-written code that references the markers is warned until graduation.

`@RequiresOptIn` markers may not target `FILE` or `TYPE_USAGE`; this marker is confined to class/function/property declarations (the codegen annotations that propagate it).