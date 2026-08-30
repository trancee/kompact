# ADR-0006: Validation, diagnostics, and mutation

Status: accepted

## Context

Kompact accepts untrusted BLE bytes and generates public Kotlin and C interfaces from schema declarations. Invalid schemas must not leave partial generated artifacts, malformed packets must fail identically across platforms, diagnostics must not leak payload data, and rejected writes must not partially mutate caller-owned buffers.

## Decision

### Runtime status codes

Kotlin and C share this one-byte public status table:

| Value | Name |
| --- | --- |
| `0x00` | `OK` |
| `0x01` | `NULL_ARGUMENT` |
| `0x02` | `INVALID_PACKET_LENGTH` |
| `0x03` | `RESERVED_SCHEMA_ID` |
| `0x04` | `UNKNOWN_SCHEMA_ID` |
| `0x05` | `UNSUPPORTED_LAYOUT_VERSION` |
| `0x06` | `NONZERO_TAIL_BITS` |
| `0x07` | `UNKNOWN_ENUM_CODE` |
| `0x08` | `NONZERO_RESERVED_BITS` |
| `0x09` | `NONZERO_ABSENT_OPTIONAL` |
| `0x0A` | `VALUE_OUT_OF_RANGE` |
| `0x0B` | `INDEX_OUT_OF_RANGE` |
| `0x0C` | `INTERNAL_INVARIANT_FAILURE` |

Values `0x0D` through `0xFF` are reserved. Released values are never reinterpreted or reused.

Kotlin exposes sealed `KompactDecodeError` and `KompactWriteError` hierarchies. Every variant carries the shared status code. Failure objects may contain only redacted metadata: schema ID and version, stable field path or field ID, bit offset, expected and actual lengths, and array index where relevant. They never contain packet bytes, decoded values, attempted write values, secrets, or PII.

C exposes matching exact-width `kompact_status_t` constants. A non-`OK` schema function leaves packet bits and caller output storage unchanged. Kotlin's type system excludes null packet references; C uses `NULL_ARGUMENT` for required null pointers.

The library does not log runtime failures. Callers decide whether to log the stable code and redacted metadata.

Kotlin indexed reads check the index and throw `IndexOutOfBoundsException` before packet access, matching Kotlin array behavior without adding a result wrapper to the hot path. Kotlin writes and all C indexed operations report `INDEX_OUT_OF_RANGE` without mutation.

### Runtime validation ownership and order

Registry dispatch and schema-specific factories return one deterministic first failure. They validate in this order:

1. Required C pointers.
2. The minimum two-byte envelope length.
3. Reserved or unknown schema ID, followed by unsupported layout version.
4. Exact packet length for the selected schema version.
5. Final transport-tail bits.
6. Body fields in increasing bit-offset order, with nested values depth-first and arrays in ascending index order.

A schema-specific factory follows the same order while comparing the packet envelope with its expected ID and version. Checked wrapping validates the complete body. Direct View getters repeat no pointer, length, envelope, or semantic validation. Writer operations validate their fallible value and index inputs because a successfully created Writer already establishes packet invariants.

External mutation after wrapping is an undetectable contract violation. Callers provide exclusive mutation and synchronization; Kompact does not hash, lock, copy, or revalidate the packet on direct access.

### Mutation

A generated operation validates every fallible condition and every C output pointer before its first packet store. After mutation begins, it executes only non-failing stores. Output handles are assigned last.

Rejected initialization, edits, indexed operations, and field writes leave the packet and caller output storage byte-for-byte unchanged. Rollback is unnecessary because no failure can occur after the first store. Successful multi-byte writes are not atomic against concurrent readers or writers and require caller-provided exclusive access.

### KSP validation and diagnostics

The processor builds and validates the complete canonical descriptor before emitting Kotlin or C output. Any error suppresses all generated output for that task. It collects independent errors across schemas, suppresses dependent cascades, and sorts diagnostics by repository-relative path, line, column, diagnostic code, and stable field path.

All v1 schema, registry, portability, and generation invariant violations are errors. Accepted schemas are silent. Advisory warnings are not emitted. Deprecation warnings may be introduced only with a future explicit deprecation system.

Stable public KSP diagnostic assignments are:

