package ch.trancee.kompact.ksp.model

import kotlin.test.Test
import kotlin.test.assertTrue

class FramedLayoutValidatorTest {
    @Test
    fun validateAll_rejectsUnalignedBorrowedPayload() {
        val fields =
            listOf(
                field("flag", scalarType("Boolean"), order = 0, bitWidth = 1),
                field("payload", KompactFieldType.Blob, order = 1),
            )

        val errors = LayoutValidator.validateAll(fields, framed = true)

        assertTrue(errors.any { it.contains("byte-aligned") }, errors.toString())
    }

    @Test
    fun validateAll_rejectsCountDependentAlignmentAndParameterizedNested() {
        val fields =
            listOf(
                field(
                    "items",
                    KompactFieldType.Repeated(scalarType("Int")),
                    order = 0,
                    bitWidth = 3,
                ),
                field(
                    "nestedItems",
                    KompactFieldType.Repeated(KompactFieldType.Nested("test.Child", listOf(scalarType("Int")))),
                    order = 1,
                    isNested = true,
                ),
                field(
                    "nested",
                    KompactFieldType.Nested("test.Child", listOf(scalarType("Int"))),
                    order = 2,
                    isNested = true,
                ),
            )

        val errors = LayoutValidator.validateAll(fields, framed = true)

        assertTrue(errors.any { it.contains("count") || it.contains("byte-aligned") }, errors.toString())
        assertTrue(errors.count { it.contains("parameterized model") } == 2, errors.toString())
    }

    @Test
    fun validateAll_rejectsVariableRepeatsAfterUnalignedScalar() {
        val fields =
            listOf(
                field("flag", scalarType("Boolean"), order = 0, bitWidth = 1),
                field(
                    "labels",
                    KompactFieldType.Repeated(KompactFieldType.StringType),
                    order = 1,
                ),
            )

        val errors = LayoutValidator.validateAll(fields, framed = true)

        assertTrue(errors.any { it.contains("byte-aligned") }, errors.toString())
    }

    @Test
    fun validateAll_rejectsUnusedFramingWidths() {
        val fields =
            listOf(
                field("number", scalarType("Int"), order = 0, bitWidth = 8, lengthPrefixWidth = 16),
                field("name", KompactFieldType.StringType, order = 1, repeatCountWidth = 16),
                field(
                    "numbers",
                    KompactFieldType.Repeated(scalarType("Int")),
                    order = 2,
                    bitWidth = 8,
                    lengthPrefixWidth = 16,
                ),
            )

        val errors = LayoutValidator.validateAll(fields, framed = true)

        assertTrue(errors.count { it.contains("cannot override lengthPrefixWidth") } == 2, errors.toString())
        assertTrue(errors.any { it.contains("Non-repeated field 'name'") }, errors.toString())
    }

