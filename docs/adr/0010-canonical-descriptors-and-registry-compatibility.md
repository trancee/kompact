# ADR-0010: Canonical descriptors and registry compatibility

Status: accepted

## Context

Kotlin and C generation need one schema representation whose fingerprint changes for every wire or semantic change but remains stable across source-only Kotlin renames and generator upgrades. The checked-in registry must preserve identity history, while compatibility checks need an external historical baseline because a current file cannot prove that its own tombstones were deleted. Descriptor and registry bytes must remain deterministic across machines and relocated builds.

## Decision

### Canonical descriptor

Each schema version has one JSON descriptor with:

- `format` equal to `kompact-schema`;
- `formatVersion` equal to `1`;
- protocol namespace;
- stable schema name, schema ID, layout version, and body bit size;
- fields;
- reserved ranges.

Each field contains a stable name, bit offset, bit width, recursively tagged logical type, and structured semantics. Reserved ranges contain stable name, bit offset, and bit width.

The logical type is a closed tagged union covering Boolean, signed integer, unsigned integer, enum, IEEE binary32, IEEE binary64, fixed bytes, fixed array, optional, and same-namespace nested schema/version. Arrays contain positive fixed counts and a nested element type. Nested descriptors identify stable schema name, schema ID, and exact layout version. Cross-namespace nesting is invalid.

Structured field semantics include:

- required stable `semanticType`;
- optional case-sensitive unit;
- optional exact rational scale and offset, reduced to coprime numerator and positive denominator;
- optional numeric minimum and maximum;
- stable enum entry names and explicit codes;
- optionality, fixed counts, and nested identity/version as part of the logical type.

Descriptions, comments, Kotlin identifiers, Kotlin carrier types, lifecycle status, generator version, and generated symbol names are excluded. Kotlin ABI and generated-artifact compatibility checks own those concerns.

Optional JSON properties are omitted rather than encoded as null. Signed and unsigned 64-bit values, rational numerators and denominators, and numeric domain boundaries use canonical decimal strings matching `-?(0|[1-9][0-9]*)`. They contain no leading plus sign or redundant leading zero. Floating bit patterns use fixed-width lowercase hexadecimal strings. Duplicate JSON object keys are invalid.

Protocol namespace, schema, field, reserved-range, enum-entry, and semantic-type names match `[a-z][a-z0-9_]*`. They are independent of Kotlin identifiers. A Kotlin-only rename keeps the stable names and descriptor fingerprint; Kotlin ABI checks report its generated interface impact separately.

Before canonical serialization:

- fields and reserved ranges sort by bit offset, then stable name;
- enum entries sort by numeric code, then stable name;
- schemas sort by schema ID;
- versions sort ascending;
- duplicate semantic sort keys fail validation.

The descriptor is serialized as UTF-8 using the JSON Canonicalization Scheme in RFC 8785. SHA-256 over those exact bytes is stored as a lowercase, 64-character `descriptorSha256`. Descriptor format version participates in the hash; generator version does not. SHA-256 detects drift and is not an authentication mechanism.

KSP builds and validates one immutable descriptor model. That same instance feeds Kotlin generation, C generation, canonical serialization, hashing, and reports. Backends do not derive separate models or reparse emitted JSON. Tests independently parse emitted JSON and require an equal model and identical canonical bytes.

### Registry

The checked-in `kompact-registry.json` uses two-space-indented UTF-8 JSON, LF endings, a terminal newline, schemas sorted by ID, and versions ascending. Its top level contains:

- `$schema` pointing to the versioned registry JSON Schema;
- `formatVersion` equal to `1`;
- protocol namespace;
- maximum packet byte size;
- schema entries.

Each schema entry contains stable name, schema ID, optional `supersedes` stable schema identity, and versions. Each version contains layout version, lifecycle status, body bit size, and descriptor SHA-256.

Lifecycle status is one of:

- `active`: source and descriptor are present; generation emits encoder and decoder.
- `decode-only`: retained source and descriptor are present; generation emits only a decoder.
- `retired`: source and generated code may be removed, but registry entry, fingerprint, conformance vectors, and compatibility fixtures remain permanently.

A version moves only `active` to `decode-only` to `retired`. Retired is terminal. At most one active version exists for a schema ID. New versions use exactly the next numeric value. After version 15, evolution uses a new schema ID, layout version zero, and a new stable schema name such as `vehicle_telemetry_gen2`; the new entry may identify the previous stable schema through `supersedes`. The previous ID and name remain in history. Entries, versions, tombstones, and assigned numeric identities are never deleted or reused.

### Reviewed registry updates

Developers explicitly add stable names, schema IDs, layout versions, and lifecycle states. Generation writes a complete proposed registry to `reports/kompact-registry.proposed.json` under the plugin-owned output root. `checkKompactSchemas` compares it with the checked-in registry, prints a deterministic structured diff, and fails until the source registry matches the reviewed proposal.

