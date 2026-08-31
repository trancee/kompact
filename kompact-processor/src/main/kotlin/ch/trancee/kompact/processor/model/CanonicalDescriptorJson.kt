package ch.trancee.kompact.processor.model

import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object CanonicalDescriptorJson {
    private val json = Json { prettyPrint = false }

    fun encode(descriptor: SchemaDescriptor): String =
        json.encodeToString(JsonElement.serializer(), canonicalize(descriptor.toJson()))

    fun sha256(descriptor: SchemaDescriptor): String =
        MessageDigest.getInstance("SHA-256")
            .digest(encode(descriptor).encodeToByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private fun canonicalize(element: JsonElement): JsonElement =
        when (element) {
            is JsonObject ->
                JsonObject(
                    element.entries.sortedBy(Map.Entry<String, JsonElement>::key).associate {
                        (key, value) ->
                        key to canonicalize(value)
                    }
                )
            is JsonArray -> JsonArray(element.map(::canonicalize))
            else -> element
        }

    private fun SchemaDescriptor.toJson(): JsonObject {
        val descriptor = canonicalized()
        return buildJsonObject {
            put("format", "kompact-schema")
            put("formatVersion", 1)
            put("namespace", descriptor.namespace)
            put(
                "schema",
                buildJsonObject {
                    put("bodyBitSize", descriptor.bodyBitSize)
                    put("fields", buildJsonArray { descriptor.fields.forEach { add(it.toJson()) } })
                    put("id", descriptor.schemaId)
                    put("stableName", descriptor.stableName)
                    put("version", descriptor.version)
                    put(
                        "reservedRanges",
                        buildJsonArray { descriptor.reservedRanges.forEach { add(it.toJson()) } },
                    )
                },
            )
        }
    }

    private fun FieldDescriptor.toJson(): JsonObject = buildJsonObject {
        put("bitOffset", bitOffset)
        put("bitWidth", bitWidth)
        put("semantics", semantics.toJson())
        put("stableName", stableName)
        put("type", type.toJson())
    }

    private fun ReservedRangeDescriptor.toJson(): JsonObject = buildJsonObject {
        put("bitOffset", bitOffset)
        put("bitWidth", bitWidth)
        put("stableName", stableName)
    }

    private fun FieldSemantics.toJson(): JsonObject = buildJsonObject {
        maximum?.let { put("maximum", it) }
        minimum?.let { put("minimum", it) }
        if (offset != Rational.ZERO) put("offset", offset.toJson())
        if (scale != Rational.ONE) put("scale", scale.toJson())
        put("semanticType", semanticType)
        unit?.let { put("unit", it) }
    }

    private fun Rational.toJson(): JsonObject = buildJsonObject {
        put("denominator", denominator)
        put("numerator", numerator)
    }

    private fun LogicalType.toJson(): JsonObject =
        when (this) {
            LogicalType.BooleanType -> tagged("boolean")
            LogicalType.SignedInteger -> tagged("signed_integer")
            LogicalType.UnsignedInteger -> tagged("unsigned_integer")
            is LogicalType.FloatType -> tagged("float") { put("bits", bits) }
            is LogicalType.BytesType -> tagged("bytes") { put("count", count) }
            is LogicalType.ArrayType ->
                tagged("array") {
                    put("count", count)
                    put("elementType", elementType.toJson())
                }
            is LogicalType.OptionalType ->
                tagged("optional") { put("valueType", valueType.toJson()) }
            is LogicalType.NestedType ->
                tagged("nested") {
                    put("id", schemaId)
                    put("stableName", stableName)
                    put("version", version)
                }
            is LogicalType.EnumType ->
                tagged("enum") {
                    put(
                        "entries",
                        buildJsonArray {
                            entries
                                .sortedWith(
                                    compareBy(
                                        EnumEntryDescriptor::code,
                                        EnumEntryDescriptor::stableName,
                                    )
                                )
                                .forEach { entry ->
                                    add(
                                        buildJsonObject {
                                            put("code", entry.code)
                                            put("stableName", entry.stableName)
                                        }
                                    )
                                }
                        },
                    )
                }
        }

    private fun tagged(
        kind: String,
        content: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {},
    ): JsonObject = buildJsonObject {
        put("kind", JsonPrimitive(kind))
        content()
    }
}
