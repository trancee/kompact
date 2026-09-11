# How-to guides

Task-oriented guides for implementing common things with Kompact. Each
guide is a recipe: it assumes you already know Kotlin and you have a
working Kompact setup. Pick the guide that matches your goal.

| If you want to … | Read |
| --- | --- |
| Define your own message model (like the bundled `VehicleTelemetry`) | [`define-message.md`](define-message.md) |
| Pack or parse a string / blob / nested composite / repeated field | [`long-form-payloads.md`](long-form-payloads.md) |
| Handle a `KompactDecodeError` (bounds overrun, bad length prefix, unknown enum code) | [`handle-decode-errors.md`](handle-decode-errors.md) |
| Send a frame over BLE / receive one back | [`integrate-ble.md`](integrate-ble.md) |
| Consume Kompact from a separate Kotlin / KMP project | [`consume-from-another-project.md`](consume-from-another-project.md) |

**Prerequisites.** All guides assume:

- A working Kotlin Multiplatform toolchain (Kotlin 2.3.21, JDK 21).
- `ch.trancee.kompact:kompact:0.1.0-SNAPSHOT` on the classpath. See
  [`consume-from-another-project.md`](consume-from-another-project.md) if
  you are not yet building against it.
- Familiarity with the [getting started tutorial](../getting-started.md)
  — every how-to references `KompactWriter`, `KompactRuntime`, and the
  typed result classes by name without re-introducing them.

**Not a tutorial.** If you are new to Kompact, start with the
[getting started tutorial](../getting-started.md) first; it builds
the same 2-byte telemetry frame in 4 steps with visible byte-level
output at each step.
