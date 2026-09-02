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
 * logic. Offsets are LSB-first (Ticket 01) and must be densely packed with no
 * gaps or overlaps (Ticket 06: the processor enforces this at compile time).
 *
 * @param bitOffset zero-based LSB-first start bit of the field
 * @param bitWidth  number of bits occupied by the field (1..31)
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
public annotation class KompactField(
    public val bitOffset: Int,
    public val bitWidth: Int,
)
