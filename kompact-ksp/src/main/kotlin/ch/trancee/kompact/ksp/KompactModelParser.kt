package ch.trancee.kompact.ksp

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.KompactFieldType
import ch.trancee.kompact.ksp.model.ModelSpec
import ch.trancee.kompact.ksp.model.scalarType
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType

internal const val KOMPAT_MODEL_FQN = "ch.trancee.kompact.annotations.KompactModel"
private const val KOMPAT_FIELD_FQN = "ch.trancee.kompact.annotations.KompactField"

internal class UnresolvedKompactSymbolException(message: String) : RuntimeException(message)

@OptIn(KspExperimental::class)
internal object KompactModelParser {
    fun parse(declaration: KSClassDeclaration): ModelSpec {
        val modelArgs = modelAnnotationArguments(declaration)
        val framed = (modelArgs["framed"] as? Boolean) ?: false
        val className = declaration.simpleName.asString()
        require(!framed || declaration.typeParameters.isEmpty()) {
            "Framed model $className cannot declare type parameters"
        }
        val fields =
            declaration
                .getDeclaredProperties()
                .mapNotNull { parseField(it, framed) }
                .toList()
        return ModelSpec(
            packageName = declaration.packageName.asString(),
            className = className,
            fields = fields,
            mutable = (modelArgs["mutable"] as? Boolean) ?: false,
            framed = framed,
        )
    }

    private fun parseField(
        prop: KSPropertyDeclaration,
        framed: Boolean,
    ): KompactFieldInfo? {
        val annotation =
            prop.annotations.firstOrNull {
                it.annotationType
                    .resolve()
                    .declaration.qualifiedName
                    ?.asString() ==
                    KOMPAT_FIELD_FQN
            } ?: return null

        val args =
            annotation.arguments
                .filter { it.name != null }
                .associate { it.name!!.asString() to it.value }
        if (framed) {
            listOf("order", "bitOffset", "bitWidth", "lengthPrefixWidth", "repeatCountWidth")
                .forEach { name ->
                    require(args[name] == null || args[name] is Int) {
                        "Field '${prop.simpleName.asString()}' has non-integer $name"
                    }
                }
            require(args["signed"] == null || args["signed"] is Boolean) {
                "Field '${prop.simpleName.asString()}' has non-boolean signed"
            }
        }

        val type = resolveFieldType(prop.type.resolve())
        return KompactFieldInfo(
            name = prop.simpleName.asString(),
            type = type,
            order = args["order"] as? Int,
            bitOffset = (args["bitOffset"] as? Int) ?: 0,
            bitWidth = (args["bitWidth"] as? Int) ?: 0,
            signed = (args["signed"] as? Boolean) ?: false,
            lengthPrefixWidth = (args["lengthPrefixWidth"] as? Int) ?: 8,
            repeatCountWidth = (args["repeatCountWidth"] as? Int) ?: 8,
            isMutable = prop.isMutable,
        )
    }

    private fun modelAnnotationArguments(declaration: KSClassDeclaration): Map<String, Any?> {
        val annotation =
            declaration.annotations
                .first {
                    it.annotationType
                        .resolve()
                        .declaration.qualifiedName!!
                        .asString() == KOMPAT_MODEL_FQN
                }
        val args =
            annotation.arguments
                .filter { it.name != null }
                .associate { it.name!!.asString() to it.value }
        require(args["framed"] == null || args["framed"] is Boolean) {
            "@KompactModel framed must be a Boolean"
        }
        require(args["mutable"] == null || args["mutable"] is Boolean) {
            "@KompactModel mutable must be a Boolean"
        }
        return args
    }

    /** Returns the named `@KompactModel` arguments of [declaration], or null when it is not a Kompact schema. */
    private fun kompactModelArguments(declaration: KSClassDeclaration): Map<String, Any?>? {
        val annotation =
            declaration.annotations.firstOrNull { candidate ->
                candidate.annotationType
                    .resolve()
                    .declaration.qualifiedName
                    ?.asString() == KOMPAT_MODEL_FQN
            } ?: return null
        return annotation.arguments
            .filter { it.name != null }
            .associate { it.name!!.asString() to it.value }
    }

    /**
     * Resolves a property type. Nesting is inferred: a non-scalar, non-`List` type whose declaration is a
     * `@KompactModel(framed = true)` class becomes [KompactFieldType.Nested]; any other Kompact schema is
     * rejected and unknown types stay [KompactFieldType.Unsupported] for the validators to report.
     */
    private fun resolveFieldType(resolved: KSType): KompactFieldType {
        if (resolved.isError) {
            throw UnresolvedKompactSymbolException("Field type could not be resolved")
        }
        require(!resolved.isMarkedNullable) { "Nullable field types are not supported" }
        val declaration = resolved.declaration
        val typeName = (declaration.qualifiedName ?: declaration.simpleName).asString()
        val scalar = scalarType(typeName)
        if (scalar !is KompactFieldType.Unsupported) return scalar

        if (typeName == "List" || typeName == "kotlin.collections.List") {
            require(resolved.arguments.size == 1) {
                "Repeated field $typeName must declare exactly one element type"
            }
            val argumentType =
                requireNotNull(resolved.arguments.single().type) {
                    "Repeated field $typeName cannot use a star-projected element type"
                }.resolve()
            return KompactFieldType.Repeated(resolveFieldType(argumentType))
        }

        val modelArgs =
            (declaration as? KSClassDeclaration)?.let(::kompactModelArguments)
                ?: return KompactFieldType.Unsupported(typeName)
        require(modelArgs["framed"] == true) {
            "Nested field type $typeName must be a @KompactModel(framed = true) class"
        }
        val typeArguments =
            resolved.arguments.map { argument ->
                resolveFieldType(
                    requireNotNull(argument.type) {
                        "Nested field $typeName cannot use a star-projected type argument"
                    }.resolve(),
                )
            }
        return KompactFieldType.Nested(typeName, typeArguments)
    }
}
