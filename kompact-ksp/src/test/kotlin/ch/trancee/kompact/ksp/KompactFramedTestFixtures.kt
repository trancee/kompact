package ch.trancee.kompact.ksp

import ch.trancee.kompact.ksp.testing.FakeKSAnnotation
import ch.trancee.kompact.ksp.testing.FakeKSClassDeclaration
import ch.trancee.kompact.ksp.testing.FakeKSPropertyDeclaration
import ch.trancee.kompact.ksp.testing.FakeKSTypeArgument
import ch.trancee.kompact.ksp.testing.FakeKSTypeReference
import ch.trancee.kompact.ksp.testing.FakeResolver
import kotlin.test.assertTrue

internal fun assertInvalid(
    fields: List<FakeKSPropertyDeclaration>,
    modelMutable: Any? = false,
    modelFramed: Any? = true,
) {
    val (processor, codeGen, logger) = createTestSetup(mode = "jvm")
    val model =
        FakeKSClassDeclaration(
            simpleNameStr = "InvalidFrame",
            packageNameStr = "ch.trancee.test",
            properties = fields,
            declAnnotations =
                listOf(
                    FakeKSAnnotation(
                        "ch.trancee.kompact.annotations.KompactModel",
                        mapOf("framed" to modelFramed, "mutable" to modelMutable),
                    ),
                ),
        )

    processor.process(FakeResolver(listOf(model)))

    assertTrue(logger.errors.isNotEmpty(), "invalid framed schema must be rejected")
    assertTrue(codeGen.generatedFiles.isEmpty(), "invalid schemas must not leave partial generated output")
}

internal fun assertDeferred(fields: List<FakeKSPropertyDeclaration>) {
    val (processor, codeGen, logger) = createTestSetup(mode = "jvm")
    val model =
        FakeKSClassDeclaration(
            simpleNameStr = "UnresolvedFrame",
            packageNameStr = "ch.trancee.test",
            properties = fields,
            declAnnotations =
                listOf(
                    FakeKSAnnotation(
                        "ch.trancee.kompact.annotations.KompactModel",
                        mapOf("framed" to true, "mutable" to false),
                    ),
                ),
        )

    processor.process(FakeResolver(listOf(model)))

    assertTrue(logger.errors.isEmpty(), "unresolved symbols must be deferred without diagnostics")
    assertTrue(codeGen.generatedFiles.isEmpty(), "unresolved schemas must not leave partial generated output")
}

internal fun field(
    name: String,
    typeName: String,
    order: Int?,
    bitWidth: Int = 0,
    lengthPrefixWidth: Int = 8,
    repeatCountWidth: Int = 8,
    isNested: Boolean = false,
    bitOffset: Int = 0,
    mutable: Boolean = false,
    signed: Boolean = false,
    typeArguments: List<String?> = emptyList(),
    errorType: Boolean = false,
    nullableType: Boolean = false,
    framedModelType: Boolean? = null,
    declarationAnnotations: List<FakeKSAnnotation> = emptyList(),
    annotationArgs: Map<String, Any?> = emptyMap(),
    nullQualifiedName: Boolean = false,
    classType: Boolean = true,
): FakeKSPropertyDeclaration =
    FakeKSPropertyDeclaration(
        simpleNameStr = name,
        packageNameStr = "ch.trancee.test",
        typeStr = typeName,
        typeReference =
            FakeKSTypeReference(
                typeName = typeName,
                typeArguments =
                    typeArguments.map { typeArgument ->
                        FakeKSTypeArgument(
                            typeArgument?.let { argumentType ->
                                FakeKSTypeReference(
                                    argumentType,
                                    framedModel = argumentType.startsWith("ch.trancee.test."),
                                )
                            },
                        )
                    },
                framedModel = framedModelType ?: (isNested && typeName.startsWith("ch.trancee.test.")),
                errorType = errorType,
                nullableType = nullableType,
                declarationAnnotations = declarationAnnotations,
                nullQualifiedName = nullQualifiedName,
                classType = classType,
            ),
        isMutable = mutable,
        declAnnotations =
            listOf(
                FakeKSAnnotation(
                    "ch.trancee.kompact.annotations.KompactField",
                    mapOf(
                        "order" to order,
                        "bitOffset" to bitOffset,
                        "bitWidth" to bitWidth,
                        "signed" to signed,
                        "lengthPrefixWidth" to lengthPrefixWidth,
                        "repeatCountWidth" to repeatCountWidth,
                        "isNested" to isNested,
                    ) + annotationArgs,
                ),
            ),
    )
