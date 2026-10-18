package ch.trancee.kompact.ksp

import ch.trancee.kompact.ksp.testing.FakeKSAnnotation
import ch.trancee.kompact.ksp.testing.FakeKSClassDeclaration
import ch.trancee.kompact.ksp.testing.FakeResolver
import kotlin.test.Test
import kotlin.test.assertTrue

class KompactSymbolProcessorFramedTypeValidationTest {
    @Test
    fun process_framedModel_withStarProjectedRepeat_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field(
                    "values",
                    "kotlin.collections.List",
                    order = 0,
                    typeArguments = listOf(null),
                ),
            ),
        )
    }

    @Test
    fun process_framedModel_withRawRepeat_reportsDiagnosticWithoutOutput() {
        assertInvalid(listOf(field("values", "kotlin.collections.List", order = 0)))
    }

    @Test
    fun process_framedModel_withWrongRepeatArity_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field(
                    "values",
                    "kotlin.collections.List",
                    order = 0,
                    typeArguments = listOf("kotlin.Int", "kotlin.String"),
                ),
            ),
        )
    }

    @Test
    fun process_framedModel_withRepeatedUnsupportedClass_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field(
                    "values",
                    "kotlin.collections.List",
                    order = 0,
                    typeArguments = listOf("ch.trancee.test.Custom"),
                ),
            ),
        )
    }

    @Test
    fun process_framedModel_withUnresolvedType_defersWithoutOutput() {
        assertDeferred(listOf(field("unresolved", "kotlin.Int", order = 0, bitWidth = 5, errorType = true)))
    }

    @Test
    fun process_framedModel_withNullableType_reportsDiagnosticWithoutOutput() {
        assertInvalid(listOf(field("nullable", "kotlin.Int", order = 0, bitWidth = 5, nullableType = true)))
    }

    @Test
    fun process_framedModel_withCustomIntNamedType_reportsDiagnosticWithoutOutput() {
        assertInvalid(listOf(field("value", "ch.trancee.test.Int", order = 0, bitWidth = 5)))
    }

    @Test
    fun process_framedModel_withUnqualifiedTypeName_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field(
                    "value",
                    "ch.trancee.test.Payload",
                    order = 0,
                    nullQualifiedName = true,
                ),
            ),
        )
    }

    @Test
    fun process_framedModel_withNestedTypeParameter_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field(
                    "child",
                    "ch.trancee.test.Payload",
                    order = 0,
                    nestedModel = true,
                    typeArguments = listOf(null),
                ),
            ),
        )
    }

    @Test
    fun process_framedModel_withNonFramedNestedModel_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field(
                    "child",
                    "ch.trancee.test.Payload",
                    order = 0,
                    nestedModel = true,
                    framedModelType = false,
                    declarationAnnotations = listOf(FakeKSAnnotation("ch.trancee.kompact.annotations.Other")),
                ),
            ),
        )
    }

    @Test
    fun process_framedModel_withUnresolvedNestedDeclaration_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field(
                    "child",
                    "ch.trancee.test.Payload",
                    order = 0,
                    nestedModel = true,
                    classType = false,
                ),
            ),
        )
    }

    @Test
    fun process_framedModel_withUnresolvedNestedAnnotation_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field(
                    "child",
                    "ch.trancee.test.Payload",
                    order = 0,
                    nestedModel = true,
                    framedModelType = false,
                    declarationAnnotations =
                        listOf(
                            FakeKSAnnotation(
                                "ch.trancee.kompact.annotations.KompactModel",
                                mapOf("framed" to true),
                                nullQualifiedName = true,
                            ),
                        ),
                ),
            ),
        )
    }

    @Test
    fun process_framedModel_withUnannotatedNestedModel_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field(
                    "child",
                    "ch.trancee.test.Payload",
                    order = 0,
                    nestedModel = true,
                    framedModelType = false,
                ),
            ),
        )
    }

    @Test
    fun process_framedModel_withNestedModelNotMarkedFramed_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field(
                    "child",
                    "ch.trancee.test.Payload",
                    order = 0,
                    nestedModel = true,
                    framedModelType = false,
                    declarationAnnotations =
                        listOf(
                            FakeKSAnnotation(
                                "ch.trancee.kompact.annotations.KompactModel",
                                mapOf("framed" to false),
                            ),
                        ),
                ),
            ),
        )
    }

    @Test
    fun process_framedModel_withNonBooleanNestedFramedAnnotation_reportsDiagnosticWithoutOutput() {
        assertInvalid(
            listOf(
                field(
                    "child",
                    "ch.trancee.test.Payload",
                    order = 0,
                    nestedModel = true,
                    framedModelType = false,
                    declarationAnnotations =
                        listOf(
                            FakeKSAnnotation(
                                "ch.trancee.kompact.annotations.KompactModel",
                                mapOf("framed" to "true"),
                            ),
                        ),
                ),
            ),
        )
    }

    @Test
    fun process_framedModel_infersNestedFieldsFromFramedModelTypes() {
        val (processor, codeGen, logger) = createTestSetup()
        val model =
            FakeKSClassDeclaration(
                simpleNameStr = "InferredFrame",
                packageNameStr = "ch.trancee.test",
                properties =
                    listOf(
                        field("child", "ch.trancee.test.Payload", order = 0, framedModelType = true),
                        field(
                            "children",
                            "kotlin.collections.List",
                            order = 1,
                            typeArguments = listOf("ch.trancee.test.Payload"),
                            nestedModel = true,
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

        assertTrue(logger.errors.isEmpty(), logger.errors.toString())
        val generated = codeGen.generatedFiles.getValue("ch.trancee.test.InferredFrameView.kt")
        assertTrue(generated.contains("public val child: PayloadView"), generated)
        assertTrue(generated.contains("public val children: KompactRepeatedView<PayloadView>"), generated)
    }

    @Test
    fun process_framedModel_withFixedLayoutModelFieldType_reportsFramedRequirement() {
        val (processor, codeGen, logger) = createTestSetup()
        val model =
            FakeKSClassDeclaration(
                simpleNameStr = "FixedChildFrame",
                packageNameStr = "ch.trancee.test",
                properties =
                    listOf(
                        field(
                            "child",
                            "ch.trancee.test.Payload",
                            order = 0,
                            declarationAnnotations =
                                listOf(
                                    FakeKSAnnotation(
                                        "ch.trancee.kompact.annotations.KompactModel",
                                        mapOf("framed" to false),
                                    ),
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

        assertTrue(
            logger.errors.any {
                it.contains("Nested field type ch.trancee.test.Payload must be a @KompactModel(framed = true) class")
            },
            logger.errors.toString(),
        )
        assertTrue(codeGen.generatedFiles.isEmpty())
    }

    @Test
    fun process_framedModel_rejectsParameterizedNestedModel() {
        val (processor, codeGen, logger) = createTestSetup()
        val model =
            FakeKSClassDeclaration(
                simpleNameStr = "GenericChildFrame",
                packageNameStr = "ch.trancee.test",
                properties =
                    listOf(
                        field(
                            "child",
                            "ch.trancee.test.Payload",
                            order = 0,
                            lengthPrefixWidth = 16,
                            nestedModel = true,
                            typeArguments = listOf("kotlin.Int"),
                            nullQualifiedName = true,
                            declarationAnnotations =
                                listOf(
                                    FakeKSAnnotation("ch.trancee.kompact.annotations.Other"),
                                    FakeKSAnnotation(
                                        "ch.trancee.kompact.annotations.KompactModel",
                                        mapOf("framed" to true),
                                        nullNameArgValue = 1,
                                    ),
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

        assertTrue(logger.errors.any { it.contains("parameterized model") }, logger.errors.toString())
        assertTrue(codeGen.generatedFiles.isEmpty())
    }
}
