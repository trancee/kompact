package ch.trancee.kompact.ksp.model

internal enum class KompactScalarKind(
    val simpleName: String,
) {
    BOOLEAN("Boolean"),
    INT("Int"),
    LONG("Long"),
    FLOAT("Float"),
    DOUBLE("Double"),
}

internal sealed interface KompactFieldType {
    val displayName: String

    data class Scalar(
        val kind: KompactScalarKind,
    ) : KompactFieldType {
        override val displayName: String get() = kind.simpleName
    }

    data object StringType : KompactFieldType {
        override val displayName: String = "String"
    }

    data object Blob : KompactFieldType {
        override val displayName: String = "ByteArray"
    }

    data class Nested(
        val qualifiedName: String,
        val typeArguments: List<KompactFieldType> = emptyList(),
    ) : KompactFieldType {
        override val displayName: String get() = qualifiedName.substringAfterLast('.')
    }

    data class Repeated(
        val elementType: KompactFieldType,
    ) : KompactFieldType {
        override val displayName: String = "List<${elementType.displayName}>"
    }

    data class Unsupported(
        val qualifiedName: String,
    ) : KompactFieldType {
        override val displayName: String = qualifiedName
    }
}

internal fun scalarType(name: String): KompactFieldType =
    when (name) {
        "Boolean", "kotlin.Boolean" -> KompactFieldType.Scalar(KompactScalarKind.BOOLEAN)
        "Int", "kotlin.Int" -> KompactFieldType.Scalar(KompactScalarKind.INT)
        "Long", "kotlin.Long" -> KompactFieldType.Scalar(KompactScalarKind.LONG)
        "Float", "kotlin.Float" -> KompactFieldType.Scalar(KompactScalarKind.FLOAT)
        "Double", "kotlin.Double" -> KompactFieldType.Scalar(KompactScalarKind.DOUBLE)
        "String", "kotlin.String" -> KompactFieldType.StringType
        "ByteArray", "kotlin.ByteArray" -> KompactFieldType.Blob
        else -> KompactFieldType.Unsupported(name)
    }