    @Test
    fun validateAll_rejectsUnsupportedFramedFieldCombinations() {
        val fields =
            listOf(
                field(
                    "scalar",
                    scalarType("Int"),
                    order = 0,
                    bitWidth = 0,
                    bitOffset = 1,
                    lengthPrefixWidth = 7,
                    repeatCountWidth = 16,
                    isNested = true,
                    enumWidth = 1,
                    defaultValue = "1",
                    mutable = true,
                ),
                field(
                    "string",
                    KompactFieldType.StringType,
                    order = 1,
                    bitWidth = 1,
                    isNested = true,
                    lengthPrefixWidth = 0,
                ),
                field("blob", KompactFieldType.Blob, order = 2, bitWidth = 1, lengthPrefixWidth = 7),
                field(
                    "nested",
                    KompactFieldType.Nested("test.Payload"),
                    order = 3,
                    bitWidth = 1,
                    lengthPrefixWidth = 7,
                ),
                field(
                    "repeatedScalar",
                    KompactFieldType.Repeated(scalarType("Int")),
                    order = 4,
                    bitWidth = 0,
                    repeatCountWidth = 7,
                    isNested = true,
                ),
                field(
                    "repeatedString",
                    KompactFieldType.Repeated(KompactFieldType.StringType),
                    order = 5,
                    bitWidth = 1,
                    isNested = true,
                    lengthPrefixWidth = 7,
                ),
                field(
                    "repeatedNested",
                    KompactFieldType.Repeated(KompactFieldType.Nested("test.Payload")),
                    order = 6,
                    bitWidth = 1,
                    isNested = false,
                    lengthPrefixWidth = 7,
                ),
                field(
                    "repeatedRepeat",
                    KompactFieldType.Repeated(KompactFieldType.Repeated(scalarType("Int"))),
                    order = 7,
                ),
                field(
                    "repeatedUnsupported",
                    KompactFieldType.Repeated(KompactFieldType.Unsupported("test.Unsupported")),
                    order = 8,
                ),
                field("unsupported", KompactFieldType.Unsupported("test.Unsupported"), order = 9),
                field("badRepeatedBoolean", KompactFieldType.Repeated(scalarType("Boolean")), order = 10, bitWidth = 0),
                field("badRepeatedInt", KompactFieldType.Repeated(scalarType("Int")), order = 11, bitWidth = 33),
                field("badRepeatedLong", KompactFieldType.Repeated(scalarType("Long")), order = 12, bitWidth = 65),
                field("badRepeatedFloat", KompactFieldType.Repeated(scalarType("Float")), order = 13, bitWidth = 31),
                field("badRepeatedDouble", KompactFieldType.Repeated(scalarType("Double")), order = 14, bitWidth = 32),
                field("badRepeatedLongZero", KompactFieldType.Repeated(scalarType("Long")), order = 15, bitWidth = 0),
                field(
                    "badRepeatedBlob",
                    KompactFieldType.Repeated(KompactFieldType.Blob),
                    order = 16,
                    bitWidth = 1,
                ),
            )

        val errors = LayoutValidator.validateAll(fields, framed = true)

        assertTrue(errors.any { it.contains("immutable") }, errors.toString())
        assertTrue(errors.any { it.contains("bitOffset") }, errors.toString())
        assertTrue(errors.any { it.contains("enumWidth") }, errors.toString())
        assertTrue(errors.any { it.contains("defaultValue") }, errors.toString())
        assertTrue(errors.any { it.contains("repeatCountWidth") }, errors.toString())
        assertTrue(errors.any { it.contains("Nested field") }, errors.toString())
        assertTrue(errors.any { it.contains("cannot contain another repeated field") }, errors.toString())
        assertTrue(errors.any { it.contains("unsupported element type") }, errors.toString())
        assertTrue(errors.any { it.contains("unsupported type") }, errors.toString())
    }

    @Test
    fun validateWidths_rejectsFramingShapesInScalarModels() {
        val fields =
            listOf(
                field("nested", KompactFieldType.Nested("test.Payload"), order = null),
                field("repeated", KompactFieldType.Repeated(scalarType("Int")), order = null),
                field("string", KompactFieldType.StringType, order = null, bitWidth = 0),
            )

        val errors = LayoutValidator.validateWidths(fields)

        assertTrue(errors.size == 2, errors.toString())
    }

    private fun field(
        name: String,
        type: KompactFieldType,
        order: Int?,
        bitOffset: Int = 0,
        bitWidth: Int = 0,
        lengthPrefixWidth: Int = 8,
        repeatCountWidth: Int = 8,
        isNested: Boolean = false,
        enumWidth: Int = 0,
        defaultValue: String = "",
        mutable: Boolean = false,
    ) = KompactFieldInfo(
        name = name,
        type = type,
        order = order,
        bitOffset = bitOffset,
        bitWidth = bitWidth,
        signed = false,
        lengthPrefixWidth = lengthPrefixWidth,
        isNested = isNested,
        repeatCountWidth = repeatCountWidth,
        enumWidth = enumWidth,
        defaultValue = defaultValue,
        isMutable = mutable,
    )
}
