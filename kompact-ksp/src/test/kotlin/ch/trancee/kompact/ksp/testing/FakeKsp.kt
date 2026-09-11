package ch.trancee.kompact.ksp.testing

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSBuiltIns
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.PlatformInfo
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.AnnotationUseSiteTarget
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSDeclarationContainer
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSModifierListOwner
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyAccessor
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSPropertyGetter
import com.google.devtools.ksp.symbol.KSPropertySetter
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.KSVisitor
import com.google.devtools.ksp.symbol.Location
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.Variance
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.EnumSet

// ------------------------------------------------------------------
// KSName — all methods (getQualifier, getShortName are FUNCTIONS)
// ------------------------------------------------------------------

class FakeKSName(
    private val fullName: String,
) : KSName {
    override fun asString(): String = fullName

    override fun getQualifier(): String = fullName.substringBeforeLast('.', "")

    override fun getShortName(): String = fullName.substringAfterLast('.')
}

// ------------------------------------------------------------------
// Location — sealed class; use NonExistLocation object
// ------------------------------------------------------------------

val FakeLocation: Location = com.google.devtools.ksp.symbol.NonExistLocation

// ------------------------------------------------------------------
// KSPLogger — captures messages
// ------------------------------------------------------------------

class FakeKSPLogger : KSPLogger {
    val errors: MutableList<String> = mutableListOf()
    val warnings: MutableList<String> = mutableListOf()
    val infos: MutableList<String> = mutableListOf()

    override fun logging(
        message: String,
        symbol: KSNode?,
    ) {
        infos.add(message)
    }

    override fun info(
        message: String,
        symbol: KSNode?,
    ) {
        infos.add(message)
    }

    override fun warn(
        message: String,
        symbol: KSNode?,
    ) {
        warnings.add(message)
    }

    override fun error(
        message: String,
        symbol: KSNode?,
    ) {
        errors.add(message)
    }

    override fun exception(e: Throwable) {
        errors.add("Exception: ${e.message}")
    }
}

// ------------------------------------------------------------------
// CodeGenerator — captures generated files in memory
// Note: extensionName is a String param (default "kt"), NOT vararg
// generatedFile is a PROPERTY, not a method
// associateWithFunctions/associateWithProperties have default impls
// ------------------------------------------------------------------

class FakeCodeGenerator(
    private val throwOnWrite: Boolean = false,
) : CodeGenerator {
    val generatedFiles: MutableMap<String, String> = mutableMapOf()

    override fun createNewFile(
        dependencies: Dependencies,
        packageName: String,
        fileName: String,
        extensionName: String,
    ): OutputStream {
        val key = "$packageName.$fileName.$extensionName"
        val baos = ByteArrayOutputStream()
        return object : OutputStream() {
            override fun write(b: Int) {
                if (throwOnWrite) throw RuntimeException("Write failed")
                baos.write(b)
            }

            override fun write(
                b: ByteArray,
                off: Int,
                len: Int,
            ) {
                if (throwOnWrite) throw RuntimeException("Write failed")
                baos.write(b, off, len)
            }

            override fun close() {
                baos.close()
                generatedFiles[key] = baos.toString(Charsets.UTF_8.name())
            }
        }
    }

    override fun createNewFileByPath(
        dependencies: Dependencies,
        path: String,
        extensionName: String,
    ): OutputStream = createNewFile(dependencies, path, "generated", extensionName)

    override fun associate(
        sources: List<KSFile>,
        packageName: String,
        fileName: String,
        extensionName: String,
    ) {
    }

    override fun associateByPath(
        sources: List<KSFile>,
        path: String,
        extensionName: String,
    ) {
    }

    override fun associateWithClasses(
        classes: List<KSClassDeclaration>,
        packageName: String,
        fileName: String,
        extensionName: String,
    ) {
    }

    override val generatedFile: Collection<java.io.File> = emptyList()
}

// ------------------------------------------------------------------
// KSAnnotation — extends KSNode
// ------------------------------------------------------------------

