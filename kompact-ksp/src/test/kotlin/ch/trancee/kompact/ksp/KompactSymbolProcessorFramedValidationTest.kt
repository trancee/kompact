package ch.trancee.kompact.ksp

import ch.trancee.kompact.ksp.testing.FakeKSAnnotation
import ch.trancee.kompact.ksp.testing.FakeKSClassDeclaration
import ch.trancee.kompact.ksp.testing.FakeResolver
import kotlin.test.Test
import kotlin.test.assertTrue

class KompactSymbolProcessorFramedValidationTest {
    @Test
    fun process_framedModel_withSparseOrder_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field("first", "kotlin.Int", order = 0, bitWidth = 5),
                field("third", "kotlin.Int", order = 2, bitWidth = 5),
            ),
        )
    }

    @Test
    fun process_framedModel_withDuplicateOrder_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field("first", "kotlin.Int", order = 0, bitWidth = 5),
                field("alsoFirst", "kotlin.Int", order = 0, bitWidth = 5),
            ),
        )
    }

    @Test
    fun process_framedModel_withNonzeroBitOffset_reportsDiagnosticWithoutOutput() {
        assertInvalid(listOf(field("value", "kotlin.Int", order = 0, bitWidth = 5, bitOffset = 1)))
    }

    @Test
    fun process_framedModel_withInvalidPrefixWidth_reportsDiagnosticWithoutOutput() {
        assertInvalid(listOf(field("label", "kotlin.String", order = 0, lengthPrefixWidth = 7)))
    }

    @Test
    fun process_framedModel_withInvalidRepeatCountWidth_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field(
                    "values",
                    "kotlin.collections.List",
                    order = 0,
                    bitWidth = 5,
                    repeatCountWidth = 7,
                    typeArguments = listOf("kotlin.Int"),
                ),
            ),
        )
    }

    @Test
    fun process_framedModel_withMissingOrder_reportsDiagnosticWithoutOutput() {
        assertInvalid(listOf(field("value", "kotlin.Int", order = null, bitWidth = 5)))
    }

    @Test
    fun process_framedModel_withWrongOrderType_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field(
                    "value",
                    "kotlin.Int",
                    order = 0,
                    bitWidth = 5,
                    annotationArgs = mapOf("order" to "first"),
                ),
            ),
        )
    }

    @Test
    fun process_framedModel_withWrongPrefixType_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field(
                    "label",
                    "kotlin.String",
                    order = 0,
                    annotationArgs = mapOf("lengthPrefixWidth" to "8"),
                ),
            ),
        )
    }

    @Test
    fun process_framedModel_withWrongBooleanFieldArgument_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field(
                    "value",
                    "kotlin.Int",
                    order = 0,
                    bitWidth = 5,
                    annotationArgs = mapOf("isNested" to "false"),
                ),
            ),
        )
    }

    @Test
    fun process_framedModel_withWrongSignednessArgument_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field(
                    "value",
                    "kotlin.Int",
                    order = 0,
                    bitWidth = 5,
                    annotationArgs = mapOf("signed" to "false"),
                ),
            ),
        )
    }

    @Test
    fun process_framedModel_withWrongSignedFieldArgument_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field(
                    "value",
                    "kotlin.Int",
                    order = 0,
                    bitWidth = 5,
                    annotationArgs = mapOf("signed" to "false"),
                ),
            ),
        )
    }

    @Test
    fun process_framedModel_withWrongEnumFieldArgument_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field(
                    "value",
                    "kotlin.Int",
                    order = 0,
                    bitWidth = 5,
                    annotationArgs = mapOf("enumWidth" to "0"),
                ),
            ),
        )
    }

    @Test
    fun process_framedModel_withWrongDefaultFieldArgument_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field(
                    "value",
                    "kotlin.Int",
                    order = 0,
                    bitWidth = 5,
                    annotationArgs = mapOf("defaultValue" to 0),
                ),
            ),
        )
    }

    @Test
    fun process_framedModel_withNullableOptionalArgsUsesSafeDefaults() {
        val (processor, codeGen, logger) = createTestSetup(mode = "jvm")
        val model =
            FakeKSClassDeclaration(
                simpleNameStr = "DefaultedFrame",
                packageNameStr = "ch.trancee.test",
                properties =
                    listOf(
                        field(
                            "value",
                            "kotlin.Int",
                            order = 0,
                            bitWidth = 5,
                            annotationArgs =
                                mapOf(
                                    "signed" to null,
                                    "isNested" to null,
                                    "enumWidth" to 0,
                                    "defaultValue" to "",
                                ),
                        ),
                    ),
                declAnnotations =
                    listOf(
                        FakeKSAnnotation(
                            "ch.trancee.kompact.annotations.KompactModel",
                            mapOf("framed" to true),
                        ),
                    ),
            )

        processor.process(FakeResolver(listOf(model)))

        assertTrue(logger.errors.isEmpty(), "omitted optional arguments should use declared defaults")
        assertTrue(codeGen.generatedFiles.containsKey("ch.trancee.test.DefaultedFrameViewGenJvm.kt"))
    }

    @Test
    fun process_framedModel_withMutableModel_reportsDiagnosticWithoutOutput() {
        assertInvalid(listOf(field("value", "kotlin.Int", order = 0, bitWidth = 5)), modelMutable = true)
    }

    @Test
    fun process_modelWithWrongFramedArgument_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(field("value", "kotlin.Int", order = 0, bitWidth = 5)),
            modelFramed = "true",
        )
    }

    @Test
    fun process_modelWithWrongMutableArgument_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(field("value", "kotlin.Int", order = 0, bitWidth = 5)),
            modelMutable = "true",
        )
    }

    @Test
    fun process_framedModel_withMutableField_reportsDiagnosticWithoutOutput() {
        assertInvalid(listOf(field("value", "kotlin.Int", order = 0, bitWidth = 5, mutable = true)))
    }
}