| Code | Name |
| --- | --- |
| `KOMPACT-KSP-1001` | `INVALID_SCHEMA_DECLARATION` |
| `KOMPACT-KSP-1002` | `REGISTRY_NOT_FOUND` |
| `KOMPACT-KSP-1003` | `REGISTRY_IDENTITY_MISMATCH` |
| `KOMPACT-KSP-1004` | `TOMBSTONED_IDENTITY_REUSE` |
| `KOMPACT-KSP-1005` | `DUPLICATE_SCHEMA_ID_VERSION` |
| `KOMPACT-KSP-1006` | `DESCRIPTOR_FINGERPRINT_MISMATCH` |
| `KOMPACT-KSP-1007` | `REGISTRY_HISTORY_REMOVED` |
| `KOMPACT-KSP-1008` | `UNSUPPORTED_REGISTRY_FORMAT` |
| `KOMPACT-KSP-1009` | `COMPATIBILITY_BASELINE_REQUIRED` |
| `KOMPACT-KSP-1010` | `ILLEGAL_LIFECYCLE_TRANSITION` |
| `KOMPACT-KSP-1011` | `NONSEQUENTIAL_LAYOUT_VERSION` |
| `KOMPACT-KSP-1012` | `SUPPORTED_DECODER_MISSING` |
| `KOMPACT-KSP-1101` | `UNSUPPORTED_FIELD_TYPE` |
| `KOMPACT-KSP-1102` | `INVALID_BIT_OFFSET` |
| `KOMPACT-KSP-1103` | `INVALID_BIT_WIDTH` |
| `KOMPACT-KSP-1104` | `FIELD_TYPE_WIDTH_MISMATCH` |
| `KOMPACT-KSP-1105` | `FIELD_OVERLAP` |
| `KOMPACT-KSP-1106` | `IMPLICIT_LAYOUT_GAP` |
| `KOMPACT-KSP-1107` | `RESERVED_RANGE_CONFLICT` |
| `KOMPACT-KSP-1108` | `DUPLICATE_ENUM_CODE` |
| `KOMPACT-KSP-1109` | `ENUM_CODE_OUT_OF_RANGE` |
| `KOMPACT-KSP-1201` | `INVALID_ARRAY_COUNT` |
| `KOMPACT-KSP-1202` | `NESTED_OPTIONAL` |
| `KOMPACT-KSP-1203` | `UNKNOWN_NESTED_SCHEMA` |
| `KOMPACT-KSP-1204` | `UNSUPPORTED_NESTED_VERSION` |
| `KOMPACT-KSP-1205` | `SCHEMA_NESTING_CYCLE` |
| `KOMPACT-KSP-1206` | `SIZE_ARITHMETIC_OVERFLOW` |
| `KOMPACT-KSP-1207` | `PACKET_SIZE_LIMIT_EXCEEDED` |
| `KOMPACT-KSP-1301` | `GENERATED_KOTLIN_NAME_COLLISION` |
| `KOMPACT-KSP-1302` | `GENERATED_C_SYMBOL_COLLISION` |
| `KOMPACT-KSP-1303` | `GENERATED_OUTPUT_PATH_COLLISION` |
| `KOMPACT-KSP-1304` | `GENERATED_VISIBILITY_CONFLICT` |

Each diagnostic exposes its code, `ERROR` severity, source symbol and location, stable schema and field metadata, offending schema metadata, and expected constraint. Code, severity, and structured payload shape are compatibility contracts. Human-readable prose may improve without changing the code. Expected validation failures use `KSPLogger.error(message, symbol)` rather than processor exceptions.

Unassigned numbers within each family remain reserved: `1001..1099` for declarations and registry identity, `1101..1199` for fields and scalar layout, `1201..1299` for aggregates and size, and `1301..1399` for generated output and visibility. Assigned numbers are never reused.

## Alternatives

Failing on the first schema error was rejected because it forces one fix per build and makes traversal order visible. Emitting only valid schemas was rejected because it can package partial Kotlin, C, and registry artifacts. Runtime lists of all failures were rejected because they allocate, scan beyond the first invalid structure, and diverge from C's fixed status interface. Platform-specific status tables and string-only failures were rejected because cross-language conformance could not compare them. Rollback after partial writes was rejected because all fallible checks can run before mutation. Hashing or copying live buffers was rejected because it defeats direct zero-copy access without solving concurrent races.

## Risks

Returning one runtime failure hides later problems until the first is corrected. Public numeric codes and diagnostic payloads constrain future changes. Rich Kotlin failure objects allocate on failure paths. `INTERNAL_INVARIANT_FAILURE` cannot explain implementation details without risking sensitive diagnostics. C callers can forge trusted handles, and external aliases can invalidate a packet after checked wrapping. Kotlin and C differ for invalid read indices, so conformance tests must assert the documented exception-versus-status distinction.

## Migration

No error contract has been released. After release, removing or renumbering a status or KSP diagnostic, changing severity, or incompatibly changing structured metadata is a public compatibility break. New codes use previously unassigned values and require corresponding Kotlin, C, documentation, and conformance-vector updates. Implementations must migrate to validate-then-emit and validate-before-mutate before any generated artifact is published.
