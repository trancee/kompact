package ch.trancee.kompact.ksp

import ch.trancee.kompact.ksp.testing.FakeKSAnnotation
import ch.trancee.kompact.ksp.testing.FakeKSClassDeclaration
import ch.trancee.kompact.ksp.testing.FakeKSFile
import ch.trancee.kompact.ksp.testing.FakeKSPropertyDeclaration
import ch.trancee.kompact.ksp.testing.FakeResolver
import ch.trancee.kompact.ksp.testing.buildModelDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests verifying **generated file content** — the single common
 * `<Schema>View` source the processor emits per schema — and that the right
 * read/write primitives are selected per field type.
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
    fun process_validModel_generatesSingleCommonViewFile() {
        val (processor, codeGen, _) = createTestSetup()
        val model =
            buildModelDeclaration(
                className = "MyModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
            )

        processor.process(FakeResolver(listOf(model)))

        assertEquals(setOf("ch.trancee.test.MyModelView.kt"), codeGen.generatedFiles.keys)
    }

    @Test
    fun process_validModel_generatesRegularJvmInlineViewWithoutExpectActual() {
        val (processor, codeGen, _) = createTestSetup()
        val model =
            buildModelDeclaration(
                className = "MyModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
            )

        processor.process(FakeResolver(listOf(model)))

        val content = codeGen.generatedFiles.getValue("ch.trancee.test.MyModelView.kt")
        assertTrue(content.contains("package ch.trancee.test"), content)
        assertTrue(content.contains("@JvmInline"), content)
        assertTrue(content.contains("public value class MyModelView(\n  public val raw: ByteArray"), content)
        assertTrue(content.contains("require(raw.size >= 2)"), content)
        assertTrue(content.contains("readBits"), content)
        assertTrue(content.contains("internal fun encodeMyModelView("), content)
        assertTrue(content.contains("public class MyModelViewHolder("), content)
        assertFalse(content.contains("expect "), content)
        assertFalse(content.contains("actual "), content)
    }

    @Test
    fun process_mutableModel_emitsMutableSiblingWithVarSetters() {
        val (processor, codeGen, _) = createTestSetup()

        val model =
            buildModelDeclaration(
                className = "MyModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
                mutable = true,
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        // ADR-0006 D3: mutable=true emits a write-through Mutable<Schema>View sibling.
        val content = codeGen.generatedFiles.getValue("ch.trancee.test.MyModelView.kt")
        assertTrue(content.contains("value class MutableMyModelView"), "mutable=true must emit a Mutable sibling")
        assertTrue(content.contains("public var "), "Mutable sibling must expose var")
        assertTrue(content.contains("writeBits"), "Mutable sibling setter must delegate to writeBits")
        assertTrue(content.contains("set(`value`)"), "Mutable sibling must wire a setter")
        // The default immutable view is still present alongside the escape hatch.
        assertTrue(content.contains("public val `field`"), "default view must remain val")
        assertTrue(content.contains("fun copy("), "default view must still provide copy")
    }

    @Test
    fun process_modelWithPositionalKompactModelArg_ignoresUnnamedArg() {
        val (processor, codeGen, _) = createTestSetup()

        // @KompactModel(true) — a positional (unnamed) argument. Argument parsing
        // is named-only (same as @KompactField in parseField): unnamed args are
        // filtered out by `filter { name != null }`, so the model stays immutable.
        // This also exercises the class-level @KompactModel lookup's filter
        // false-branch (a null-name argument is discarded, not matched).
        val model =
            FakeKSClassDeclaration(
                simpleNameStr = "PosModel",
                packageNameStr = "ch.trancee.test",
                properties =
                    listOf(
                        FakeKSPropertyDeclaration(
                            "field",
                            "ch.trancee.test",
                            "Int",
                            declAnnotations =
                                listOf(
                                    FakeKSAnnotation(
                                        "ch.trancee.kompact.annotations.KompactField",
                                        mapOf("bitOffset" to 0, "bitWidth" to 16),
                                    ),
                                ),
                        ),
                    ),
                declAnnotations =
                    listOf(FakeKSAnnotation("ch.trancee.kompact.annotations.KompactModel", nullNameArgValue = true)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        val content = codeGen.generatedFiles["ch.trancee.test.PosModelView.kt"]!!
        assertFalse(
            content.contains("MutablePosModelView"),
            "unnamed @KompactModel args are skipped (named-only), so mutable stays false",
        )
        assertTrue(content.contains("fun copy("), "default immutable view still emits copy")
    }

    @Test
    fun process_defaultView_emitsValReadOnly() {
        val (processor, codeGen, _) = createTestSetup()

        val model =
            buildModelDeclaration(
                className = "MyModel",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
            )
        val resolver = FakeResolver(listOf(model))

        processor.process(resolver)

        val content = codeGen.generatedFiles["ch.trancee.test.MyModelView.kt"]!!
        // ADR-0006 D1/D4: the default view is immutable — `val`, no write-through setter.
        assertTrue(content.contains("public val `field`"))
        val view = content.substringBefore("public class MyModelViewHolder")
        assertFalse(view.contains("public var "), view)
        assertFalse(view.contains("set(`value`)"), view)
        // ADR-0006 D2/D3: the immutable default view still carries copy(...) so
        // callers can derive an updated frame.
        assertTrue(content.contains("fun copy("), "default view must still provide copy")
        // Without expect/actual, copy carries its `= this.x` defaults directly.
        assertTrue(
            content.contains("`field`: Int = this.field"),
            "copy must default each argument to the current field value: $content",
        )
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

        val jvmContent = codeGen.generatedFiles["ch.trancee.test.BoolModelView.kt"]!!
        assertTrue(jvmContent.contains("readBitsBoolean"))
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

        val jvmContent = codeGen.generatedFiles["ch.trancee.test.LongModelView.kt"]!!
        assertTrue(jvmContent.contains("readBitsLong"))
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

        val jvmContent = codeGen.generatedFiles["ch.trancee.test.FloatModelView.kt"]!!
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

        val jvmContent = codeGen.generatedFiles["ch.trancee.test.DoubleModelView.kt"]!!
        assertTrue(jvmContent.contains("Double.fromBits"))
        assertTrue(jvmContent.contains("readBitsLong"))
    }

    @Test
    fun process_expectSchemaDeclaration_reportsErrorAndGeneratesNothing() {
        val (processor, codeGen, logger) = createTestSetup()
        val model =
            buildModelDeclaration(
                className = "SensorFrame",
                packageName = "ch.trancee.test",
                fields = listOf(Triple("field", "Int", 0 to 16)),
                isExpect = true,
            )

        val deferred = processor.process(FakeResolver(listOf(model)))

        assertTrue(deferred.isEmpty(), "expect schemas are a deterministic error, not deferred")
        assertTrue(codeGen.generatedFiles.isEmpty(), codeGen.generatedFiles.keys.toString())
        assertEquals(1, logger.errors.size, logger.errors.toString())
        val error = logger.errors.single()
        assertTrue(error.contains("Kompact schemas must be regular classes"), error)
        assertTrue(error.contains("SensorFrameView"), error)
        assertTrue(error.contains("Remove `expect` from SensorFrame"), error)
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
        val expectDeps = codeGen.generatedDependencies["ch.trancee.test.IsoModelView.kt"]!!
        assertFalse(expectDeps.isAggregating, "per-schema view must be isolating, not aggregating")
        assertEquals(listOf(srcFile), expectDeps.originatingFiles)
    }
}
