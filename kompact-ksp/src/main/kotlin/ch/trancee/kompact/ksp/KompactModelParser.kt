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
            listOf("order", "bitOffset", "bitWidth", "lengthPrefixWidth", "repeatCountWidth", "enumWidth")
                .forEach { name ->
                    require(args[name] == null || args[name] is Int) {
                        "Field '${prop.simpleName.asString()}' has non-integer $name"
                    }
                }
            listOf("isNested", "signed").forEach { name ->
                require(args[name] == null || args[name] is Boolean) {
                    "Field '${prop.simpleName.asString()}' has non-boolean $name"
                }
            }
            require(args["defaultValue"] == null || args["defaultValue"] is String) {
                "Field '${prop.simpleName.asString()}' has non-string defaultValue"
            }
        }

        val nested = (args["isNested"] as? Boolean) ?: false
        val type = resolveFieldType(prop.type.resolve(), nested)
        return KompactFieldInfo(
            name = prop.simpleName.asString(),
            type = type,
            order = args["order"] as? Int,
            bitOffset = (args["bitOffset"] as? Int) ?: 0,
            bitWidth = (args["bitWidth"] as? Int) ?: 0,
            signed = (args["signed"] as? Boolean) ?: false,
            lengthPrefixWidth = (args["lengthPrefixWidth"] as? Int) ?: 8,
            isNested = (args["isNested"] as? Boolean) ?: false,
            repeatCountWidth = (args["repeatCountWidth"] as? Int) ?: 8,
            enumWidth = (args["enumWidth"] as? Int) ?: 0,
            defaultValue = (args["defaultValue"] as? String) ?: "",
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

    private fun resolveFieldType(
        resolved: KSType,
        isNested: Boolean,
    ): KompactFieldType {
        require(!resolved.isError) { "Field type could not be resolved" }
        require(!resolved.isMarkedNullable) { "Nullable field types are not supported" }
        val declaration = resolved.declaration
        val typeName = (declaration.qualifiedName ?: declaration.simpleName).asString()
        val scalar = scalarType(typeName)
        if (scalar !is KompactFieldType.Unsupported) return scalar

        return when (typeName) {
            "List", "kotlin.collections.List" -> {
                require(resolved.arguments.size == 1) {
                    "Repeated field $typeName must declare exactly one element type"
                }
                val argumentType =
                    requireNotNull(resolved.arguments.single().type) {
                        "Repeated field $typeName cannot use a star-projected element type"
                    }.resolve()
                KompactFieldType.Repeated(resolveFieldType(argumentType, isNested))
            }

            else -> {
                if (!isNested) {
                    KompactFieldType.Unsupported(typeName)
                } else {
                    val nestedDeclaration = resolved.declaration as? KSClassDeclaration
                    require(nestedDeclaration != null && isFramedModel(nestedDeclaration)) {
                        "Nested field type $typeName must be a @KompactModel(framed = true) class"
                    }
                    val typeArguments =
                        resolved.arguments.map { argument ->
                            resolveFieldType(
                                requireNotNull(argument.type) {
                                    "Nested field $typeName cannot use a star-projected type argument"
                                }.resolve(),
                                isNested = true,
                            )
                        }
                    KompactFieldType.Nested(typeName, typeArguments)
                }
            }
        }
    }

    private fun isFramedModel(declaration: KSClassDeclaration): Boolean {
        val annotation =
            declaration.annotations.firstOrNull { candidate ->
                candidate.annotationType
                    .resolve()
                    .declaration.qualifiedName
                    ?.asString() == KOMPAT_MODEL_FQN
            } ?: return false
        return annotation.arguments
            .filter { it.name != null }
            .associate { it.name!!.asString() to it.value }["framed"] == true
    }
}
