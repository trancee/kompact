package ch.trancee.kompact.processor

import ch.trancee.kompact.processor.registry.RegistryCompatibility
import ch.trancee.kompact.processor.registry.RegistryDocument
import ch.trancee.kompact.processor.registry.RegistryJson
import ch.trancee.kompact.processor.registry.RegistrySchema
import ch.trancee.kompact.processor.registry.RegistryStatus
import ch.trancee.kompact.processor.registry.RegistryVersion
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

public data class KompactRegistryProblem(public val code: String, public val message: String)

public data class KompactRegistryProposal(
    public val json: String,
    public val matchesCheckedInRegistry: Boolean,
    public val problems: List<KompactRegistryProblem>,
)

public object KompactRegistryValidator {
    public fun validate(
        currentJson: String,
        baselineJson: String? = null,
        requireBaseline: Boolean = false,
    ): List<KompactRegistryProblem> =
        try {
            val current = RegistryJson.decode(currentJson)
            val baseline = baselineJson?.let(RegistryJson::decode)
            RegistryCompatibility.validate(current, baseline, requireBaseline).map {
                KompactRegistryProblem(it.code, it.message)
            }
        } catch (_: IllegalArgumentException) {
            malformedRegistryProblem()
        }

    public fun propose(
        currentJson: String,
        canonicalDescriptors: List<String>,
    ): KompactRegistryProposal =
        try {
            val current = RegistryJson.decode(currentJson)
            val proposed = canonicalDescriptors.fold(current, ::applyDescriptor)
            val proposedJson = RegistryJson.encode(proposed)
            KompactRegistryProposal(
                json = proposedJson,
                matchesCheckedInRegistry = RegistryJson.encode(current) == proposedJson,
                problems = emptyList(),
            )
        } catch (_: IllegalArgumentException) {
            KompactRegistryProposal(
                json = currentJson,
                matchesCheckedInRegistry = false,
                problems = malformedRegistryProblem(),
            )
        }

    private fun applyDescriptor(
        registry: RegistryDocument,
        descriptorJson: String,
    ): RegistryDocument {
        val root = Json.parseToJsonElement(descriptorJson).jsonObject
        val schema = root.getValue("schema").jsonObject
        val stableName = schema.getValue("stableName").jsonPrimitive.content
        val schemaId = schema.getValue("id").jsonPrimitive.int
        val version = schema.getValue("version").jsonPrimitive.int
        val bodyBitSize = schema.getValue("bodyBitSize").jsonPrimitive.int
        val descriptorHash = sha256(descriptorJson.trim())
        val schemas = registry.schemas.toMutableList()
        val schemaIndex = schemas.indexOfFirst { it.id == schemaId }
        if (schemaIndex < 0) {
            schemas +=
                RegistrySchema(
                    stableName = stableName,
                    id = schemaId,
                    versions =
                        listOf(
                            RegistryVersion(
                                version = version,
                                status = RegistryStatus.ACTIVE,
                                bodyBitSize = bodyBitSize,
                                descriptorSha256 = descriptorHash,
                            )
                        ),
                )
        } else {
            val oldSchema = schemas[schemaIndex]
            val versions = oldSchema.versions.toMutableList()
            val versionIndex = versions.indexOfFirst { it.version == version }
            val updated =
                RegistryVersion(
                    version = version,
                    status = versions.getOrNull(versionIndex)?.status ?: RegistryStatus.ACTIVE,
                    bodyBitSize = bodyBitSize,
                    descriptorSha256 = descriptorHash,
                )
            if (versionIndex < 0) versions += updated else versions[versionIndex] = updated
            schemas[schemaIndex] = oldSchema.copy(stableName = stableName, versions = versions)
        }
        return registry.copy(schemas = schemas)
    }

    private fun sha256(content: String): String =
        MessageDigest.getInstance("SHA-256").digest(content.encodeToByteArray()).joinToString(
            separator = ""
        ) { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }

    private fun malformedRegistryProblem(): List<KompactRegistryProblem> =
        listOf(
            KompactRegistryProblem(
                code = "KOMPACT-KSP-1008",
                message = "unsupported or malformed registry format",
            )
        )
}
