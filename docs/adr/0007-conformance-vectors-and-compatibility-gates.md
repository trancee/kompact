# ADR-0007: Conformance vectors and compatibility gates

Status: accepted

## Context

Kompact will generate Kotlin and C implementations of one bit-level protocol. Tests derived from either production encoder can reproduce the same defect in expected bytes. Releases need independent, reviewable packet vectors and target-specific execution proving identical behavior on Android/JVM, Kotlin/Native iOS, GCC, Clang, and a big-endian C target. Wire compatibility and generated public interfaces also need retained machine-readable baselines.

## Decision

### Authoritative vector corpus

Each released schema version has one append-only, synthetic, secret-free JSON manifest at:

```text
conformance/<namespace>/<stable-name>/v<version>.json
```

A manifest records its format version, protocol namespace, stable schema name, schema ID, layout version, canonical descriptor SHA-256, packet byte size, and named test cases.

Packet bytes use lowercase, even-length hexadecimal without separators. Signed and unsigned integers use decimal strings so JSON number precision cannot change a value. Floating values record their exact raw hexadecimal bits plus a semantic label. Byte sequences use hexadecimal. Enum values record stable code and name. Arrays, optionals, and nested values follow the schema structure.

Every case records logical values, exact packet bytes, expected shared status code from ADR-0006, and only the permitted redacted error metadata. Expected bytes are reviewed protocol data. Production Kotlin and C encoders never generate or update expected bytes.

Released cases and expected results are immutable and are never removed. New cases may append. Retired schema versions retain their manifests for as long as any decoder remains supported.

### Required valid cases

Every supported schema version covers:

- canonical all-zero values where the schema permits them;
- minimum and maximum values for every scalar width;
- negative signed values and exact sign-extension boundaries;
- aligned and unaligned offsets crossing every relevant byte boundary;
- every declared enum code and representative gaps around declared codes;
- finite floats, positive and negative zero, infinities, canonical written NaNs, and accepted noncanonical NaN reads;
- each optional field present and absent;
- first and last elements of every fixed array;
- representative nested values at every nesting level;
- every possible final transport-tail-bit count exercised by the canonical schema suite.

### Required invalid and mutation cases

Malformed cases change one invariant at a time and preserve every earlier validation stage so ADR-0006 precedence is testable. They cover:

- every packet length shorter than expected, including zero and one byte;
- at least one extra byte;
- reserved schema ID zero, an unknown schema ID, and every unsupported version boundary;
- every transport-tail and reserved bit set independently;
- every undeclared enum code that fits the field when the code space is tractable, otherwise every gap boundary plus fixed-seed property coverage;
- nonzero value-slot bits under each absent optional;
- first invalid index below and above every array range where the language can express it;
- values immediately outside each writable scalar range;
- nested failures with their expected stable field path, bit offset, and array index.

Rejected write and initialization cases snapshot the packet and caller output storage before the operation and require byte-for-byte equality afterward.

### Cross-language execution

Every execution target reads all relevant manifests, decodes packet hex, compares logical values, encodes valid logical values, and compares the resulting packet byte-for-byte with the reviewed hex.

A standalone C harness also reads packet files emitted by Kotlin, and a Kotlin integration harness reads packet files emitted by C. Both compare those files with the reviewed manifest so neither implementation becomes the expected-byte authority.

Normal merge gates execute:

- common correctness tests on JVM;
- Android instrumented conformance on a pinned emulator image;
- `iosSimulatorArm64` conformance on a pinned macOS and Xcode image;
- `iosArm64` compile and link on macOS;
- strict C99 consumer compilation and vector execution under GCC and Clang;
- C vector execution using a pinned big-endian QEMU target;
- ASan and UBSan C execution where the selected compiler and target support them.

Physical Android and iPhone conformance smoke runs are release gates and share the dedicated performance-device jobs. Each selected production firmware compiler becomes a release gate when a firmware toolchain is adopted.

C builds use warnings as errors with strict C99 and conversion diagnostics. Generated headers must compile in more than one translation unit to expose linkage mistakes.

### Determinism and generated artifacts

A compact canonical schema suite checks in reviewable generated Kotlin, C header, canonical descriptor, registry, and diagnostic snapshots. Consumer-generated files outside this suite remain build outputs and are not checked in.

All schema generation must produce byte-identical outputs and hashes across repeated, parallel, clean, and relocated builds. The test matrix covers schema addition, change, rename, removal, tombstone retention, and stale-output cleanup.

Fixed-seed property tests supplement reviewed vectors on every merge. They cover round trips, offset and width combinations, canonicalization, deterministic failure precedence, and rejected-operation immutability. Longer randomized Kotlin runs and sanitizer-backed C fuzzing run on a schedule. Randomized evidence never replaces reviewed vectors.

### Compatibility gates

Kotlin public ABI tracking covers the runtime, annotations, Gradle plugin, and canonical generated interfaces. Compatibility checks compare protocol registries and canonical descriptors for ID or version reuse, fingerprint drift, tombstone deletion, semantic change without a new version, and removal of a supported decoder.

Retained old and new C consumer fixtures compile against current versioned schema and runtime headers. Header-only C has a source-compatibility contract rather than a linked binary ABI.

A released vector change requires a new layout version. An incompatible Kotlin or C public-interface change requires the documented SemVer impact, migration instructions, updated compatibility artifacts, and retained old-version proof where support continues.

### Evidence retention

CI retains manifests, canonical snapshots, hashes, test reports, failing case names, C compiler commands, sanitizer and fuzzer artifacts, emulator and runtime versions, compiler options, and target build metadata. Failures identify synthetic cases and ADR-0006 redacted metadata only; they never include packet values from production traffic.

A candidate may claim Android, iOS, and C conformance only when all required target gates pass on that exact commit. A skipped, unavailable, or host-incompatible target remains explicitly unverified.

## Alternatives

Generated binary fixtures were rejected because reviewers cannot inspect field meaning and expected bytes easily. Kotlin-authored expectations were rejected because they privilege one production implementation. Decode-only vectors were rejected because writer divergence remains hidden. Random or fuzz input as primary proof was rejected because release evidence and failure precedence become nondeterministic. Snapshotting every generated consumer file was rejected because it duplicates build output and creates noisy reviews. JVM-only execution and host-GCC-only C tests were rejected because they do not exercise ART, Kotlin/Native, Clang, or endian assumptions. Physical devices on every merge were rejected because the default suite must remain deterministic and independent of retained hardware.

## Risks

The required matrix has meaningful CI cost and needs macOS, Android emulator, and big-endian emulation capacity. Human-reviewed expected bytes can still contain mistakes, so bidirectional implementations and property tests remain necessary. Strict append-only vectors and compatibility baselines increase repository size. Emulators do not prove physical performance or every device behavior. Sanitizer and fuzz results vary by toolchain and need pinned environments. Supporting old versions increases generated code size.

## Migration

No conformance corpus has been released. Before the first runtime release, implementation must add the manifest schema, canonical fixtures, target harnesses, ABI baselines, registry comparison, deterministic generation checks, and CI jobs described here. Later vector corrections that change released expected bytes create a new layout version; old manifests remain intact. New target or firmware compiler support adds gates without weakening existing ones.
