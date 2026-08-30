# ADR-0004: Schema authoring and generated Kotlin interface

Status: accepted

## Context

Kompact must let developers describe explicit wire layouts without writing bit arithmetic while keeping direct reads and valid writes allocation-free in the supported call shape. KSP can generate declarations but cannot add members to a user-authored value class. Generic, interface, and nullable value-class use can box, and a value class cannot carry both a `ByteArray` and a dynamic slice offset without another object.

## Decision

Developers author a declaration-only annotated interface. For example, `VehicleTelemetrySchema` carries `@KompactSchema`, abstract field properties, and explicit reserved-range annotations. It cannot be instantiated and contains no runtime behavior.

`@KompactSchema` repeats the stable registry name, schema ID, and layout version. KSP fails generation if any value disagrees with the selected protocol registry. Each property declares `@KompactField(bitOffset, bitWidth)`. The property's Kotlin type selects its scalar carrier and signedness; KSP verifies that the width and encoding fit that type. Fields and reserved ranges must satisfy ADR-0001 through ADR-0003.

KSP generates three declarations in the schema package with reserved, collision-checked names:

- A stateless facade such as `VehicleTelemetry`, containing stable schema identity and packet-size constants plus checked construction functions.
- A read-only `VehicleTelemetryView`.
- A mutable `VehicleTelemetryWriter`.

Generated visibility matches schema-interface visibility. Generated public signatures never expose the declaration interface. Generated files live only in the Gradle-owned build output.

The facade provides:

- `wrap(packet)`, which validates an existing exact-length packet and returns `KompactDecodeResult<VehicleTelemetryView>`.
- `initialize(packet)`, which requires the exact packet size, writes the envelope, clears the body, and returns `KompactDecodeResult<VehicleTelemetryWriter>`.
- `edit(packet)`, which validates an existing packet and returns `KompactDecodeResult<VehicleTelemetryWriter>`.

A generic factory result may allocate or box once. Callers extract a concrete view or writer before entering a measured hot path. Exact error variants remain owned by the validation decision.

The generated view and writer are common `@JvmInline value class` declarations. Each has an internal constructor and one internal `ByteArray` property. `@JvmInline` is required for the Android/JVM backend and is available as a common expected annotation. The backing array is not exposed publicly; the caller already owns the array it supplied.

Scalar fields are direct `val` properties that preserve the declared Kotlin carrier. Fixed byte sequences and arrays use direct indexed methods. Nested arrays flatten index parameters so no dynamic slice object is required. A static nested field may return another `ByteArray`-backed value-class view because its bit offset is compile-time constant.

Optional scalar fields expose `hasX: Boolean` and `xOr(defaultValue): T`. Writers expose `writeX(value)` and `clearX()`. A field write returns `KompactWriteError?`: null means success, and a typed error means validation rejected the operation without mutation. `writer.view()` reuses the same canonical packet without another validation pass.

Views and writers accept only an exact packet array beginning at byte zero. They are live, non-owning interpretations: successful writes and any external mutation are immediately observable. Callers provide exclusive mutation and synchronization while a view or writer is in use. Generated views and writers are not thread-safe snapshots.

Default value-class equality follows the backing `ByteArray` identity rather than packet contents. Generated `contentEquals` and `contentHashCode` methods provide explicit packet comparison. Generation does not add a field-dumping `toString`.

The generated public interface contains no reflection, platform APIs, generic hot-path helpers, nullable view values, or Swift/Objective-C bridge types. Allocation-free claims apply only to direct, concrete, non-null view and writer calls under the measurement contract recorded by the allocation research.

## Alternatives

An annotated data class was rejected because it creates a second allocated representation and invites copy-based decoding. A user-authored value-class shell was rejected because KSP cannot inject members, checked constructors, or property bodies, leaving manual arithmetic and bypassable validation. A single mutable view was rejected because all readers would receive mutation capability. Throwing factories were rejected because malformed BLE input is an expected typed failure. Nullable optional properties and generic write results were rejected because they can box or add hot-path wrappers. Allocated slice views and copied arrays were rejected for v1 aggregate access.

## Risks

Generic checked-construction results allocate or box outside the scalar hot path. Requiring exact packet arrays prevents a value-class view over a slice of a larger receive buffer. Flattened indexed methods can expand generated names and code for deeply nested arrays. A caller can mutate the shared array after checked wrapping and violate previously validated invariants. Reference equality may surprise callers who expect structural packet equality. Compiler lowering can still introduce boxing when callers erase, generalize, or null the generated type, so benchmarks must retain positive boxing controls.

## Migration

No generated Kotlin interface has been released. After release, changing generated names, visibility, factory results, property carriers, optional access, write results, equality meaning, or buffer ownership is a public compatibility change. Wire-compatible source renames preserve registry identity but require generated API migration and compatibility review. Future slice or Swift adapters must be separate interfaces and cannot weaken the direct value-class contract.
