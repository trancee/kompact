package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.KompactFieldType
import ch.trancee.kompact.ksp.model.KompactScalarKind
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName

internal object FieldCodeGenerator {
    fun resolveFramedNestedTypeName(type: KompactFieldType.Nested): ClassName {
        val schema = ClassName.bestGuess(type.qualifiedName)
        return ClassName(schema.packageName, "${schema.simpleName}View")
    }

    private val KOMPAT_RUNTIME = ClassName("ch.trancee.kompact.runtime", "KompactRuntime")
    private val BYTE_ARRAY_TYPE = ClassName("kotlin", "ByteArray")

    private val READ_CALL_BUILDERS: Map<KompactScalarKind, (KompactFieldInfo) -> CodeBlock> =
        mapOf(
            KompactScalarKind.BOOLEAN to { field ->
                CodeBlock.of("%T.readBitsBoolean(raw, %L)", KOMPAT_RUNTIME, field.bitOffset)
            },
            KompactScalarKind.INT to { field ->
                val read = CodeBlock.of("%T.readBits(raw, %L, %L)", KOMPAT_RUNTIME, field.bitOffset, field.bitWidth)
                if (field.signed) {
                    val shift = Int.SIZE_BITS - field.bitWidth
                    CodeBlock.of("(%L shl %L) shr %L", read, shift, shift)
                } else {
                    read
                }
            },
            KompactScalarKind.LONG to { field ->
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
            KompactScalarKind.FLOAT to { field ->
                CodeBlock.of("Float.fromBits(%T.readBitsLong(raw, %L, 32).toInt())", KOMPAT_RUNTIME, field.bitOffset)
            },
            KompactScalarKind.DOUBLE to { field ->
                CodeBlock.of("Double.fromBits(%T.readBitsLong(raw, %L, 64))", KOMPAT_RUNTIME, field.bitOffset)
            },
        )

    private val ENCODE_CALL_BUILDERS: Map<KompactScalarKind, (KompactFieldInfo) -> CodeBlock> =
        mapOf(
            KompactScalarKind.BOOLEAN to { field ->
                CodeBlock.of("%T.writeBitsBoolean(raw, %L, %L)", KOMPAT_RUNTIME, field.bitOffset, field.name)
            },
            KompactScalarKind.INT to { field ->
                CodeBlock.of(
                    "%T.writeBits(raw, %L, %L, %L)",
                    KOMPAT_RUNTIME,
                    field.bitOffset,
                    field.bitWidth,
                    field.name,
                )
            },
            KompactScalarKind.LONG to { field ->
                CodeBlock.of(
                    "%T.writeBitsLong(raw, %L, %L, %L)",
                    KOMPAT_RUNTIME,
                    field.bitOffset,
                    field.bitWidth,
                    field.name,
                )
            },
            KompactScalarKind.FLOAT to { field ->
                CodeBlock.of(
                    "%T.writeBitsLong(raw, %L, 32, %L.toRawBits().toLong())",
                    KOMPAT_RUNTIME,
                    field.bitOffset,
                    field.name,
                )
            },
            KompactScalarKind.DOUBLE to { field ->
                CodeBlock.of(
                    "%T.writeBitsLong(raw, %L, 64, %L.toRawBits())",
                    KOMPAT_RUNTIME,
                    field.bitOffset,
                    field.name,
                )
            },
        )

    private val WRITE_CALL_BUILDERS: Map<KompactScalarKind, (KompactFieldInfo) -> CodeBlock> =
        mapOf(
            KompactScalarKind.BOOLEAN to { field ->
                CodeBlock.of("%T.writeBitsBoolean(raw, %L, value)", KOMPAT_RUNTIME, field.bitOffset)
            },
            KompactScalarKind.INT to { field ->
                CodeBlock.of(
                    "%T.writeBits(raw, %L, %L, value)",
                    KOMPAT_RUNTIME,
                    field.bitOffset,
                    field.bitWidth,
                )
            },
            KompactScalarKind.LONG to { field ->
                CodeBlock.of(
                    "%T.writeBitsLong(raw, %L, %L, value)",
                    KOMPAT_RUNTIME,
                    field.bitOffset,
                    field.bitWidth,
                )
            },
            KompactScalarKind.FLOAT to { field ->
                CodeBlock.of(
                    "%T.writeBitsLong(raw, %L, 32, value.toRawBits().toLong())",
                    KOMPAT_RUNTIME,
                    field.bitOffset,
                )
            },
            KompactScalarKind.DOUBLE to { field ->
                CodeBlock.of(
                    "%T.writeBitsLong(raw, %L, 64, value.toRawBits())",
                    KOMPAT_RUNTIME,
                    field.bitOffset,
                )
            },
        )

    fun readCall(field: KompactFieldInfo): CodeBlock = READ_CALL_BUILDERS.getValue(field.scalarKind())(field)

    fun encodeWriteCall(field: KompactFieldInfo): CodeBlock = ENCODE_CALL_BUILDERS.getValue(field.scalarKind())(field)

    fun writeCall(field: KompactFieldInfo): CodeBlock = WRITE_CALL_BUILDERS.getValue(field.scalarKind())(field)

    fun resolveTypeName(type: KompactFieldType): TypeName =
        when (type) {
            is KompactFieldType.Scalar -> {
                when (type.kind) {
                    KompactScalarKind.BOOLEAN -> ClassName("kotlin", "Boolean")
                    KompactScalarKind.INT -> ClassName("kotlin", "Int")
                    KompactScalarKind.LONG -> ClassName("kotlin", "Long")
                    KompactScalarKind.FLOAT -> ClassName("kotlin", "Float")
                    KompactScalarKind.DOUBLE -> ClassName("kotlin", "Double")
                }
            }

            KompactFieldType.StringType -> {
                ClassName("kotlin", "String")
            }

            KompactFieldType.Blob -> {
                BYTE_ARRAY_TYPE
            }

            is KompactFieldType.Nested -> {
                val className = ClassName.bestGuess(type.qualifiedName)
                if (type.typeArguments.isEmpty()) {
                    className
                } else {
                    className.parameterizedBy(type.typeArguments.map(::resolveTypeName))
                }
            }

            is KompactFieldType.Repeated -> {
                ClassName("kotlin.collections", "List").parameterizedBy(resolveTypeName(type.elementType))
            }

            is KompactFieldType.Unsupported -> {
                ClassName.bestGuess(type.qualifiedName)
            }
        }

    private fun KompactFieldInfo.scalarKind(): KompactScalarKind =
        when (val fieldType = type) {
            is KompactFieldType.Scalar -> fieldType.kind
            else -> throw IllegalArgumentException("Field '$name' has non-scalar type ${fieldType.displayName}")
        }
}
