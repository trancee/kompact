# ADR-0002: Envelope identity and layout versions

Status: accepted

## Context

A decoder must identify a Kompact schema and its fixed layout before reading the body. Kotlin and C firmware need constant-time dispatch without field tags or a duplicated length value. Numeric identities must survive source renames and deletion, and deployed layouts must never be silently reinterpreted.

## Decision

Every top-level Kompact packet begins with a fixed 16-bit envelope governed by the LSB-first bitstream in ADR-0001:

- Bits 0 through 11 contain a schema ID from 1 through 4095.
- Bits 12 through 15 contain a layout version from 0 through 15.
- Schema ID zero is reserved as an escape for a future envelope format and is invalid for v1 schemas.

The two envelope bytes form `raw = byte0 | (byte1 << 8)`. The schema ID is `raw & 0x0FFF`, and the layout version is `raw >> 12`. Schema field offsets are body-relative, so body bit zero is packet bit 16.

Each enclosing BLE service or application protocol selects one Kompact protocol namespace out of band. Schema IDs are unique within that namespace. Combining namespaces on one channel requires an explicit gateway or a merged registry.

Each namespace owns a checked-in `kompact-registry.json`. The registry is an identity ledger, not a second editable schema definition. It records:

- registry format version and protocol namespace;
- schema ID and stable schema name;
- layout version and exact body bit size;
- support status;
- a lowercase SHA-256 fingerprint of a versioned canonical schema descriptor;
- permanent tombstones for retired IDs and versions.

Schema declarations own field definitions. Generation recomputes the canonical descriptor and fingerprint. A mismatch under an existing schema ID and layout version fails the build. Tooling never allocates identities implicitly and never reuses a retired numeric identity. The compatibility-tooling decision will fix the descriptor fields, JSON schema, normalization, comparison algorithm, and diagnostics.

Version zero is the first layout. Versions increase monotonically through 15. After version 15, a changed layout receives a new schema ID while the old ID history remains in the registry. Any wire or semantic change requires a new version, including a field addition or removal, offset, width, encoding, enum code, optionality, nested layout version, unit, range, or meaning. A Kotlin source rename may retain the version only when stable registry names and semantics remain unchanged.

A decoder supports only versions explicitly marked supported. It rejects reserved schema ID zero, unknown schema IDs, unsupported versions, incorrect packet lengths, and nonzero transport-tail bits before reading the body. Removing a supported decoder is a breaking public and protocol change. Retirement changes registry status but never deletes history.

The registry supplies the exact body bit size. The required packet length is `ceil((16 + bodyBitSize) / 8)` bytes. The envelope carries no length field. Truncated packets, extra bytes, and nonzero unused high bits after the final declared packet bit are invalid.

The Kompact envelope contains no checksum, authentication tag, sequence number, or replay counter. The enclosing BLE or application protocol owns outer framing, integrity, authentication, sequencing, and replay protection.

## Alternatives

An 8-bit envelope was rejected because either schema or version space becomes too small for long-lived protocol namespaces. An 8-bit schema ID plus 8-bit version was rejected because 256 versions per schema are less useful than a larger schema registry. A variable-length envelope was rejected because it makes body offsets and firmware dispatch variable. Hash-derived IDs and annotation-only identity were rejected because collisions, renames, and deleted declarations can change or reuse wire identities. A per-packet length was rejected because fixed schema versions already define exact size. Automatic migration was rejected because bit layouts do not contain the semantic conversion rules it requires.

## Risks

The fixed envelope spends two bytes on every packet. Four version bits limit one schema ID to 16 layouts, so long-lived schemas may need a new ID. Namespace selection is out of band; decoding under the wrong enclosing protocol can map the same numeric ID to a different schema, so callers must bind the correct registry before accepting packets. Strict length and tail-bit checks reject concatenated or extended data. SHA-256 detects descriptor drift but does not authenticate a registry or payload.

## Migration

No released envelope exists. Once v1 ships, the 16-bit envelope mapping and numeric identities are immutable. Layout changes create a new version or, after version 15, a new schema ID. Decoders retain explicitly supported old versions during staged application and firmware rollouts. A future envelope format begins with reserved schema ID zero and must define an explicit transition; v1 decoders fail closed when they encounter it.
