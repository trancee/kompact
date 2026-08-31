package ch.trancee.kompact.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class KompactSchema(
    public val registryName: String,
    public val id: Int,
    public val version: Int,
)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
public annotation class KompactField(
    public val stableName: String,
    public val semanticType: String,
    public val bitOffset: Int,
    public val bitWidth: Int,
    public val unit: String = "",
    public val scaleNumerator: String = "1",
    public val scaleDenominator: String = "1",
    public val offsetNumerator: String = "0",
    public val offsetDenominator: String = "1",
    public val minimum: String = "",
    public val maximum: String = "",
)

@Repeatable
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class KompactReserved(
    public val stableName: String,
    public val bitOffset: Int,
    public val bitWidth: Int,
)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class KompactEnum(public val bitWidth: Int)

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
public annotation class KompactCode(public val stableName: String, public val code: Long)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
public annotation class KompactBytes(public val count: Int)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
public annotation class KompactArray(public val count: Int)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
public annotation class KompactOptional

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
public annotation class KompactNested(
    public val registryName: String,
    public val schemaId: Int,
    public val version: Int,
)