class FakeKSAnnotation(
    private val typeFqn: String,
    private val args: Map<String, Any?> = emptyMap(),
    private val nullQualifiedName: Boolean = false,
    private val nullNameArgValue: Any? = null,
) : KSAnnotation {
    override val annotationType: KSTypeReference = FakeKSTypeReference(typeFqn, nullQualifiedName = nullQualifiedName)
    override val arguments: List<KSValueArgument> =
        buildList {
            args.forEach { (name, value) -> add(FakeKSValueArgument(name, value)) }
            if (nullNameArgValue != null) add(FakeKSValueArgument(null, nullNameArgValue))
        }
    override val defaultArguments: List<KSValueArgument> = emptyList()
    override val shortName: KSName = FakeKSName(typeFqn.substringAfterLast('.'))
    override val useSiteTarget: AnnotationUseSiteTarget? = null

    // KSNode
    override val origin: Origin = Origin.KOTLIN
    override val location: Location = FakeLocation
    override val parent: KSNode? = null

    override fun <D, R> accept(
        visitor: KSVisitor<D, R>,
        data: D,
    ): R = visitor.visitNode(this, data)
}

// ------------------------------------------------------------------
// KSValueArgument — extends KSAnnotated
// ------------------------------------------------------------------

class FakeKSValueArgument(
    private val argName: String?,
    private val argValue: Any?,
) : KSValueArgument {
    override val name: KSName? = argName?.let { FakeKSName(it) }
    override val isSpread: Boolean = false
    override val value: Any? = argValue

    // KSAnnotated
    override val annotations: Sequence<KSAnnotation> = emptySequence()

    // KSNode (inherited via KSAnnotated -> KSNode)
    override val origin: Origin = Origin.KOTLIN
    override val location: Location = FakeLocation
    override val parent: KSNode? = null

    override fun <D, R> accept(
        visitor: KSVisitor<D, R>,
        data: D,
    ): R = visitor.visitNode(this, data)
}

// ------------------------------------------------------------------
// KSReferenceElement — extends KSNode
// ------------------------------------------------------------------

class FakeKSReferenceElement : com.google.devtools.ksp.symbol.KSReferenceElement {
    override val typeArguments: List<KSTypeArgument> = emptyList()

    // KSNode
    override val origin: Origin = Origin.KOTLIN
    override val location: Location = FakeLocation
    override val parent: KSNode? = null

    override fun <D, R> accept(
        visitor: KSVisitor<D, R>,
        data: D,
    ): R = visitor.visitNode(this, data)
}

// ------------------------------------------------------------------
// KSTypeReference — extends KSAnnotated, KSModifierListOwner
// ------------------------------------------------------------------

class FakeKSTypeReference(
    private val typeName: String,
    private val typeAnnotations: List<KSAnnotation> = emptyList(),
    private val nullQualifiedName: Boolean = false,
) : KSTypeReference {
    override val element: com.google.devtools.ksp.symbol.KSReferenceElement? = null

    override fun resolve(): KSType = FakeKSType(typeName, nullQualifiedName)

    // KSAnnotated
    override val annotations: Sequence<KSAnnotation> = typeAnnotations.asSequence()

    // KSModifierListOwner
    override val modifiers: Set<Modifier> = emptySet()

    // KSNode
    override val origin: Origin = Origin.KOTLIN
    override val location: Location = FakeLocation
    override val parent: KSNode? = null

    override fun <D, R> accept(
        visitor: KSVisitor<D, R>,
        data: D,
    ): R = visitor.visitNode(this, data)
}

// ------------------------------------------------------------------
// KSType — does NOT extend KSNode
// ------------------------------------------------------------------

class FakeKSType(
    private val typeName: String,
    private val nullQualifiedName: Boolean = false,
) : KSType {
    override val declaration: KSDeclaration =
        FakeKSDeclaration(typeName, "", emptyList(), nullQualifiedName)
    override val nullability: Nullability = Nullability.NOT_NULL
    override val arguments: List<KSTypeArgument> = emptyList()
    override val annotations: Sequence<KSAnnotation> = emptySequence()

    override fun isAssignableFrom(that: KSType): Boolean = false

    override fun isMutabilityFlexible(): Boolean = false

    override fun isCovarianceFlexible(): Boolean = false

    override fun replace(arguments: List<KSTypeArgument>): KSType = this

    override fun starProjection(): KSType = this

    override fun makeNullable(): KSType = this

    override fun makeNotNullable(): KSType = this

    override val isMarkedNullable: Boolean = false
    override val isError: Boolean = false
    override val isFunctionType: Boolean = false
    override val isSuspendFunctionType: Boolean = false
}

