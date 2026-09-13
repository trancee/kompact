@file:Suppress("DEPRECATION")

package ch.trancee.kompact.ksp.testing

import com.google.devtools.ksp.processing.KSBuiltIns
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
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
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.KSVisitor
import com.google.devtools.ksp.symbol.Location
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import java.util.EnumSet

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

    override fun asMemberOf(containing: KSType): KSType = FakeKSType(typeStr)
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

    override fun asMemberOf(containing: KSType): KSFunction =
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
