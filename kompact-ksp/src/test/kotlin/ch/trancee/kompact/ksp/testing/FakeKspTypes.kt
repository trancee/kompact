package ch.trancee.kompact.ksp.testing

import com.google.devtools.ksp.symbol.AnnotationUseSiteTarget
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.KSVisitor
import com.google.devtools.ksp.symbol.Location
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.Variance

// ------------------------------------------------------------------
// KSAnnotation
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
// KSValueArgument
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
// KSReferenceElement
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
// KSTypeReference
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
// KSType
// ------------------------------------------------------------------

class FakeKSType(
    private val typeName: String,
    private val nullQualifiedName: Boolean = false,
) : KSType {
    override val declaration: KSDeclaration = FakeKSDeclaration(typeName, "", emptyList(), nullQualifiedName)
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
// KSTypeArgument
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