// ------------------------------------------------------------------
// KSTypeArgument — extends KSAnnotated
// ------------------------------------------------------------------

class FakeKSTypeArgument : KSTypeArgument {
    override val variance: Variance = Variance.INVARIANT
    override val type: KSTypeReference? = null

    // KSAnnotated
    override val annotations: Sequence<KSAnnotation> = emptySequence()

    // KSNode
    override val origin: Origin = Origin.KOTLIN
    override val location: Location = FakeLocation
    override val parent: KSNode? = null

    override fun <D, R> accept(
        visitor: KSVisitor<D, R>,
        data: D,
    ): R = visitor.visitNode(this, data)
}

// ------------------------------------------------------------------
// KSDeclaration — extends KSModifierListOwner, KSAnnotated, KSExpectActual
// (KSDeclaration does NOT extend KSDeclarationContainer)
// ------------------------------------------------------------------

open class FakeKSDeclaration(
    internal val simpleNameStr: String,
    internal val packageNameStr: String,
    private val declAnnotations: List<KSAnnotation> = emptyList(),
    private val nullQualifiedName: Boolean = false,
) : KSDeclaration {
    override val simpleName: KSName = FakeKSName(simpleNameStr)
    override val qualifiedName: KSName? =
        if (nullQualifiedName) {
            null
        } else {
            FakeKSName(if (packageNameStr.isEmpty()) simpleNameStr else "$packageNameStr.$simpleNameStr")
        }
    override val typeParameters: List<KSTypeParameter> = emptyList()
    override val packageName: KSName = FakeKSName(packageNameStr)
    override val parentDeclaration: KSDeclaration? = null
    override val containingFile: KSFile? = null
    override val docString: String? = null

    // KSExpectActual
    override val isActual: Boolean = false
    override val isExpect: Boolean = false

    override fun findActuals(): Sequence<KSDeclaration> = emptySequence()

    override fun findExpects(): Sequence<KSDeclaration> = emptySequence()

    // KSModifierListOwner
    override val modifiers: Set<Modifier> = EnumSet.noneOf(Modifier::class.java)

    // KSAnnotated
    override val annotations: Sequence<KSAnnotation> = declAnnotations.asSequence()

    // KSNode
    override val origin: Origin = Origin.KOTLIN
    override val location: Location = FakeLocation
    override val parent: KSNode? = null

    override fun <D, R> accept(
        visitor: KSVisitor<D, R>,
        data: D,
    ): R = visitor.visitNode(this, data)
}

// ------------------------------------------------------------------
// KSPropertyDeclaration — extends KSDeclaration
// ------------------------------------------------------------------

class FakeKSPropertyDeclaration(
    simpleNameStr: String,
    packageNameStr: String,
    private val typeStr: String,
    declAnnotations: List<KSAnnotation> = emptyList(),
) : FakeKSDeclaration(simpleNameStr, packageNameStr, declAnnotations),
    KSPropertyDeclaration {
    override val getter: KSPropertyGetter? = null
    override val setter: KSPropertySetter? = null
    override val extensionReceiver: KSTypeReference? = null
    override val type: KSTypeReference = FakeKSTypeReference(typeStr)
    override val isMutable: Boolean = true
    override val hasBackingField: Boolean = false

    override fun isDelegated(): Boolean = false

    override fun findOverridee(): KSPropertyDeclaration? = null

    override fun asMemberOf(owningType: KSType): KSType = FakeKSType(typeStr)
}

// ------------------------------------------------------------------
// KSClassDeclaration — extends KSDeclaration, KSDeclarationContainer
// ------------------------------------------------------------------

