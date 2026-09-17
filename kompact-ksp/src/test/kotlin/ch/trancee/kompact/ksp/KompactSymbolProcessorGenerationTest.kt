package ch.trancee.kompact.ksp

import ch.trancee.kompact.ksp.testing.FakeKSFile
import ch.trancee.kompact.ksp.testing.FakeResolver
import ch.trancee.kompact.ksp.testing.buildModelDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests verifying **generated file content** — what the processor emits
 * into the three generated source files (expect, jvm actual, ios actual)
 * and that the right read/write primitives are selected per field type.
 *
 * Also covers provider instantiation and the empty-symbols no-op path.
 */
class KompactSymbolProcessorGenerationTest {
    @Test
    fun provider_create_returnsKompactSymbolProcessor() {
        val (processor, _, _) = createTestSetup()
        assertEquals("KompactSymbolProcessor", processor::class.simpleName)
    }

    @Test
    fun process_noAnnotatedSymbols_returnsEmptyList() {
        val (processor, _, _) = createTestSetup()
        val resolver = FakeResolver(emptyList())
        val result = processor.process(resolver)
        assertTrue(result.isEmpty())
    }

    @Test
    fun process_validModel_generatesThreeFiles() {
        val (processor, codeGen, _) = createTestSetup()

        val model =
            buildModelDeclaration(
                className = "MyModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        assertTrue(codeGen.generatedFiles.containsKey("ch.trancee.test.MyModelGen.kt"))
        assertTrue(codeGen.generatedFiles.containsKey("ch.trancee.test.MyModelGenJvm.kt"))
        assertTrue(codeGen.generatedFiles.containsKey("ch.trancee.test.MyModelGenIos.kt"))
    }

    @Test
    fun process_validModel_generatesCorrectExpectFile() {
        val (processor, codeGen, _) = createTestSetup()

        val model =
            buildModelDeclaration(
                className = "MyModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        val expectContent = codeGen.generatedFiles["ch.trancee.test.MyModelGen.kt"]!!
        assertTrue(expectContent.contains("package ch.trancee.test"))
        assertTrue(expectContent.contains("value class MyModel"))
        assertTrue(expectContent.contains("val raw: ByteArray"))
        assertTrue(expectContent.contains("require(raw.size >= 2)"))
        assertTrue(expectContent.contains("field"))
    }

    @Test
    fun process_validModel_generatesCorrectJvmActualFile() {
        val (processor, codeGen, _) = createTestSetup()

        val model =
            buildModelDeclaration(
                className = "MyModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        val jvmContent = codeGen.generatedFiles["ch.trancee.test.MyModelGenJvm.kt"]!!
        assertTrue(jvmContent.contains("package ch.trancee.test"))
        assertTrue(jvmContent.contains("@JvmInline"))
        assertTrue(jvmContent.contains("actual value class MyModel"))
        assertTrue(jvmContent.contains("actual var"))
        assertTrue(jvmContent.contains("readBits"))
        assertTrue(jvmContent.contains("writeBits"))
    }

    @Test
    fun process_validModel_generatesCorrectIosActualFile() {
        val (processor, codeGen, _) = createTestSetup()

        val model =
            buildModelDeclaration(
                className = "MyModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        val iosContent = codeGen.generatedFiles["ch.trancee.test.MyModelGenIos.kt"]!!
        assertTrue(iosContent.contains("package ch.trancee.test"))
        assertTrue(iosContent.contains("actual value class MyModel"))
        assertTrue(iosContent.contains("actual var"))
    }

    @Test
    fun process_modelWithBooleanField_usesReadBitsBoolean() {
        val (processor, codeGen, _) = createTestSetup()

        val model =
            buildModelDeclaration(
                className = "BoolModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("flag", "Boolean", 0 to 1)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        val jvmContent = codeGen.generatedFiles["ch.trancee.test.BoolModelGenJvm.kt"]!!
        assertTrue(jvmContent.contains("readBitsBoolean"))
        assertTrue(jvmContent.contains("writeBitsBoolean"))
    }

    @Test
    fun process_modelWithLongField_usesReadBitsLong() {
        val (processor, codeGen, _) = createTestSetup()

        val model =
            buildModelDeclaration(
                className = "LongModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("value", "Long", 0 to 64)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        val jvmContent = codeGen.generatedFiles["ch.trancee.test.LongModelGenJvm.kt"]!!
        assertTrue(jvmContent.contains("readBitsLong"))
        assertTrue(jvmContent.contains("writeBitsLong"))
    }

    @Test
    fun process_modelWithFloatField_usesFloatFromBits() {
        val (processor, codeGen, _) = createTestSetup()

        val model =
            buildModelDeclaration(
                className = "FloatModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("value", "Float", 0 to 32)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        val jvmContent = codeGen.generatedFiles["ch.trancee.test.FloatModelGenJvm.kt"]!!
        assertTrue(jvmContent.contains("Float.fromBits"))
        assertTrue(jvmContent.contains("readBitsLong"))
    }

    @Test
    fun process_modelWithDoubleField_usesDoubleFromBits() {
        val (processor, codeGen, _) = createTestSetup()

        val model =
            buildModelDeclaration(
                className = "DoubleModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("value", "Double", 0 to 64)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        val jvmContent = codeGen.generatedFiles["ch.trancee.test.DoubleModelGenJvm.kt"]!!
        assertTrue(jvmContent.contains("Double.fromBits"))
        assertTrue(jvmContent.contains("readBitsLong"))
    }

    @Test
    fun process_generateModeCommon_emitsOnlyExpectFile() {
        val (processor, codeGen, _) = createTestSetup(mode = "common")

        val model =
            buildModelDeclaration(
                className = "ModeModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        assertTrue(codeGen.generatedFiles.containsKey("ch.trancee.test.ModeModelGen.kt"))
        assertTrue(codeGen.generatedFiles["ch.trancee.test.ModeModelGen.kt"]!!.contains("expect"))
        assertTrue(
            codeGen.generatedFiles.size == 1,
            "Common mode should emit only 1 file, got ${codeGen.generatedFiles.size}",
        )
    }

    @Test
    fun process_generateModeJvm_emitsOnlyJvmActualFile() {
        val (processor, codeGen, _) = createTestSetup(mode = "jvm")

        val model =
            buildModelDeclaration(
                className = "ModeModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        assertTrue(codeGen.generatedFiles.containsKey("ch.trancee.test.ModeModelGenJvm.kt"))
        assertTrue(codeGen.generatedFiles["ch.trancee.test.ModeModelGenJvm.kt"]!!.contains("@JvmInline"))
        assertTrue(
            codeGen.generatedFiles.size == 1,
            "JVM mode should emit only 1 file, got ${codeGen.generatedFiles.size}",
        )
    }

    @Test
    fun process_generateModeIos_emitsOnlyIosActualFile() {
        val (processor, codeGen, _) = createTestSetup(mode = "ios")

        val model =
            buildModelDeclaration(
                className = "ModeModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        assertTrue(codeGen.generatedFiles.containsKey("ch.trancee.test.ModeModelGenIos.kt"))
        assertTrue(codeGen.generatedFiles["ch.trancee.test.ModeModelGenIos.kt"]!!.contains("actual"))
        assertTrue(
            codeGen.generatedFiles.size == 1,
            "IOS mode should emit only 1 file, got ${codeGen.generatedFiles.size}",
        )
    }

    @Test
    fun process_generateModeAll_emitsThreeFiles() {
        val (processor, codeGen, _) = createTestSetup(mode = "all")

        val model =
            buildModelDeclaration(
                className = "AllModeModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        assertTrue(codeGen.generatedFiles.containsKey("ch.trancee.test.AllModeModelGen.kt"))
        assertTrue(codeGen.generatedFiles.containsKey("ch.trancee.test.AllModeModelGenJvm.kt"))
        assertTrue(codeGen.generatedFiles.containsKey("ch.trancee.test.AllModeModelGenIos.kt"))
        assertEquals(3, codeGen.generatedFiles.size, "Explicit 'all' must emit exactly 3 files")
    }

    @Test
    fun create_withUnknownGenerateMode_failsClosed() {
        // S3 (fail-closed) + D1 (no speculative fallback): an unrecognized,
        // non-null kompact.generate value is a misconfiguration, not a
        // silent fall-back to 'all' (which would mis-route expect/actuals
        // for a KMP consumer and produce a non-local compile error).
        val ex =
            assertFailsWith<IllegalArgumentException> {
                createTestSetup(mode = "cmomn")
            }

        // The diagnostic must name the option and the offending value so the
        // failure is localised to the misconfig site (S4: observable).
        assertTrue(ex.message!!.contains("kompact.generate"), "should name the option: ${ex.message}")
        assertTrue(ex.message!!.contains("cmomn"), "should echo the bad value: ${ex.message}")
    }

    @Test
    fun process_validModel_emitsIsolatingDependencies() {
        val (processor, codeGen, _) = createTestSetup()

        val srcFile = FakeKSFile("IsoModel.kt", "ch.trancee.test")
        val model =
            buildModelDeclaration(
                className = "IsoModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
                containingFile = srcFile,
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        // Spec #13(d): per-schema views are *isolating* — regenerated only when the
        // model's own source file changes — not aggregating (Dependencies(true)),
        // which forces a full recompile when *any* source changes. The
        // KompactAnnotations stub is the only aggregating output, and it is
        // hand-authored (not emitted by this processor).
        val expectDeps = codeGen.generatedDependencies["ch.trancee.test.IsoModelGen.kt"]!!
        assertFalse(expectDeps.isAggregating, "per-schema view must be isolating, not aggregating")
        assertEquals(listOf(srcFile), expectDeps.originatingFiles)
    }
}
