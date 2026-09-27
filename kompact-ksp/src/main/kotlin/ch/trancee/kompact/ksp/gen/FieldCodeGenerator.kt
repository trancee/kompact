package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName

internal object FieldCodeGenerator {
    private val KOMPAT_RUNTIME = ClassName("ch.trancee.kompact.runtime", "KompactRuntime")
    private val BYTE_ARRAY_TYPE = ClassName("kotlin", "ByteArray")

    private val READ_CALL_BUILDERS: Map<String, (KompactFieldInfo) -> CodeBlock> =
        mapOf(
            "Boolean" to { field ->
                CodeBlock.of("%T.readBitsBoolean(raw, %L)", KOMPAT_RUNTIME, field.bitOffset)
            },
            "Int" to { field ->
                val read = CodeBlock.of("%T.readBits(raw, %L, %L)", KOMPAT_RUNTIME, field.bitOffset, field.bitWidth)
                if (field.signed) {
                    val shift = Int.SIZE_BITS - field.bitWidth
                    CodeBlock.of("(%L shl %L) shr %L", read, shift, shift)
                } else {
                    read
                }
            },
            "Long" to { field ->
                val read =
                    CodeBlock.of(
                        "%T.readBitsLong(raw, %L, %L)",
                        KOMPAT_RUNTIME,
                        field.bitOffset,
                        field.bitWidth,
                    )
                if (field.signed) {
                    val shift = Long.SIZE_BITS - field.bitWidth
                    CodeBlock.of("(%L shl %L) shr %L", read, shift, shift)
                } else {
                    read
                }
            },
            "Float" to { field ->
                CodeBlock.of("Float.fromBits(%T.readBitsLong(raw, %L, 32).toInt())", KOMPAT_RUNTIME, field.bitOffset)
            },
            "Double" to { field ->
                CodeBlock.of("Double.fromBits(%T.readBitsLong(raw, %L, 64))", KOMPAT_RUNTIME, field.bitOffset)
            },
        )

    private val ENCODE_CALL_BUILDERS: Map<String, (KompactFieldInfo) -> CodeBlock> =
        mapOf(
            "Boolean" to { field ->
                CodeBlock.of("%T.writeBitsBoolean(raw, %L, %L)", KOMPAT_RUNTIME, field.bitOffset, field.name)
            },
            "Int" to { field ->
                CodeBlock.of(
                    "%T.writeBits(raw, %L, %L, %L)",
                    KOMPAT_RUNTIME,
                    field.bitOffset,
                    field.bitWidth,
                    field.name,
                )
            },
            "Long" to { field ->
                CodeBlock.of(
                    "%T.writeBitsLong(raw, %L, %L, %L)",
                    KOMPAT_RUNTIME,
                    field.bitOffset,
                    field.bitWidth,
                    field.name,
                )
            },
            "Float" to { field ->
                CodeBlock.of(
                    "%T.writeBitsLong(raw, %L, 32, %L.toRawBits().toLong())",
                    KOMPAT_RUNTIME,
                    field.bitOffset,
                    field.name,
                )
            },
            "Double" to { field ->
                CodeBlock.of(
                    "%T.writeBitsLong(raw, %L, 64, %L.toRawBits())",
                    KOMPAT_RUNTIME,
                    field.bitOffset,
                    field.name,
                )
            },
        )

    private val WRITE_CALL_BUILDERS: Map<String, (KompactFieldInfo) -> CodeBlock> =
        mapOf(
            "Boolean" to { field ->
                CodeBlock.of("%T.writeBitsBoolean(raw, %L, value)", KOMPAT_RUNTIME, field.bitOffset)
            },
            "Int" to { field ->
                CodeBlock.of(
                    "%T.writeBits(raw, %L, %L, value)",
                    KOMPAT_RUNTIME,
                    field.bitOffset,
                    field.bitWidth,
                )
            },
            "Long" to { field ->
                CodeBlock.of(
                    "%T.writeBitsLong(raw, %L, %L, value)",
                    KOMPAT_RUNTIME,
                    field.bitOffset,
                    field.bitWidth,
                )
            },
            "Float" to { field ->
                CodeBlock.of(
                    "%T.writeBitsLong(raw, %L, 32, value.toRawBits().toLong())",
                    KOMPAT_RUNTIME,
                    field.bitOffset,
                )
            },
            "Double" to { field ->
                CodeBlock.of(
                    "%T.writeBitsLong(raw, %L, 64, value.toRawBits())",
                    KOMPAT_RUNTIME,
                    field.bitOffset,
                )
            },
        )

    fun readCall(field: KompactFieldInfo): CodeBlock = READ_CALL_BUILDERS.getValue(field.kotlinType)(field)

    fun encodeWriteCall(field: KompactFieldInfo): CodeBlock = ENCODE_CALL_BUILDERS.getValue(field.kotlinType)(field)

    fun writeCall(field: KompactFieldInfo): CodeBlock = WRITE_CALL_BUILDERS.getValue(field.kotlinType)(field)

    fun resolveTypeName(typeName: String): TypeName =
        when (typeName) {
            "Int" -> ClassName("kotlin", "Int")
            "Long" -> ClassName("kotlin", "Long")
            "Boolean" -> ClassName("kotlin", "Boolean")
            "Float" -> ClassName("kotlin", "Float")
            "Double" -> ClassName("kotlin", "Double")
            "String" -> ClassName("kotlin", "String")
            "ByteArray" -> BYTE_ARRAY_TYPE
            else -> ClassName.bestGuess(typeName)
        }
}