class FakeKSClassDeclaration(
    simpleNameStr: String,
    packageNameStr: String,
    private val properties: List<KSPropertyDeclaration> = emptyList(),
    declAnnotations: List<KSAnnotation> = emptyList(),
) : FakeKSDeclaration(simpleNameStr, packageNameStr, declAnnotations),
    KSClassDeclaration {
    override val classKind: ClassKind = ClassKind.CLASS
    override val primaryConstructor: KSFunctionDeclaration? = null
    override val superTypes: Sequence<KSTypeReference> = emptySequence()
    override val isCompanionObject: Boolean = false

    override fun getSealedSubclasses(): Sequence<KSClassDeclaration> = emptySequence()

    override fun getAllFunctions(): Sequence<KSFunctionDeclaration> = emptySequence()

    override fun getAllProperties(): Sequence<KSPropertyDeclaration> = emptySequence()

    override val declarations: Sequence<KSDeclaration> = properties.asSequence()

    override fun asType(typeArguments: List<KSTypeArgument>): KSType = FakeKSType(simpleNameStr)

    override fun asStarProjectedType(): KSType = FakeKSType(simpleNameStr)
}

// ------------------------------------------------------------------
// KSFunctionDeclaration — extends KSDeclaration, KSDeclarationContainer
// ------------------------------------------------------------------

class FakeKSFunctionDeclaration(
    simpleNameStr: String,
    packageNameStr: String,
) : FakeKSDeclaration(simpleNameStr, packageNameStr, emptyList()),
    KSFunctionDeclaration {
    override val functionKind: FunctionKind = FunctionKind.TOP_LEVEL
    override val isAbstract: Boolean = false
    override val extensionReceiver: KSTypeReference? = null
    override val returnType: KSTypeReference? = null
    override val parameters: List<KSValueParameter> = emptyList()
    override val declarations: Sequence<KSDeclaration> = emptySequence()

    override fun findOverridee(): KSDeclaration? = null

    override fun asMemberOf(owningType: KSType): KSFunction =
        throw UnsupportedOperationException("Not needed for tests")
}

// ------------------------------------------------------------------
// KSPropertyAccessor — extends KSDeclarationContainer, KSAnnotated, KSModifierListOwner
// ------------------------------------------------------------------

class FakeKSPropertyAccessor : KSPropertyAccessor {
    override val receiver: KSPropertyDeclaration =
        FakeKSPropertyDeclaration("dummy", "", "Unit")

    // KSDeclarationContainer
    override val declarations: Sequence<KSDeclaration> = emptySequence()

    // KSAnnotated
    override val annotations: Sequence<KSAnnotation> = emptySequence()

    // KSModifierListOwner
    override val modifiers: Set<Modifier> = emptySet()

    // KSNode
    override val origin: Origin = Origin.KOTLIN
    override val location: Location = FakeLocation
    override val parent: KSNode? = null

    override fun <D, R> accept(
        visitor: KSVisitor<D, R>,
        data: D,
    ): R = visitor.visitNode(this, data)
}

// ------------------------------------------------------------------
// KSValueParameter — extends KSAnnotated
// ------------------------------------------------------------------

class FakeKSValueParameter : KSValueParameter {
    override val name: KSName? = null
    override val type: KSTypeReference = FakeKSTypeReference("")
    override val isVararg: Boolean = false
    override val isNoInline: Boolean = false
    override val isCrossInline: Boolean = false
    override val isVal: Boolean = false
    override val isVar: Boolean = false
    override val hasDefault: Boolean = false

    // KSAnnotated
    override val annotations: Sequence<KSAnnotation> = emptySequence()

    // KSNode
    override val origin: Origin = Origin.KOTLIN
    override val location: Location = FakeLocation
    override val parent: KSNode? = null

    override fun <D, R> accept(
        visitor: KSVisitor<D, R>,
        data: D,
    ): R = visitor.visitNode(this, data)
}

// ------------------------------------------------------------------
// KSFile — extends KSDeclarationContainer, KSAnnotated
// (KSFile does NOT extend KSModifierListOwner, so no 'modifiers')
// ------------------------------------------------------------------

class FakeKSFile(
    private val name: String,
    private val pkg: String,
) : KSFile {
    override val packageName: KSName = FakeKSName(pkg)
    override val fileName: String = name
    override val filePath: String = name
    override val declarations: Sequence<KSDeclaration> = emptySequence()
    override val annotations: Sequence<KSAnnotation> = emptySequence()
    override val origin: Origin = Origin.KOTLIN
    override val location: Location = FakeLocation
    override val parent: KSNode? = null

    override fun <D, R> accept(
        visitor: KSVisitor<D, R>,
        data: D,
    ): R = visitor.visitNode(this, data)
}

