@file:OptIn(KompactPreview::class)

package ch.trancee.kompact.annotations

/**
 * Marks a Kompact binary schema. The default fixed layout generates platform
 * value classes; [framed] generates bounded regular classes for sequential fields.
 *
 * The processor treats the annotated declaration as source schema metadata.
 * Fixed-layout schemas generate platform value classes over a `ByteArray`;
 * framed schemas generate bounded view classes. This annotation is retained
 * only at source level and is not a runtime dependency. [mutable] applies to
 * fixed-layout models; mutable framed models are rejected.
 */
@KompactPreview
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class KompactModel(
    public val mutable: Boolean = false,
    public val framed: Boolean = false,
)

/**
 * Describes a property in a fixed-layout or sequential framed schema.
 *
 * The Kompact KSP processor reads these to generate the backing read/write
 * logic. Fixed-layout offsets are LSB-first and must not overlap; dense
 * packing is not required, so gap bits are permitted.
 *
 * Framed fields use [order] (contiguous from zero), not [bitOffset]. Scalar
 * fields specify [bitWidth]; strings, blobs and nested fields use a prefix width.
 * Repeats are `List<T>` with [repeatCountWidth], plus [bitWidth] for scalar
 * elements or [lengthPrefixWidth] for variable-length elements.
 * Fixed-layout annotations retain their original bit-offset meaning.
 *
 * @param bitOffset zero-based LSB-first start bit of the field
 * @param bitWidth  number of bits occupied by the field (1..64; for 32-bit use 32)
 * @param signed   true for two's-complement, false for unsigned magnitude (v1-spec-04: type set)
 * @param lengthPrefixWidth fixed-width LE byte-count prefix width in {8,16,32}
 *        used when the field is a string/blob/nested/repeat (Ticket 05)
 * @param isNested   true when the field is a length-delimited composite region
 * @param repeatCountWidth fixed-width LE count prefix width in {8,16,32}
 *        for repeated fields
 * @param enumWidth    bit width of an enum/ordinal (0 = not an enum)
 * @param defaultValue string-encoded default metadata; it is not currently
 *        supported by code generation
 * @param order zero-based sequential position in a framed schema; unused in fixed layouts
 */
@KompactPreview
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
public annotation class KompactField(
    public val bitOffset: Int = 0,
    public val bitWidth: Int = 0,
    public val signed: Boolean = false,
    public val lengthPrefixWidth: Int = 8,
    public val isNested: Boolean = false,
    public val repeatCountWidth: Int = 8,
    public val enumWidth: Int = 0,
    public val defaultValue: String = "",
    public val order: Int = -1,
)
