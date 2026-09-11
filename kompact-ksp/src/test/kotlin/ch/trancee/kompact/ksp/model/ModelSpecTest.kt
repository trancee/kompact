package ch.trancee.kompact.ksp.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelSpecTest {
    private fun field(
        name: String,
        bitOffset: Int,
        bitWidth: Int,
    ) = KompactFieldInfo(
        name = name,
        kotlinType = "Int",
        bitOffset = bitOffset,
        bitWidth = bitWidth,
        signed = false,
        lengthPrefixWidth = 8,
        isNested = false,
        repeatCountWidth = 8,
        enumWidth = 0,
        defaultValue = "",
        isVersionField = false,
    )

    @Test
    fun `empty field list has zero totalBits`() {
        val spec = ModelSpec.create("test", "Empty", emptyList())
        assertEquals(0, spec.totalBits)
    }

    @Test
    fun `empty field list has zero minBufferSize`() {
        val spec = ModelSpec.create("test", "Empty", emptyList())
        assertEquals(0, spec.minBufferSize)
    }

    @Test
    fun `minBufferSize rounds up to whole bytes`() {
        val spec = ModelSpec.create("test", "Model", listOf(field("a", 0, 9)))
        // 9 bits → 2 bytes
        assertEquals(2, spec.minBufferSize)
    }

    @Test
    fun `totalBits is end of last field`() {
        val spec =
            ModelSpec.create(
                "test",
                "Model",
                listOf(field("a", 0, 8), field("b", 8, 8)),
            )
        assertEquals(16, spec.totalBits)
    }
}