// ------------------------------------------------------------------
// KSBuiltIns
// ------------------------------------------------------------------

class FakeKSBuiltIns : KSBuiltIns {
    override val anyType: KSType = FakeKSType("Any")
    override val nothingType: KSType = FakeKSType("Nothing")
    override val unitType: KSType = FakeKSType("Unit")
    override val numberType: KSType = FakeKSType("Number")
    override val byteType: KSType = FakeKSType("Byte")
    override val shortType: KSType = FakeKSType("Short")
    override val intType: KSType = FakeKSType("Int")
    override val longType: KSType = FakeKSType("Long")
    override val floatType: KSType = FakeKSType("Float")
    override val doubleType: KSType = FakeKSType("Double")
    override val charType: KSType = FakeKSType("Char")
    override val booleanType: KSType = FakeKSType("Boolean")
    override val stringType: KSType = FakeKSType("String")
    override val iterableType: KSType = FakeKSType("Iterable")
    override val annotationType: KSType = FakeKSType("Annotation")
    override val arrayType: KSType = FakeKSType("Array")
}

// ------------------------------------------------------------------
// Resolver — many methods are @KspExperimental
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
        kotlinVersion = KotlinVersion(2, 4, 20),
        codeGenerator = codeGenerator,
        logger = logger,
    )

fun buildModelDeclaration(
    className: String,
    packageName: String,
    fields: List<Triple<String, String, Pair<Int, Int>>>,
): FakeKSClassDeclaration {
    val props =
        fields.map { (name, kotlinType, offsets) ->
            val (bitOffset, bitWidth) = offsets
            FakeKSPropertyDeclaration(
                simpleNameStr = name,
                packageNameStr = packageName,
                typeStr = kotlinType,
                declAnnotations =
                    listOf(
                        FakeKSAnnotation(
                            typeFqn = "ch.trancee.kompact.annotations.KompactField",
                            args =
                                buildMap {
                                    put("bitOffset", bitOffset)
                                    put("bitWidth", bitWidth)
                                },
                        ),
                    ),
            )
        }

    return FakeKSClassDeclaration(
        simpleNameStr = className,
        packageNameStr = packageName,
        properties = props,
        declAnnotations = listOf(FakeKSAnnotation("ch.trancee.kompact.annotations.KompactModel")),
    )
}

/**
 * Builds a @KompactModel class declaration where every @KompactField
 * annotation carries ALL optional members (signed, lengthPrefixWidth,
 * isNested, repeatCountWidth, enumWidth, defaultValue, isVersionField)
 * so the processor's `as? Int` / `as? Boolean` / `as? String` cast
 * **success** branches are exercised.
 */
fun buildModelDeclarationWithAllArgs(
    className: String,
    packageName: String,
    fields: List<Triple<String, String, Pair<Int, Int>>>,
): FakeKSClassDeclaration {
    val props =
        fields.map { (name, kotlinType, offsets) ->
            val (bitOffset, bitWidth) = offsets
            FakeKSPropertyDeclaration(
                simpleNameStr = name,
                packageNameStr = packageName,
                typeStr = kotlinType,
                declAnnotations =
                    listOf(
                        FakeKSAnnotation(
                            typeFqn = "ch.trancee.kompact.annotations.KompactField",
                            args =
                                buildMap {
                                    put("bitOffset", bitOffset)
                                    put("bitWidth", bitWidth)
                                    put("signed", bitOffset > 10)
                                    put("lengthPrefixWidth", 8)
                                    put("isNested", false)
                                    put("repeatCountWidth", 8)
                                    put("enumWidth", 2)
                                    put("defaultValue", "0")
                                    put("isVersionField", false)
                                },
                        ),
                    ),
            )
        }

    return FakeKSClassDeclaration(
        simpleNameStr = className,
        packageNameStr = packageName,
        properties = props,
        declAnnotations = listOf(FakeKSAnnotation("ch.trancee.kompact.annotations.KompactModel")),
    )
}
