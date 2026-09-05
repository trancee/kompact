package ch.trancee.kompact.runtime

/**
 * Marks a value class as a Kompact binary schema.
 *
 * A Kompact schema is a multiplatform `value class` over a single `ByteArray`.
 * The processor reads this annotation to validate field layout at compile time
 * (Ticket 06) and is retained only at source level — it is compile-time
 * metadata, not a runtime dependency (PROMPT §2).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class KompactModel

/**
 * Documents a property's bit position and width in the packed `ByteArray`.
 *
 * The Kompact KSP processor reads these to generate the backing read/write
 * logic (Tickets 04, 05, 06, 09). Offsets are LSB-first (Ticket 01) and must be
 * densely packed with no gaps or overlaps (Ticket 06: the processor enforces this).
 *
 * The length-prefix / nesting / repeat / enum / version members are v1 schema
 * metadata consumed by codegen; they carry safe defaults so a plain
 * `@KompactField(bitOffset, bitWidth)` scalar declaration remains valid.
 *
 * @param bitOffset zero-based LSB-first start bit of the field
 * @param bitWidth  number of bits occupied by the field (1..64; for 32-bit use 32)
 * @param lengthPrefixWidth fixed-width LE byte-count prefix width in {8,16,32}
 *        used when the field is a string/blob/nested/repeat (Ticket 05)
 * @param isNested   true when the field is a length-delimited composite region
 * @param repeatCountWidth fixed-width LE count prefix width in {8,16,32}
 *        for repeated fields
 * @param enumWidth    bit width of an enum/ordinal (0 = not an enum)
 * @param defaultValue string-encoded default used by the generated ctor/accessor
 *        when the backing region is absent or zero-filled (Ticket 04)
 * @param isVersionField true for the schema-evolution version-tag field (Ticket 09)
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
public annotation class KompactField(
    public val bitOffset: Int,
    public val bitWidth: Int,
    public val lengthPrefixWidth: Int = 8,
    public val isNested: Boolean = false,
    public val repeatCountWidth: Int = 8,
    public val enumWidth: Int = 0,
    public val defaultValue: String = "",
    public val isVersionField: Boolean = false,
)

