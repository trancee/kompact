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
                if (field.enumWidth != 0) {
                    add("Framed field '${field.name}' cannot declare enumWidth")
                }
                if (field.defaultValue.isNotEmpty()) {
                    add("Framed field '${field.name}' cannot declare defaultValue")
                }
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
                    is KompactFieldType.Scalar -> {
                        if (field.isNested) add("Scalar field '${field.name}' cannot be marked nested")
                    }

                    KompactFieldType.StringType, KompactFieldType.Blob -> {
                        if (field.isNested) add("Field '${field.name}' cannot be marked nested")
                    }

                    is KompactFieldType.Nested -> {
                        if (!field.isNested) add("Nested field '${field.name}' must set isNested=true")
                        if (type.typeArguments.isNotEmpty()) {
                            add("Nested field '${field.name}' cannot use a parameterized model")
                        }
                    }

                    is KompactFieldType.Repeated -> {
                        validateRepeatedElement(field, type.elementType)?.let(::add)
                    }

                    is KompactFieldType.Unsupported -> {
                        add("Field '${field.name}' has unsupported type ${type.qualifiedName}")
                    }
                }
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
                        if ((bitRemainder != 0 || countDependentRemainder) &&
                            type.elementType !is KompactFieldType.Scalar
                        ) {
                            add("Framed field '${field.name}' needs a byte-aligned start for borrowed payloads")
                        }
                        if (type.elementType is KompactFieldType.Scalar && field.bitWidth % 8 != 0) {
                            countDependentRemainder = true
                        }
                    }

                    KompactFieldType.StringType, KompactFieldType.Blob, is KompactFieldType.Nested -> {
                        if (bitRemainder != 0 || countDependentRemainder) {
                            add("Framed field '${field.name}' needs a byte-aligned start for borrowed payloads")
                        }
                    }

                    is KompactFieldType.Unsupported -> {
                        return@forEach
                    }
                }
            }
        }

    private fun validateRepeatedElement(
        field: KompactFieldInfo,
        elementType: KompactFieldType,
    ): String? =
        when (elementType) {
            is KompactFieldType.Scalar -> {
                if (field.isNested) "Repeated scalar field '${field.name}' cannot be marked nested" else null
            }

            KompactFieldType.StringType, KompactFieldType.Blob -> {
                if (field.isNested) {
                    "Repeated field '${field.name}' cannot be marked nested"
                } else {
                    null
                }
            }

            is KompactFieldType.Nested -> {
                if (elementType.typeArguments.isNotEmpty()) {
                    "Repeated nested field '${field.name}' cannot use a parameterized model"
                } else if (!field.isNested) {
                    "Repeated nested field '${field.name}' must set isNested=true"
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
