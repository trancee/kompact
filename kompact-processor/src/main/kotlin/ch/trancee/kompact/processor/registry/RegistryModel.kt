package ch.trancee.kompact.processor.registry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class RegistryDocument(
    @SerialName("\$schema") val schema: String,
    val formatVersion: Int,
    val namespace: String,
    val maxPacketBytes: Int,
    val schemas: List<RegistrySchema>,
)

@Serializable
internal data class RegistrySchema(
    val stableName: String,
    val id: Int,
    val supersedes: String? = null,
    val versions: List<RegistryVersion>,
)

@Serializable
internal data class RegistryVersion(
    val version: Int,
    val status: RegistryStatus,
    val bodyBitSize: Int,
    val descriptorSha256: String,
)

@Serializable
internal enum class RegistryStatus {
    @SerialName("active") ACTIVE,
    @SerialName("decode-only") DECODE_ONLY,
    @SerialName("retired") RETIRED,
}

internal object RegistryJson {
    private val compact = Json {
        explicitNulls = false
        ignoreUnknownKeys = false
    }
    private val pretty = Json {
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun decode(content: String): RegistryDocument = compact.decodeFromString(content)

    fun encode(registry: RegistryDocument): String =
        pretty.encodeToString(
            registry.copy(
                schemas =
                    registry.schemas.sortedBy(RegistrySchema::id).map { schema ->
                        schema.copy(versions = schema.versions.sortedBy(RegistryVersion::version))
                    }
            )
        ) + "\n"
}
