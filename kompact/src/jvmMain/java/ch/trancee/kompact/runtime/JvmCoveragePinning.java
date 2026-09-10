package ch.trancee.kompact.runtime;

import ch.trancee.kompact.Kompact;
import ch.trancee.kompact.generated.VehicleTelemetry;
import java.lang.reflect.Method;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * JVM-only coverage-pinning helpers that force {@code INVOKEVIRTUAL} calls to
 * synthetic {@code getPacked()} / {@code getRaw()} getters on
 * {@code @JvmInline} value classes, and {@code INVOKESTATIC} calls to
 * synthetic {@code $default} bridge methods.
 *
 * <p>The Kotlin compiler always lowers {@code value.packed} to a direct
 * {@code GETFIELD} (field read) — never to {@code INVOKEVIRTUAL getPacked()}
 * — for {@code @JvmInline} value classes, regardless of receiver boxing.
 * JaCoCo/Kover can only record method-level coverage when the call goes through
 * {@code INVOKEVIRTUAL}, so this Java class (whose compiler does not perform
 * this optimisation) exercises the getter by name.</p>
 *
 * <p>Similarly, Kotlin resolves default-parameter values at compile time,
 * calling the main method directly and bypassing the {@code $default} bridge.
 * From Java we call the bridge explicitly (via reflection, since {@code $}
 * in a method name is treated as an inner-class separator by javac).</p>
 *
 * <p>Value-class instances are created via the {@code box-impl} static factory
 * (reflectively, since Kotlin mangles the name with {@code -} which is not a
 * valid Java identifier character). The subsequent {@code getPacked()} call is
 * a genuine {@code INVOKEVIRTUAL} that JaCoCo tracks.</p>
 *
 * <p>All methods are branch-free and assertion-free to maintain 100 % code
 * coverage. The companion Kotlin test ({@code JvmCoveragePinningTest}) performs
 * all value checks and assertions.</p>
 */
// Package-private: test utility, not public API. Accessible from the same
// package's Kotlin test sources (JvmCoveragePinningTest).
class JvmCoveragePinning {

    // ── Packed-value encodings (mirror KompactResult.kt internal helpers) ──

    /** Encodes a ≤32-bit success value: bit 63 set + low 48 bits of value. */
    public static long smallSuccess(long value) {
        return Long.MIN_VALUE | (value & 0x0000_FFFF_FFFF_FFFFL);
    }

    /** Encodes a ≤32-bit failure (BoundsError): kind=0, rawCode=0 → 0. */
    public static long smallFailure() {
        return 0L;
    }

    /** Encodes a LongResult failure (BoundsError): LONG_FAIL_BASE | 0 | 0 = Long.MIN_VALUE. */
    public static long longFailure() {
        return Long.MIN_VALUE;
    }

    /** Success value 42 for LongResult (not in the sentinel range). */
    public static long longSuccess42() {
        return 42L;
    }

    // ── Boxed value-class instances via reflection (box-impl has '-' in name) ─
    // Each overload avoids runtime type-checking branches.

    public static ByteResult boxByte(long packed) throws Exception {
        Method m = ByteResult.class.getMethod("box-impl", long.class);
        return (ByteResult) m.invoke(null, packed);
    }

    public static ShortResult boxShort(long packed) throws Exception {
        Method m = ShortResult.class.getMethod("box-impl", long.class);
        return (ShortResult) m.invoke(null, packed);
    }

    public static IntResult boxInt(long packed) throws Exception {
        Method m = IntResult.class.getMethod("box-impl", long.class);
        return (IntResult) m.invoke(null, packed);
    }

    public static LongResult boxLong(long packed) throws Exception {
        Method m = LongResult.class.getMethod("box-impl", long.class);
        return (LongResult) m.invoke(null, packed);
    }

    public static FloatResult boxFloat(long packed) throws Exception {
        Method m = FloatResult.class.getMethod("box-impl", long.class);
        return (FloatResult) m.invoke(null, packed);
    }

    public static DoubleResult boxDouble(long packed) throws Exception {
        Method m = DoubleResult.class.getMethod("box-impl", long.class);
        return (DoubleResult) m.invoke(null, packed);
    }

    public static BooleanResult boxBoolean(long packed) throws Exception {
        Method m = BooleanResult.class.getMethod("box-impl", long.class);
        return (BooleanResult) m.invoke(null, packed);
    }

    public static ScalarType boxScalarType(int packed) throws Exception {
        Method m = ScalarType.class.getMethod("box-impl", int.class);
        return (ScalarType) m.invoke(null, packed);
    }

    public static NestedRegionResult boxNestedRegion(long packed) throws Exception {
        Method m = NestedRegionResult.class.getMethod("box-impl", long.class);
        return (NestedRegionResult) m.invoke(null, packed);
    }

