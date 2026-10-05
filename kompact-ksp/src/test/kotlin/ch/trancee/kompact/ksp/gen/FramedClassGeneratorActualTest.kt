package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.KompactFieldType
import ch.trancee.kompact.ksp.model.KompactScalarKind
import ch.trancee.kompact.ksp.model.ModelSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FramedClassGeneratorActualTest {
    @Test
    fun platformActualsExposeFramedDecodeCreateCopyAndCompletionContracts() {
        val spec =
            ModelSpec(
                packageName = "example",
                className = "ActualFrame",
                framed = true,
                fields =
                    listOf(
                        field("label", KompactFieldType.StringType, 0, 0),
                        field("payload", KompactFieldType.Blob, 1, 0),
                        field("count", KompactFieldType.Scalar(KompactScalarKind.INT), 2, 5),
                    ),
            )

        val expect = FramedClassGenerator.generateExpect(spec)
        val jvm = FramedClassGenerator.generateJvmActual(spec)
        val ios = FramedClassGenerator.generateIosActual(spec)
        val androidArm64 = FramedClassGenerator.generateAndroidArm64Actual(spec)

        assertTrue(expect.contains("class ActualFrameView"))
        assertTrue(expect.contains("start: Int = 0"))
        assertTrue(expect.contains("end: Int = raw.size"))
        assertTrue(expect.contains("label: String = this.label"))
        assertTrue(expect.contains("payload: ByteArray = this.payload"))
        assertTrue(expect.contains("count: Int = this.count"))
        assertTrue(expect.contains("val label: String"))
        assertTrue(expect.contains("val payload: ByteArray"))
        assertTrue(expect.contains("val payloadSlice: KompactByteSlice"))
        assertTrue(expect.contains("val count: Int"))
        assertTrue(jvm.contains("actual class ActualFrameView"))
        assertTrue(jvm.contains("actual constructor("))
        assertTrue(!expect.contains("actual constructor("))
        assertTrue(jvm.contains("actual companion object"))
        assertTrue(jvm.contains("actual fun decode("))
        assertTrue(jvm.contains("KompactFrameResult.Success(ActualFrameView(raw, start, end))"))
        assertTrue(jvm.contains("KompactFrameResult.Failure(failure.error)"))
        assertTrue(jvm.contains("KompactFrame.decode(raw, start, end).getOrThrow()"))
        assertTrue(jvm.contains("frame.requireComplete()"))
        assertTrue(jvm.contains("private val labelValue: String"))
        assertTrue(jvm.contains("this.labelValue = frame.readString(8)"))
        assertTrue(jvm.contains("private val payloadValue: KompactByteSlice"))
        assertTrue(jvm.contains("this.payloadValue = frame.readBlob(8)"))
        assertTrue(jvm.contains("actual val payloadSlice: KompactByteSlice"))
        assertTrue(jvm.contains("actual val payload: ByteArray"))
        assertTrue(jvm.contains("actual val count: Int"))
        assertTrue(jvm.contains("actual fun create("))
        val createSignature = jvm.substringAfter("actual fun create(").substringBefore("): ActualFrameView")
        assertTrue(createSignature.contains("label: String"))
        assertTrue(createSignature.contains("payload: ByteArray"))
        assertTrue(createSignature.contains("count: Int"))
        assertTrue(jvm.contains("writer.writeString(8, label)"))
        assertTrue(jvm.contains("writer.writeBlob(8, payload)"))
        assertTrue(jvm.contains("writer.writeBits(5, count)"))
        assertTrue(jvm.contains("actual fun copy("))
        val copySignature = jvm.substringAfter("actual fun copy(").substringBefore("): ActualFrameView")
        assertTrue(copySignature.contains("label: String"))
        assertTrue(copySignature.contains("payload: ByteArray"))
        assertTrue(copySignature.contains("count: Int"))
        assertTrue(jvm.contains("ActualFrameView.create(label, payload, count)"))
        assertEquals(ios, androidArm64)
        assertTrue(ios.contains("actual fun decode("))
        assertTrue(ios.contains("actual fun create("))
    }

    private fun field(
        name: String,
        type: KompactFieldType,
        order: Int,
        bitWidth: Int,
    ) = KompactFieldInfo(
        name = name,
        type = type,
        order = order,
        bitOffset = 0,
        bitWidth = bitWidth,
        signed = false,
        lengthPrefixWidth = 8,
        isNested = type is KompactFieldType.Nested,
        repeatCountWidth = 8,
        enumWidth = 0,
        defaultValue = "",
    )
}
