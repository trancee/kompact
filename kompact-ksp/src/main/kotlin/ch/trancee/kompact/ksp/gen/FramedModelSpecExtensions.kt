package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldInfo
import ch.trancee.kompact.ksp.model.ModelSpec

internal fun ModelSpec.generatedName(): String = "${className}View"

internal fun ModelSpec.orderedFields(): List<KompactFieldInfo> = fields.sortedBy { it.order }

internal fun String.capitalizedFirstChar(): String = replaceFirstChar { it.uppercase() }
