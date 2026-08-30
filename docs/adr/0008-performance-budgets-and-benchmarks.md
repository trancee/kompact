# ADR-0008: Performance budgets and benchmark matrix

Status: accepted

## Context

Kompact's payload-size, allocation, and latency claims need numeric thresholds tied to representative BLE workloads and controlled target environments. Universal nanosecond limits would describe one processor rather than the library, while measurements without thresholds cannot block regressions. The selected Kotlin interface also permits bounded allocation during checked construction but requires allocation-free direct access afterward.

## Decision

### Retained workloads

The benchmark suite retains three exact, reviewed canonical schema descriptors and deterministic value corpora:

- Small: the four-byte VehicleTelemetry packet with a 4-bit enum, cross-byte 10-bit integer, Boolean, and reserved bit.
- Medium: a fixed 32-byte packet covering every scalar carrier, aligned and unaligned fields, float32 and float64, enum gaps, optional presence and absence, fixed bytes, arrays, and one nested schema.
- Large: a fixed 244-byte packet dominated by nested arrays, unaligned byte sequences, optionals, and whole-body validation.

These sizes are benchmark workloads, not global protocol limits. Exact descriptors and values are checked in and may change only through baseline-change review.

### Operation boundaries

Separate benchmarks measure:

- low-level aligned and unaligned bit reads and writes;
- generated direct scalar reads;
- fixed-byte and array indexed reads;
- optional `hasX` and `xOr(defaultValue)` access;
- valid generated scalar and indexed writes;
- Writer-to-View conversion;
- complete valid `wrap`, `edit`, and `initialize` operations;
- complete packet read and write throughput.

Packet allocation, initialization, expected-value construction, and checksum validation remain outside the timed operation. Each measured operation contributes to a primitive checksum so the optimizer cannot remove it. Inputs cycle deterministically through minimum, maximum, signed, float-special, aligned, unaligned, first, middle, last-index, optional, and nested cases.

Each generated benchmark has a reviewed hand-written reference with the same operation boundary, validation behavior, carrier type, compiler options, and input sequence. The reference uses direct bit code and no generated call path.

### Numeric latency budgets

On each reference runtime and device:

- Generated direct scalar and indexed reads, optional access, valid writes, and Writer-to-View conversion have median time no greater than `1.10` times the equivalent hand-written reference.
- Valid `wrap`, `edit`, and `initialize` have median time no greater than `1.25` times equivalent hand-written validation.
- Any candidate operation more than `1.10` times its previous committed generated baseline blocks merge, even when its hand-written-reference ratio still passes.

### Allocation budgets

Direct scalar and indexed reads, optional access, valid writes, and Writer-to-View conversion allocate exactly zero managed objects and zero managed bytes per operation on HotSpot and ART. On iOS, the same operations produce no differential allocation and no relevant allocation stack in the measured Instruments interval.

Each measurement artifact contains generic, interface, nullable, and intentional-allocation positive controls. A zero-allocation result is invalid if the same run does not detect its controls.

Successful `wrap`, `edit`, and `initialize` allocate at most two managed objects. Failure allocations are reported but not part of the hot-path ceiling. Generated C headers and operations perform no heap allocation.

### Code-size budgets

The release runtime contributes at most 16 KiB of code and read-only constant data per target after subtracting an empty harness. Measurement uses the target's stable representation:

- JVM classfile method bytecode and constant data for JVM publication;
- DEX code and constant data for Android publication;
- linked text and read-only data for Kotlin/Native and C artifacts.

Code attributable to each canonical generated schema is no greater than `1.25` times its reviewed hand-written equivalent and no greater than `1.10` times its previous committed baseline. Each generated C schema/version header is at most 64 KiB in source bytes.

### Encoded-size budget

A packet contains exactly the 16-bit envelope plus declared field and reserved bits. Byte transport adds only the ceiling to the next byte, whose unused tail bits are zero. Packets contain no hidden tags, offsets, lengths, alignment, or generator metadata.

### Reference environments

Repository metadata pins one dedicated physical Android device and one dedicated iPhone as blocking reference environments. It records device model, CPU, memory, OS build, power source and battery state, thermal state, clock-lock or sustained-performance state where available, toolchain versions, compiler options, GC and allocator options, and benchmark-harness version. Additional devices report nonblocking results.

Controlled HotSpot/JMH measurements are diagnostic and blocking for the JVM publication. AndroidX Microbenchmark measures ART on the physical Android device. Release `iosArm64` loops measure time on the iPhone without Instruments; a separate run captures allocations with Instruments. Simulator, emulator, timing, and allocation runs are never compared as interchangeable environments.

### Statistical gate

A budget session runs seven randomized or alternating baseline/candidate process pairs and gates on the median paired ratio. A ratio above its ceiling triggers one complete repeat in reversed order. Two failing sessions block the change.

Baseline and candidate use the same worker, device, power and thermal state, toolchain, build flags, workload, units, and profiler-attachment state. Thermal throttling, environment drift, missing positive controls, invalid checksums, tool failure, or incomparable metadata makes the session unverified.

### CI and release enforcement

Shared CI runs short discovery, execution, parameter, checksum, and report-generation smoke profiles. Any change to a budgeted runtime, generated interface, generator, compiler option, dependency, or canonical workload requires successful controlled JVM, Android, and iPhone jobs on the candidate commit before merge. Scheduled unchanged-baseline sessions detect worker drift.

Physical benchmark jobs build artifacts before the timed session and retain raw per-run data. Timing and allocation profiling run separately. Performance results from shared hosted runners are informational only.

Reference implementations, raw baseline data, workload descriptors, and environment identities are versioned. A baseline or reference change requires a separate pull request with rationale, previous and replacement raw evidence, environment identity, and approval. A feature change cannot reset its own baseline.

### Evidence retention

Each retained result includes repository commit, generated-source hash, raw Android and JMH-compatible JSON, Instruments trace or export, linker maps or binary-section reports, JVM classfile or Android DEX size reports, compiler arguments, checksums, run order, sample values, units, thermal and clock state, tool versions, comparison summary, and every disclosed limitation.

## Alternatives

Universal absolute nanosecond limits were rejected because Android and iPhone processors are not comparable. Measurements without thresholds were rejected because they cannot enforce the product claim. Exact `1.00` parity was rejected because measurement noise would fail equivalent code. Zero allocation during generic checked construction was rejected because the selected result and value-class interface may allocate or box before the hot path. One four-byte workload was rejected because it does not exercise validation scaling, nesting, arrays, or code-size growth. Hosted-runner timing gates were rejected because infrastructure variation dominates small bit-operation measurements. Feature-owned baseline regeneration was rejected because it normalizes regressions.

## Risks

Relative budgets can pass when both generated and hand-written implementations are slow, so reference code requires review and retained absolute measurements. Seven paired sessions and two physical devices add merge latency and infrastructure cost. The 16 KiB runtime and 64 KiB header ceilings may require revision after measured implementation evidence; changing them requires explicit baseline governance. Instruments does not expose the same normalized allocation metric as HotSpot or ART. Compiler upgrades can change code size and timing independently of source and therefore require a baseline-change review.

## Migration

No performance baseline exists. Before merging the first budgeted implementation, the project must commit canonical descriptors, hand-written references, benchmark harnesses, environment metadata, smoke profiles, raw baseline evidence, and comparison tooling. Later hardware or toolchain replacement establishes a separately reviewed baseline without deleting old data. Any approved budget change records rationale, measurement impact, and migration in this ADR and its benchmark metadata.
