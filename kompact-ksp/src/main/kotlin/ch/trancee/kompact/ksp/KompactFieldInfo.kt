package ch.trancee.kompact.ksp

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSPropertyDeclaration

/**
 * Resolved metadata for a single `@KompactField`-annotated property.
 */
internal data class KompactFieldInfo(
    val name: String,
    val bitOffset: Int,
    val bitWidth: Int,
    val lengthPrefixBits: Int,
    val enumWidth: Int,
    val signed: Boolean,
    val defaultValue: Int = 0,
) {
    val kotlinType: String
        get() = when {
            lengthPrefixBits > 0 -> "String"
            enumWidth > 0 -> "Int"
            bitWidth == 1 -> "Boolean"
            else -> "Int"
        }

    /** Body of the read call: `KompactRuntime.readBits(raw, 4, 10)` */
    val accessorCall: String
        get() = when {
            lengthPrefixBits > 0 -> "KompactRead.readString(raw, $bitOffset, $lengthPrefixBits)"
            bitWidth == 1 -> "KompactRuntime.readBitsBoolean(raw, $bitOffset)"
            else -> "KompactRuntime.readBits(raw, $bitOffset, $bitWidth)"
        }

    /** Common expect: `val foo: Int` (no body in expect). */
    fun accessorDeclaration(): String = "val $name: $kotlinType"

    /**
     * Actual body: `actual override val foo: Int get() = KompactRead.readUInt8WithDefault(raw, 4, 0)`.
     * Uses the `WithDefault` helper for non-prefixed scalar fields so a
     * newer reader can fall back when an older writer omits the field
     * (Ticket 09).
     */
    fun actualAccessorBody(): String = when {
        lengthPrefixBits > 0 -> "actual override val $name: $kotlinType get() = $accessorCall"
        bitWidth == 1 -> "actual override val $name: $kotlinType get() = KompactRead.readBoolWithDefault(raw, $bitOffset, ${defaultValue != 0})"
        bitWidth <= 8 -> "actual override val $name: $kotlinType get() = KompactRead.readUInt8WithDefault(raw, $bitOffset, $defaultValue)"
        bitWidth <= 16 -> "actual override val $name: $kotlinType get() = KompactRead.readUInt16WithDefault(raw, $bitOffset, $defaultValue)"
        else -> "actual override val $name: $kotlinType get() = $accessorCall"
    }

    companion object {
        fun from(prop: KSPropertyDeclaration, ann: KSAnnotation): KompactFieldInfo? {
            val args = ann.arguments.associateBy { it.name?.asString() ?: "" }
            return KompactFieldInfo(
                name = prop.simpleName.asString(),
                bitOffset = (args["bitOffset"]?.value as? Int) ?: return null,
                bitWidth = (args["bitWidth"]?.value as? Int) ?: return null,
                lengthPrefixBits = (args["lengthPrefixBits"]?.value as? Int) ?: 0,
                enumWidth = (args["enumWidth"]?.value as? Int) ?: 0,
                signed = (args["signed"]?.value as? Boolean) ?: false,
                defaultValue = (args["defaultValue"]?.value as? Int) ?: 0,
            )
        }
    }
}