    public static VehicleTelemetry boxVehicleTelemetry(byte[] raw) throws Exception {
        Method m = VehicleTelemetry.class.getMethod("box-impl", byte[].class);
        return (VehicleTelemetry) m.invoke(null, raw);
    }

    // ── INVOKEVIRTUAL getPacked() on every @JvmInline value class ───────────
    // Each method calls getPacked() (an instance method → INVOKEVIRTUAL).
    // Returns the raw packed long so the caller can assert on it.

    public static long getBytePacked(ByteResult r) {
        return r.getPacked();  // INVOKEVIRTUAL
    }

    public static long getShortPacked(ShortResult r) {
        return r.getPacked();  // INVOKEVIRTUAL
    }

    public static long getIntPacked(IntResult r) {
        return r.getPacked();  // INVOKEVIRTUAL
    }

    public static long getLongPacked(LongResult r) {
        return r.getPacked();  // INVOKEVIRTUAL
    }

    public static long getFloatPacked(FloatResult r) {
        return r.getPacked();  // INVOKEVIRTUAL
    }

    public static long getDoublePacked(DoubleResult r) {
        return r.getPacked();  // INVOKEVIRTUAL
    }

    public static long getBooleanPacked(BooleanResult r) {
        return r.getPacked();  // INVOKEVIRTUAL
    }

    public static int getScalarTypePacked(ScalarType r) {
        return r.getPacked();  // INVOKEVIRTUAL
    }

    public static long getNestedRegionPacked(NestedRegionResult r) {
        return r.getPacked();  // INVOKEVIRTUAL
    }

    // ── VehicleTelemetry.getRaw() via INVOKEVIRTUAL ──────────────────────────

    public static byte[] getVehicleRaw(VehicleTelemetry vt) {
        return vt.getRaw();  // INVOKEVIRTUAL
    }

    // ── Kompact / Kompact.Result class initialisation (<clinit> → <init>) ─

    public static void initializeKompactObjects() {
        Object kompact = Kompact.INSTANCE;
        Object result = Kompact.Result.INSTANCE;
    }

    // ── writeNested$default / writeRepeated$default via reflection ─────────
    // javac treats '$' as inner-class separator; use reflection.
    // Returns build() output so the caller can assert.

    private static final Function1<KompactWriter, Unit> NOOP_BLOCK =
        new Function1<KompactWriter, Unit>() {
            @Override
            public Unit invoke(KompactWriter w) {
                return Unit.INSTANCE;
            }
        };

    public static byte[] writeNestedDefault(int lengthPrefixWidth, int bitMask) throws Exception {
        KompactWriter w = new KompactWriter();
        Method m = KompactWriter.class.getMethod("writeNested$default",
            KompactWriter.class, int.class, Function1.class, int.class, Object.class);
        m.invoke(null, w, lengthPrefixWidth, NOOP_BLOCK, bitMask, null);
        return w.build();
    }

    public static byte[] writeRepeatedDefault(int count, int countWidth, int bitMask) throws Exception {
        KompactWriter w = new KompactWriter();
        Method m = KompactWriter.class.getMethod("writeRepeated$default",
            KompactWriter.class, int.class, int.class, Function1.class, int.class, Object.class);
        m.invoke(null, w, count, countWidth, NOOP_BLOCK, bitMask, null);
        return w.build();
    }

    // ── LongResult.map bridge (map-8pLS_AI) via reflection ────────────────
    // Name contains '-', use reflection.
    // Returns {mappedSuccess, mappedFailure} for the caller to assert.

    public static long[] callMapBridge(long successPacked, long failurePacked) throws Exception {
        Method m = KompactResultExtensionsKt.class.getMethod(
            "map-8pLS_AI", long.class, Function1.class);

        Function1<Long, Long> doubler = new Function1<Long, Long>() {
            @Override public Long invoke(Long d) { return d + 1L; }
        };
        long mappedOk = (long) m.invoke(null, successPacked, doubler);
        long mappedBad = (long) m.invoke(null, failurePacked, doubler);
        return new long[]{mappedOk, mappedBad};
    }

    // ── ShortResult / LongResult.getOrThrow-impl via reflection ────────────
    // Name contains '-', use reflection.
    // On failure path, getOrThrow-impl throws KompactDecodeException, which
    // Method.invoke wraps in InvocationTargetException. The caller (Kotlin
    // test) catches it and verifies the cause type.

    public static short callShortGetOrThrow(long packed) throws Exception {
        Method m = ShortResult.class.getMethod("getOrThrow-impl", long.class);
        return (short) m.invoke(null, packed);
    }

    public static long callLongGetOrThrow(long packed) throws Exception {
        Method m = LongResult.class.getMethod("getOrThrow-impl", long.class);
        return (long) m.invoke(null, packed);
    }
}
