package ch.trancee.kompact.ksp

import ch.trancee.kompact.ksp.testing.FakeKSAnnotation
import ch.trancee.kompact.ksp.testing.FakeKSClassDeclaration
import ch.trancee.kompact.ksp.testing.FakeResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KompactSymbolProcessorFramedTest {
    @Test
    fun process_framedModel_emitsSingleCommonRegularViewFile() {
        val (processor, codeGen, logger) = createTestSetup()
        val model =
            FakeKSClassDeclaration(
                simpleNameStr = "KmpFrame",
                packageNameStr = "ch.trancee.test",
                properties = listOf(field("marker", "kotlin.Int", 0, bitWidth = 8)),
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
        assertEquals(setOf("ch.trancee.test.KmpFrameView.kt"), codeGen.generatedFiles.keys)
        val generated = codeGen.generatedFiles.getValue("ch.trancee.test.KmpFrameView.kt")
        assertTrue(generated.contains("public class KmpFrameView("), generated)
        assertTrue(!generated.contains("expect "), generated)
        assertTrue(!generated.contains("actual "), generated)
    }

    @Test
    fun process_framedModel_generatesOrderedTypedFrameView() {
        val (processor, codeGen, logger) = createTestSetup()
        val model =
            FakeKSClassDeclaration(
                simpleNameStr = "SensorFrame",
                packageNameStr = "ch.trancee.test",
                properties =
                    listOf(
                        field(
                            "child",
                            "ch.trancee.test.Payload",
                            order = 3,
                            lengthPrefixWidth = 16,
                            nestedModel = true,
                        ),
                        field(
                            "children",
                            "kotlin.collections.List",
                            order = 4,
                            repeatCountWidth = 16,
                            lengthPrefixWidth = 16,
                            nestedModel = true,
                            typeArguments = listOf("ch.trancee.test.Payload"),
                        ),
                        field(
                            "samples",
                            "kotlin.collections.List",
                            order = 2,
                            bitWidth = 16,
                            repeatCountWidth = 8,
                            typeArguments = listOf("kotlin.Int"),
                        ),
                        field("label", "kotlin.String", order = 1, lengthPrefixWidth = 16),
                        field("marker", "kotlin.Int", order = 0, bitWidth = 8),
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

        assertTrue(logger.errors.isEmpty(), "framed types should be accepted: ${logger.errors}")
        assertEquals(setOf("ch.trancee.test.SensorFrameView.kt"), codeGen.generatedFiles.keys)
        val generated = codeGen.generatedFiles["ch.trancee.test.SensorFrameView.kt"]!!
        assertTrue(generated.contains("public class SensorFrameView("), generated)
        assertTrue(generated.contains("start: Int"), generated)
        assertTrue(generated.contains("end: Int"), generated)
        assertTrue(generated.contains("fun decode("), generated)
        assertTrue(!generated.contains("@JvmInline"), generated)
        assertTrue(generated.contains("public val samples: KompactRepeatedView<Int>"), generated)
        assertTrue(generated.contains("public val children: KompactRepeatedView<PayloadView>"), generated)
        assertTrue(generated.contains("readBits(8)"), generated)
        assertTrue(generated.contains("readString(16)"), generated)
        assertTrue(generated.contains("readRepeated"), generated)
        assertTrue(generated.contains("readNested(16)"), generated)
        assertTrue(generated.contains("public fun create("), generated)
        assertTrue(generated.contains("public fun copy("), generated)
        assertTrue(
            generated.indexOf("this.markerValue") < generated.indexOf("this.labelValue") &&
                generated.indexOf("this.labelValue") < generated.indexOf("this.samplesValue") &&
                generated.indexOf("this.samplesValue") < generated.indexOf("this.childValue") &&
                generated.indexOf("this.childValue") < generated.indexOf("this.childrenValue"),
            "fields must be consumed by declared order: $generated",
        )
    }

    @Test
    fun process_framedModel_generatesAllSupportedFieldShapes() {
        val (processor, codeGen, logger) = createTestSetup()
        val model =
            FakeKSClassDeclaration(
                simpleNameStr = "AllFramedShapes",
                packageNameStr = "ch.trancee.test",
                properties =
                    listOf(
                        field("flag", "kotlin.Boolean", 0, bitWidth = 1),
                        field("smallInt", "kotlin.Int", 1, bitWidth = 7, signed = true),
                        field("fullInt", "kotlin.Int", 2, bitWidth = 32),
                        field("shortLong", "kotlin.Long", 3, bitWidth = 16, signed = true),
                        field("fullLong", "kotlin.Long", 4, bitWidth = 64, signed = true),
                        field("reading", "kotlin.Float", 5, bitWidth = 32),
                        field("pressure", "kotlin.Double", 6, bitWidth = 64),
                        field("label", "kotlin.String", 7, lengthPrefixWidth = 8),
                        field("payload", "kotlin.ByteArray", 8, lengthPrefixWidth = 16),
                        field(
                            "child",
                            "ch.trancee.test.Payload",
                            9,
                            lengthPrefixWidth = 8,
                            nestedModel = true,
                        ),
                        field(
                            "samples",
                            "kotlin.collections.List",
                            10,
                            bitWidth = 8,
                            typeArguments = listOf("kotlin.Int"),
                        ),
                        field(
                            "labels",
                            "kotlin.collections.List",
                            11,
                            lengthPrefixWidth = 16,
                            typeArguments = listOf("kotlin.String"),
                        ),
                        field(
                            "payloads",
                            "kotlin.collections.List",
                            12,
                            lengthPrefixWidth = 8,
                            nestedModel = false,
                            typeArguments = listOf("kotlin.ByteArray"),
                        ),
                        field(
                            "children",
                            "kotlin.collections.List",
                            13,
                            lengthPrefixWidth = 16,
                            repeatCountWidth = 16,
                            nestedModel = true,
                            typeArguments = listOf("ch.trancee.test.Payload"),
                        ),
                        field(
                            "flags",
                            "kotlin.collections.List",
                            14,
                            bitWidth = 1,
                            typeArguments = listOf("kotlin.Boolean"),
                        ),
                        field(
                            "longValues",
                            "kotlin.collections.List",
                            15,
                            bitWidth = 64,
                            typeArguments = listOf("kotlin.Long"),
                        ),
                        field(
                            "floatValues",
                            "kotlin.collections.List",
                            16,
                            bitWidth = 32,
                            typeArguments = listOf("kotlin.Float"),
                        ),
                        field(
                            "doubleValues",
                            "kotlin.collections.List",
                            17,
                            bitWidth = 64,
                            typeArguments = listOf("kotlin.Double"),
                        ),
                        field("unsignedLong", "kotlin.Long", 18, bitWidth = 32),
                        field("signedFullInt", "kotlin.Int", 19, bitWidth = 32, signed = true),
                        field(
                            "fullIntValues",
                            "kotlin.collections.List",
                            20,
                            bitWidth = 32,
                            typeArguments = listOf("kotlin.Int"),
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

        assertTrue(logger.errors.isEmpty(), "framed shapes should be accepted: ${logger.errors}")
        val generated = codeGen.generatedFiles["ch.trancee.test.AllFramedShapesView.kt"]!!
        assertTrue(generated.contains("Float.fromBits"), generated)
        assertTrue(generated.contains("(frame.readBits(7).toInt() shl 25) shr 25"), generated)
        assertTrue(generated.contains("Double.fromBits"), generated)
        assertTrue(generated.contains("payloadSlice.toByteArray()"), generated)
        assertTrue(generated.contains("readRepeated"), generated)
        assertTrue(generated.contains("writeNested(16, element.raw, element.start, element.end)"), generated)
        assertTrue(generated.contains("writeRepeated"), generated)
        assertTrue(generated.contains("writeBitsLong(32, fullInt.toLong())"), generated)
        assertTrue(generated.contains("writeBitsLong(64, fullLong)"), generated)
        assertTrue(generated.contains("KompactRepeatedView<ByteArray>"), generated)
    }

    @Test
    fun process_framedModel_resolvesUnqualifiedListType() {
        val (processor, codeGen, logger) = createTestSetup()
        val model =
            FakeKSClassDeclaration(
                simpleNameStr = "UnqualifiedListFrame",
                packageNameStr = "ch.trancee.test",
                properties =
                    listOf(
                        field(
                            "samples",
                            "List",
                            order = 0,
                            bitWidth = 5,
                            typeArguments = listOf("kotlin.Int"),
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

        assertTrue(logger.errors.isEmpty(), "List should be recognized as repeated: ${logger.errors}")
        val generated = codeGen.generatedFiles["ch.trancee.test.UnqualifiedListFrameView.kt"]!!
        assertTrue(generated.contains("public val samples: KompactRepeatedView<Int>"), generated)
    }
}