No generation or check task mutates source files and no tool allocates an ID, version, stable name, or lifecycle transition implicitly.

Generated Kotlin, C headers, descriptors, conformance manifests, reports, and registry entries expose the same descriptor SHA-256. A mismatch is a build failure.

### Baseline comparison

The `kompact` extension adds optional `compatibilityBaseline` and Boolean `requireCompatibilityBaseline` properties. Local checks may omit a baseline and then prove current schema and registry internal consistency only. CI and release set required mode; a missing baseline fails.

Pull-request CI supplies the merge-base registry as a local input file. Release CI supplies the previous published registry artifact. Compatibility tasks perform no Git operation, network request, or credential lookup.

Comparison rejects:

- namespace change;
- registry history, tombstone, schema, or version removal;
- schema ID, stable name, or retired identity reuse;
- descriptor fingerprint or body-size drift under an existing ID/version;
- a new version that is not exactly the next value;
- version rollover without a new ID and new stable name;
- lifecycle reversal or more than one active version per schema ID;
- missing source/descriptor for active or decode-only status;
- missing encoder for active status or missing decoder for active/decode-only status;
- removal of a previously supported decoder without a legal lifecycle transition;
- a lower packet limit that excludes an active or decode-only version.

A new fingerprint is accepted only under the next legal version or a new legal schema ID and stable name. Raising the packet limit is compatible. Lowering it is compatible only when every active and decode-only version still fits.

### Diagnostics and schemas

ADR-0006 gains these stable identity-family diagnostics:

| Code | Name |
| --- | --- |
| `KOMPACT-KSP-1007` | `REGISTRY_HISTORY_REMOVED` |
| `KOMPACT-KSP-1008` | `UNSUPPORTED_REGISTRY_FORMAT` |
| `KOMPACT-KSP-1009` | `COMPATIBILITY_BASELINE_REQUIRED` |
| `KOMPACT-KSP-1010` | `ILLEGAL_LIFECYCLE_TRANSITION` |
| `KOMPACT-KSP-1011` | `NONSEQUENTIAL_LAYOUT_VERSION` |
| `KOMPACT-KSP-1012` | `SUPPORTED_DECODER_MISSING` |

The Gradle plugin publishes versioned JSON Schemas for descriptor, registry, and conformance-manifest validation with its documentation artifacts. Unknown registry or descriptor format versions fail closed. JSON Schema validation runs before canonicalization and semantic validation.

### Required gates

Tests cover RFC 8785 and SHA-256 known-answer vectors, every descriptor type, rational and numeric-string normalization, duplicate keys, semantic ordering, stable-name validation, same-namespace nesting, and Kotlin-only renames.

Compatibility fixtures cover every legal and illegal lifecycle transition, new version, version rollover, new schema ID, history removal, identity reuse, fingerprint drift, semantic and wire mutation, source removal, decoder removal, packet-limit change, missing and malformed baseline, unsupported format, and proposed-registry diff.

Integration tests require Kotlin, C, descriptor, registry, manifest, report, and header fingerprints to agree. Repeated, parallel, clean, incremental, and relocated-cache builds produce identical descriptor bytes, fingerprints, proposals, and diagnostics.

## Alternatives

Custom canonical JSON and a binary descriptor were rejected because Kompact would own another normalization format and make review harder. Source declaration order was rejected because harmless reordering would change fingerprints. Kotlin identifiers were rejected as stable identity because source-only renames should not change wire meaning. Hashing descriptions or generator versions was rejected because typo fixes and tool upgrades are not layout versions. Bit-only fingerprints were rejected because unit, range, scale, enum meaning, and nested semantic changes can break consumers without moving bits. Current-file-only checks were rejected because deleted history becomes invisible. Git, Maven, or network lookup inside the task was rejected because compatibility must remain offline and reproducible. Automatic registry mutation was rejected because it can approve identity and lifecycle changes without review.

## Risks

RFC 8785 and JSON Schema implementations become build-tool dependencies and require retained known-answer tests. Structured semantics increase annotation verbosity and still cannot encode every domain meaning. Excluding Kotlin carriers from the fingerprint means ABI checks are required to catch carrier changes. Human review can approve an incorrect proposed registry. Baseline provisioning adds CI plumbing. Terminal retirement prevents reactivating an old decoder under the same lifecycle record. SHA-256 detects accidental drift but cannot establish registry provenance or payload integrity.

## Migration

No descriptor or registry format has been released. Implementation must add versioned JSON Schemas, canonical model and serializer, RFC 8785 and SHA-256 tests, stable-name annotations, proposed-registry output, offline baseline inputs, compatibility comparison, and diagnostics `1007` through `1012` before publishing schemas. Later descriptor or registry format changes require a new format version and migration tooling; they cannot rewrite existing descriptor fingerprints or registry history.
