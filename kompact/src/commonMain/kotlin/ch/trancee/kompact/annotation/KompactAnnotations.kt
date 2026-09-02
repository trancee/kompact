package ch.trancee.kompact.annotation

/**
 * Spec: .scratch/kompact-spec/issues/02-generation-strategy.md
 *        .scratch/kompact-spec/issues/06-validation-model.md
 *        .scratch/kompact-spec/issues/09-versioning-schema-evolution.md
 *
 * Schema annotations consumed by the `:kompact-ksp` Symbol Processor.
 * These are written by hand in :kompact:commonMain so the annotations are
 * available on the consumer's classpath (per Ticket 12's split: runtime
 * has no annotations; the KSP processor generates stubs into the
 * consumer's `commonMain` source root). This file is the *published*
 * shape — the ksp-stubs variant emitted by the processor is
 * byte-identical to this file (Ticket 02/13).
 */

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
public annotation class KompactModel

/**
 * Marks a property on a `@KompactModel` value class as a packed field.
 *
 * @property bitOffset the bit index within the buffer where this field
 *   starts, 0-based, MSB-first in declaration order, LSB-first within
 *   the byte stream (Ticket 01).
 * @property bitWidth the field's width in bits, 1..64.
 * @property lengthPrefixBits when the field is length-delimited
 *   (Ticket 05), the bit width of its little-endian length prefix.
 *   Must be one of 8, 16, 32 if present; 0 otherwise.
 * @property enumWidth when the field is a dense-ordinal enum (Ticket 04),
 *   the bit width of the wire ordinal, 1..8. 0 otherwise.
 * @property signed when true (signed integer types), the assembled
 *   magnitude is interpreted as a two's-complement signed value
 *   (Ticket 04). When false (unsigned), the assembled value is
 *   zero-extended.
 * @property defaultValue default value used when a newer reader sees
 *   an older stream that does not contain this field (Ticket 09).
 *   Defaults to 0 (the type's zero for Int / `false` for Boolean).
 *   Applied at read time by the `KompactRead.readXxxWithDefault`
 *   helpers when the buffer is short for the declared field.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
public annotation class KompactField(
    val bitOffset: Int,
    val bitWidth: Int,
    val lengthPrefixBits: Int = 0,
    val enumWidth: Int = 0,
    val signed: Boolean = false,
    val defaultValue: Int = 0,
)
