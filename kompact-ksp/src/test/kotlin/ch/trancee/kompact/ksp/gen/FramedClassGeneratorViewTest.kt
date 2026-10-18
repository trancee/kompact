package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.KompactFieldType
import ch.trancee.kompact.ksp.model.KompactScalarKind
import ch.trancee.kompact.ksp.model.ModelSpec
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FramedClassGeneratorViewTest {
    @Test
    fun commonViewExposesFramedDecodeCreateCopyAndCompletionContracts() {
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

        val view = FramedClassGenerator.generate(spec)

        assertTrue(view.contains("public class ActualFrameView("), view)
        assertFalse(view.contains("expect "), view)
        assertFalse(view.contains("actual "), view)
        assertTrue(view.contains("start: Int = 0"))
        assertTrue(view.contains("end: Int = raw.size"))
        assertTrue(view.contains("label: String = this.label"))
        assertTrue(view.contains("payload: ByteArray = this.payload"))
        assertTrue(view.contains("count: Int = this.count"))
        assertTrue(view.contains("public companion object"))
        assertTrue(view.contains("public fun decode("))
        assertTrue(view.contains("KompactFrameResult.Success(ActualFrameView(raw, start, end))"))
        assertTrue(view.contains("KompactFrameResult.Failure(failure.error)"))
        assertTrue(view.contains("KompactFrame.decode(raw, start, end).getOrThrow()"))
        assertTrue(view.contains("frame.requireComplete()"))
        assertTrue(view.contains("private val labelValue: String"))
        assertTrue(view.contains("this.labelValue = frame.readString(8)"))
        assertTrue(view.contains("private val payloadValue: KompactByteSlice"))
        assertTrue(view.contains("this.payloadValue = frame.readBlob(8)"))
        assertTrue(view.contains("public val payloadSlice: KompactByteSlice"))
        assertTrue(view.contains("public val payload: ByteArray"))
        assertTrue(view.contains("public val count: Int"))
        val createSignature = view.substringAfter("public fun create(").substringBefore("): ActualFrameView")
        assertTrue(createSignature.contains("label: String"))
        assertTrue(createSignature.contains("payload: ByteArray"))
        assertTrue(createSignature.contains("count: Int"))
        assertFalse(createSignature.contains("="), createSignature)
        assertTrue(view.contains("writer.writeString(8, label)"))
        assertTrue(view.contains("writer.writeBlob(8, payload)"))
        assertTrue(view.contains("writer.writeBits(5, count)"))
        assertTrue(view.contains("public fun copy("))
        assertTrue(view.contains("ActualFrameView.create(label, payload, count)"))
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
        repeatCountWidth = 8,
    )
}
