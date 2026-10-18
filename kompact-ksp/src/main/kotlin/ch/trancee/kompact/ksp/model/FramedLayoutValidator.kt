package ch.trancee.kompact.ksp.model

internal object FramedLayoutValidator {
    fun validate(
        fields: List<KompactFieldInfo>,
        mutable: Boolean,
    ): List<String> =
        buildList {
            if (mutable || fields.any { it.isMutable }) {
                add("Framed models and their fields must be immutable")
            }
            val orders = fields.map { it.order }
            if (orders.any { it == null }) {
                add("Every framed field must declare an order")
            } else {
                val concreteOrders = orders.filterNotNull()
                if (concreteOrders.toSet().size != concreteOrders.size) {
                    add("Framed field order values must be unique")
                }
                if (concreteOrders.sorted() != fields.indices.toList()) {
                    add("Framed field order values must be contiguous starting at 0")
                }
            }

            fields.forEach { field ->
                if (field.bitOffset != 0) {
                    add("Framed field '${field.name}' must use bitOffset=0; order defines its position")
                }
                prefixError(field, field.lengthPrefixWidth, "lengthPrefixWidth")?.let(::add)
                prefixError(field, field.repeatCountWidth, "repeatCountWidth")?.let(::add)
                if (field.type !is KompactFieldType.Repeated && field.repeatCountWidth != 8) {
                    add("Non-repeated field '${field.name}' cannot override repeatCountWidth")
                }
                if ((
                        field.type is KompactFieldType.Scalar ||
                            (
                                field.type is KompactFieldType.Repeated &&
                                    field.type.elementType is KompactFieldType.Scalar
                            )
                    ) &&
                    field.lengthPrefixWidth != 8
                ) {
                    add("Scalar field '${field.name}' cannot override lengthPrefixWidth")
                }
                when (val type = field.type) {
                    is KompactFieldType.Scalar, KompactFieldType.StringType, KompactFieldType.Blob -> null
                    is KompactFieldType.Nested ->
                        "Nested field '${field.name}' cannot use a parameterized model"
                            .takeIf { type.typeArguments.isNotEmpty() }
                    is KompactFieldType.Repeated -> validateRepeatedElement(field, type.elementType)
                    is KompactFieldType.Unsupported -> "Field '${field.name}' has unsupported type ${type.qualifiedName}"
                }?.let(::add)
                addAll(LayoutValidator.validateWidths(listOf(field), framed = true))
            }

            var bitRemainder = 0
            var countDependentRemainder = false
            fields.sortedBy { it.order }.forEach { field ->
                when (val type = field.type) {
                    is KompactFieldType.Scalar -> {
                        bitRemainder = (bitRemainder + field.bitWidth) % 8
                    }

                    is KompactFieldType.Repeated -> {
                        if (type.elementType !is KompactFieldType.Scalar) {
                            alignmentError(field, bitRemainder, countDependentRemainder)?.let(::add)
                        }
                        if (type.elementType is KompactFieldType.Scalar && field.bitWidth % 8 != 0) {
                            countDependentRemainder = true
                        }
                    }

                    KompactFieldType.StringType, KompactFieldType.Blob, is KompactFieldType.Nested -> {
                        alignmentError(field, bitRemainder, countDependentRemainder)?.let(::add)
                    }

                    is KompactFieldType.Unsupported -> {
                        return@forEach
                    }
                }
            }
        }

    private fun alignmentError(
        field: KompactFieldInfo,
        bitRemainder: Int,
        countDependentRemainder: Boolean,
    ): String? {
        val position =
            when {
                countDependentRemainder -> "its start depends on a preceding repeat count"
                bitRemainder != 0 -> "it starts $bitRemainder bits into a byte"
                else -> return null
            }
        return "Framed field '${field.name}' needs a byte-aligned start for borrowed payloads; $position"
    }

    private fun validateRepeatedElement(
        field: KompactFieldInfo,
        elementType: KompactFieldType,
    ): String? =
        when (elementType) {
            is KompactFieldType.Scalar, KompactFieldType.StringType, KompactFieldType.Blob -> {
                null
            }

            is KompactFieldType.Nested -> {
                if (elementType.typeArguments.isNotEmpty()) {
                    "Repeated nested field '${field.name}' cannot use a parameterized model"
                } else {
                    null
                }
            }

            is KompactFieldType.Repeated -> {
                "Repeated field '${field.name}' cannot contain another repeated field"
            }

            is KompactFieldType.Unsupported -> {
                "Repeated field '${field.name}' has unsupported element type ${elementType.qualifiedName}"
            }
        }

    private fun prefixError(
        field: KompactFieldInfo,
        width: Int,
        name: String,
    ): String? =
        if (width in LayoutValidator.VALID_PREFIX_WIDTHS) {
            null
        } else {
            "Field '${field.name}' has invalid $name=$width (expected 8, 16, or 32)"
        }
}
