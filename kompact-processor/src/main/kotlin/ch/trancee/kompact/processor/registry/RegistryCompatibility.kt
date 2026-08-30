package ch.trancee.kompact.processor.registry

internal data class RegistryDiagnostic(val code: String, val message: String)

internal object RegistryCompatibility {
    private val stableNamePattern = Regex("[a-z][a-z0-9_]*")
    private val hashPattern = Regex("[0-9a-f]{64}")

    fun validate(
        current: RegistryDocument,
        baseline: RegistryDocument? = null,
        requireBaseline: Boolean = false,
        availableDescriptors: Set<Pair<Int, Int>> = emptySet(),
    ): List<RegistryDiagnostic> {
        val diagnostics = mutableListOf<RegistryDiagnostic>()
        validateCurrent(current, availableDescriptors, diagnostics)
        if (baseline == null) {
            if (requireBaseline) {
                diagnostics += diagnostic(1009, "compatibility baseline is required")
            }
        } else {
            compareHistory(baseline, current, diagnostics)
        }
        return diagnostics.sortedWith(
            compareBy(RegistryDiagnostic::code, RegistryDiagnostic::message)
        )
    }

    private fun validateCurrent(
        registry: RegistryDocument,
        availableDescriptors: Set<Pair<Int, Int>>,
        diagnostics: MutableList<RegistryDiagnostic>,
    ) {
        if (registry.formatVersion != 1) {
            diagnostics += diagnostic(1008, "unsupported registry format ${registry.formatVersion}")
        }
        if (!stableNamePattern.matches(registry.namespace)) {
            diagnostics += diagnostic(1003, "invalid protocol namespace '${registry.namespace}'")
        }
        if (registry.maxPacketBytes < 2) {
            diagnostics +=
                diagnostic(1003, "maximum packet size must include the two-byte envelope")
        }

        val schemaIds = mutableSetOf<Int>()
        val schemaNames = mutableSetOf<String>()
        for (schema in registry.schemas) {
            if (
                schema.id !in 1..0x0FFF ||
                    !schemaIds.add(schema.id) ||
                    !schemaNames.add(schema.stableName)
            ) {
                diagnostics +=
                    diagnostic(
                        1005,
                        "duplicate or invalid schema identity ${schema.stableName}/${schema.id}",
                    )
            }
            if (!stableNamePattern.matches(schema.stableName)) {
                diagnostics += diagnostic(1003, "invalid stable schema name '${schema.stableName}'")
            }
            if (schema.versions.map(RegistryVersion::version) != schema.versions.indices.toList()) {
                diagnostics +=
                    diagnostic(
                        1011,
                        "schema '${schema.stableName}' versions must be sequential from zero",
                    )
            }
            if (schema.versions.count { it.status == RegistryStatus.ACTIVE } > 1) {
                diagnostics +=
                    diagnostic(
                        1010,
                        "schema '${schema.stableName}' has more than one active version",
                    )
            }
            for (version in schema.versions) {
                if (version.version !in 0..15) {
                    diagnostics +=
                        diagnostic(1011, "schema '${schema.stableName}' has version outside 0..15")
                }
                if (!hashPattern.matches(version.descriptorSha256)) {
                    diagnostics +=
                        diagnostic(
                            1006,
                            "schema '${schema.stableName}' has invalid descriptor SHA-256",
                        )
                }
                val packetBytes = (16L + version.bodyBitSize + 7L) / 8L
                if (version.bodyBitSize < 0 || packetBytes > registry.maxPacketBytes) {
                    diagnostics +=
                        diagnostic(1207, "schema '${schema.stableName}' exceeds the packet limit")
                }
                if (
                    version.status != RegistryStatus.RETIRED &&
                        availableDescriptors.isNotEmpty() &&
                        (schema.id to version.version) !in availableDescriptors
                ) {
                    diagnostics +=
                        diagnostic(
                            1012,
                            "supported decoder source is missing for '${schema.stableName}' v${version.version}",
                        )
                }
            }
        }
    }

    private fun compareHistory(
        baseline: RegistryDocument,
        current: RegistryDocument,
        diagnostics: MutableList<RegistryDiagnostic>,
    ) {
        if (baseline.namespace != current.namespace) {
            diagnostics += diagnostic(1003, "protocol namespace changed")
        }
        val currentById = current.schemas.associateBy(RegistrySchema::id)
        for (oldSchema in baseline.schemas) {
            val newSchema = currentById[oldSchema.id]
            if (newSchema == null) {
                diagnostics +=
                    diagnostic(1007, "schema history removed for '${oldSchema.stableName}'")
                continue
            }
            if (oldSchema.stableName != newSchema.stableName) {
                diagnostics += diagnostic(1004, "schema ID ${oldSchema.id} was reused")
            }
            val newVersions = newSchema.versions.associateBy(RegistryVersion::version)
            for (oldVersion in oldSchema.versions) {
                val newVersion = newVersions[oldVersion.version]
                if (newVersion == null) {
                    diagnostics +=
                        diagnostic(
                            1007,
                            "version history removed for '${oldSchema.stableName}' v${oldVersion.version}",
                        )
                    continue
                }
                if (
                    oldVersion.descriptorSha256 != newVersion.descriptorSha256 ||
                        oldVersion.bodyBitSize != newVersion.bodyBitSize
                ) {
                    diagnostics +=
                        diagnostic(
                            1006,
                            "descriptor drift for '${oldSchema.stableName}' v${oldVersion.version}",
                        )
                }
                if (newVersion.status.ordinal < oldVersion.status.ordinal) {
                    diagnostics +=
                        diagnostic(
                            1010,
                            "lifecycle reversed for '${oldSchema.stableName}' v${oldVersion.version}",
                        )
                }
            }
        }
    }

    private fun diagnostic(number: Int, message: String): RegistryDiagnostic =
        RegistryDiagnostic("KOMPACT-KSP-$number", message)
}
