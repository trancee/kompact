package ch.trancee.kompact.ksp.gen

import ch.trancee.kompact.ksp.model.KompactFieldType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName

internal object FramedTypeNames {
    private val list = ClassName("kotlin.collections", "List")
    private val repeatedView = ClassName("ch.trancee.kompact.runtime", "KompactRepeatedView")

    fun propertyType(type: KompactFieldType): TypeName =
        when (type) {
            is KompactFieldType.Repeated -> repeatedView.parameterizedBy(elementType(type.elementType))
            else -> elementType(type)
        }

    fun inputType(type: KompactFieldType): TypeName =
        when (type) {
            is KompactFieldType.Repeated -> list.parameterizedBy(elementType(type.elementType))
            else -> elementType(type)
        }

    private fun elementType(type: KompactFieldType): TypeName =
        if (type is KompactFieldType.Nested) {
            FieldCodeGenerator.resolveFramedNestedTypeName(type)
        } else {
            FieldCodeGenerator.resolveTypeName(type)
        }
}
