package ch.trancee.kompact.ksp.testing

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSBuiltIns
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.PlatformInfo
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSDeclarationContainer
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSPropertyAccessor
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Variance
import kotlin.KotlinVersion

// ------------------------------------------------------------------
// Resolver
// ------------------------------------------------------------------

@OptIn(KspExperimental::class)
class FakeResolver(
    private val modelDeclarations: List<KSAnnotated> = emptyList(),
) : Resolver {
    override fun getNewFiles(): Sequence<KSFile> = emptySequence()

    override fun getAllFiles(): Sequence<KSFile> = emptySequence()

    override fun getSymbolsWithAnnotation(
        annotationName: String,
        inDepth: Boolean,
    ): Sequence<KSAnnotated> =
        modelDeclarations.asSequence().filter { annotated ->
            annotated.annotations.any {
                it.annotationType
                    .resolve()
                    .declaration.qualifiedName
                    ?.asString() == annotationName
            }
        }

    override fun getClassDeclarationByName(name: KSName): KSClassDeclaration? = null

    override fun getFunctionDeclarationsByName(
        name: KSName,
        includeTopLevel: Boolean,
    ): Sequence<KSFunctionDeclaration> = emptySequence()

    override fun getPropertyDeclarationByName(
        name: KSName,
        includeTopLevel: Boolean,
    ): KSPropertyDeclaration? = null

    override fun getTypeArgument(
        typeRef: KSTypeReference,
        variance: Variance,
    ): KSTypeArgument = FakeKSTypeArgument()

    override fun getKSNameFromString(name: String): KSName = FakeKSName(name)

    override fun createKSTypeReferenceFromKSType(type: KSType): KSTypeReference = FakeKSTypeReference("")

    override val builtIns: KSBuiltIns = FakeKSBuiltIns()

    override fun mapToJvmSignature(declaration: KSDeclaration): String? = null

    override fun overrides(
        overrider: KSDeclaration,
        overridee: KSDeclaration,
    ): Boolean = false

    override fun overrides(
        overrider: KSDeclaration,
        overridee: KSDeclaration,
        containingClass: KSClassDeclaration,
    ): Boolean = false

    override fun getJvmName(declaration: KSFunctionDeclaration): String? = null

    override fun getJvmName(accessor: KSPropertyAccessor): String? = null

    override fun getOwnerJvmClassName(declaration: KSPropertyDeclaration): String? = null

    override fun getOwnerJvmClassName(declaration: KSFunctionDeclaration): String? = null

    override fun getJvmCheckedException(declaration: KSFunctionDeclaration): Sequence<KSType> = emptySequence()

    override fun getJvmCheckedException(accessor: KSPropertyAccessor): Sequence<KSType> = emptySequence()

    override fun getDeclarationsFromPackage(packageName: String): Sequence<KSDeclaration> = emptySequence()

    override fun mapJavaNameToKotlin(javaName: KSName): KSName? = null

    override fun mapKotlinNameToJava(kotlinName: KSName): KSName? = null

    override fun getDeclarationsInSourceOrder(container: KSDeclarationContainer): Sequence<KSDeclaration> =
        emptySequence()

    override fun effectiveJavaModifiers(declaration: KSDeclaration): Set<Modifier> = emptySet()

    override fun getJavaWildcard(reference: KSTypeReference): KSTypeReference = reference

    override fun isJavaRawType(type: KSType): Boolean = false

    override fun getPackageAnnotations(packageName: String): Sequence<KSAnnotation> = emptySequence()

    override fun getPackagesWithAnnotation(annotationName: String): Sequence<String> = emptySequence()

    override fun getModuleName(): KSName = FakeKSName("test")
}

// ------------------------------------------------------------------
// PlatformInfo
// ------------------------------------------------------------------

class FakePlatformInfo : PlatformInfo {
    override val platformName: String = "linux"
}

// ------------------------------------------------------------------
// Factory
// ------------------------------------------------------------------

fun createTestEnvironment(
    codeGenerator: CodeGenerator = FakeCodeGenerator(),
    logger: KSPLogger = FakeKSPLogger(),
): SymbolProcessorEnvironment =
    SymbolProcessorEnvironment(
        options = emptyMap(),
        kotlinVersion = KotlinVersion(2, 3, 21),
        codeGenerator = codeGenerator,
        logger = logger,
    )
