package ch.trancee.kompact.ksp

import ch.trancee.kompact.ksp.testing.FakeKSAnnotation
import ch.trancee.kompact.ksp.testing.FakeKSClassDeclaration
import ch.trancee.kompact.ksp.testing.FakeResolver
import com.google.devtools.ksp.symbol.KSTypeParameter
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertTrue

class KompactSymbolProcessorFramedGenericTest {
    @Test
    fun process_framedModel_rejectsGenericSchemaDeclaration() {
        val (processor, codeGen, logger) = createTestSetup(mode = "jvm")
        val parameter =
            Proxy.newProxyInstance(
                KSTypeParameter::class.java.classLoader,
                arrayOf(KSTypeParameter::class.java),
            ) { _, _, _ -> error("Type parameter must not be inspected") } as KSTypeParameter
        val model =
            FakeKSClassDeclaration(
                simpleNameStr = "GenericFrame",
                packageNameStr = "ch.trancee.test",
                typeParametersOverride = listOf(parameter),
                declAnnotations =
                    listOf(
                        FakeKSAnnotation(
                            "ch.trancee.kompact.annotations.KompactModel",
                            mapOf("framed" to true),
                        ),
                    ),
            )

        processor.process(FakeResolver(listOf(model)))

        assertTrue(logger.errors.any { it.contains("type parameters") }, logger.errors.toString())
        assertTrue(codeGen.generatedFiles.isEmpty())
    }
}
