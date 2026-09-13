package ch.trancee.kompact.ksp.testing

// ------------------------------------------------------------------
// Model declaration builders — construct FakeKSClassDeclaration instances
// with the @KompactModel / @KompactField annotations used by tests.
// ------------------------------------------------------------------

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
 * isNested, repeatCountWidth, enumWidth, defaultValue)
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
