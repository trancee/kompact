package ch.trancee.kompact.annotations

/**
 * API-preview marker for the Kompact codegen surface (model layout contracts and
 * field metadata). The surface is not yet binary/source-stable (Ticket 07):
 * generated declarations carry this marker via
 * `@file:OptIn(KompactPreview::class)`; hand-written code that references the
 * markers is warned until graduation.
 *
 * `@RequiresOptIn` markers may not target `FILE` or `TYPE_USAGE`; this marker is
 * confined to class/function/property declarations (the codegen annotations
 * that propagate it).
 */
@RequiresOptIn(
    message = "Kompact codegen APIs are API-preview: expect binary/source changes across releases.",
    level = RequiresOptIn.Level.WARNING,
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
public annotation class KompactPreview
