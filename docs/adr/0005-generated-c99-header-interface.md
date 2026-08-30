# ADR-0005: Generated C99 header interface

Status: accepted

## Context

C firmware must consume the same Kompact schema as Kotlin without implementation-defined C bitfields, packed structs, unaligned loads, host-endian casts, duplicated validation, or heap allocation. Firmware projects also need deterministic generated artifacts that require no additional object file or link step.

## Decision

Kompact generates one versioned `kompact_runtime.h` and one `<registry_name>_v<version>.h` for every schema version. Schema headers are C99 header-only, include the runtime header, and include `<stdbool.h>`, `<stddef.h>`, `<stdint.h>`, `<float.h>`, and `<string.h>` when their declarations require them.

`kompact_runtime.h` defines `KOMPACT_RUNTIME_INTERFACE_VERSION`. Each schema header checks the supported runtime interface version at preprocessing time and exposes generator-version and canonical-descriptor SHA-256 macros. Standard include guards, public symbols, typedefs, and constant names contain a sanitized stable registry name and layout version. Generation fails if sanitization creates a collision.

Public schema and status domains use exact-width integer typedefs plus named `UINT*_C` constant macros. The generated public interface does not use native C enum types because their size and signedness are implementation-defined. Exact status meanings and numeric assignments remain owned by the validation decision.

A generated View is a struct containing one `const uint8_t *`. A Writer is a struct containing one `uint8_t *`. Handles own no memory. The schema header provides static inline functions equivalent to the Kotlin facade:

- `wrap` validates an existing exact-length packet and assigns an output View only on success.
- `initialize` requires exact packet size, writes the envelope, clears the body, and assigns an output Writer only on success.
- `edit` validates an existing packet and assigns an output Writer only on success.
- Writer-to-View conversion reuses the packet pointer without another validation pass.

Direct scalar getters accept a successfully created View and return the declared exact-width C carrier. They repeat no pointer, length, envelope, or semantic checks. C cannot prevent a caller from forging a handle, so forged or manually modified handles are outside the supported contract.

Field writes return `kompact_status_t`. They validate every fallible precondition before mutation and preserve unrelated bits. Fixed-byte and array reads validate the index, return status, and assign caller output storage only on success. Array writes validate both index and value before mutation. Optional fields generate `has_`, `_or(default_value)`, `write_`, and `clear_` functions.

Nested and nested-array fields generate parent-prefixed flattened accessors with every required index parameter. They do not create dynamic slice handles, so View and Writer remain pointer-only.

Schema headers expose constant macros for schema ID, layout version, body bit count, packet byte count, field offsets and widths, fixed counts, enum codes, and numeric bounds. They do not generate function-like field macros.

`kompact_runtime.h` exposes reserved `kompact_internal_*` static inline bit helpers with documented preconditions. Generated checked schema functions are the supported public entry points. Runtime helpers load `uint8_t`, widen before shifting, and use unsigned operations. They never cast packet storage to wider pointers, perform unaligned loads, depend on host byte order, or right-shift signed values.

Float helpers require exact-width integers, radix-2 binary32 and binary64 characteristics, and four-byte `float` and eight-byte `double` storage through C99-compatible compile-time checks. Integer bit patterns move to and from floating carriers with `memcpy`, never pointer punning.

A failed factory, validation, indexed read, or write leaves packet bits and all caller output storage unchanged. Successful multi-byte writes are not atomic against concurrent access; firmware provides exclusive mutation and synchronization. Generated headers allocate no heap memory.

## Alternatives

A generated header plus `.c` implementation was rejected because it adds object compilation, linking, public ABI symbols, and small-call overhead unless link-time optimization removes it. Constants and expression macros alone were rejected because each firmware caller would recreate envelope checks, cross-byte operations, and write failure behavior; function-like macros also risk repeated argument evaluation. Packed structs and native C bitfields were rejected because their layout is implementation-defined. Checking every direct getter was rejected because a validated View already establishes packet invariants. Dynamic nested slice handles were rejected to keep the C and Kotlin aggregate interfaces aligned.

## Risks

Static inline schema functions can duplicate machine code across translation units. Pointer-only handles cannot enforce checked construction or retain packet length. Flattened nested accessors can create long symbol names and increase generated code size. Requiring IEEE binary32 and binary64 excludes unusual C99 targets at compile time. Visible `kompact_internal_*` helpers can be called despite being unsupported. Successful writes can be observed partially without external synchronization.

## Migration

No C header interface has been released. After release, changing public names, typedef widths, function signatures, status values, handle layout, runtime helper preconditions, or `KOMPACT_RUNTIME_INTERFACE_VERSION` is a compatibility change. Schema versions remain simultaneously includable because their symbols contain layout versions. Generator and descriptor fingerprints accompany released headers so build tooling can reject stale Kotlin, runtime, or firmware artifacts.
