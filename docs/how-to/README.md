# How-to guides

These guides assume that you know Kotlin. They describe the current checkout
(`0.10.0-SNAPSHOT`). Pick the guide for your task:

| Task | Guide |
| --- | --- |
| Add Kompact to a JVM, Android, or Kotlin Multiplatform project | [How to add Kompact to a project](consume-from-another-project.md) |
| Define a model whose fields have fixed bit positions | [How to define a fixed-layout model](define-message.md) |
| Define a model with strings, blobs, nested models, or repeats | [How to define a framed model](define-framed-schema.md) |
| Write and read strings, blobs, nested regions, or repeats without generated code | [How to write and read variable-length fields](long-form-payloads.md) |
| Decide what to do when decoding fails | [How to handle malformed input](handle-decode-errors.md) |
| Send and receive Kompact frames over BLE | [How to pass Kompact frames over BLE](integrate-ble.md) |
| Measure runtime and generated-holder performance on a host | [How to measure runtime performance on a host](benchmark-runtime.md) |

If you are new to Kompact, start with the
[getting-started tutorial](../getting-started.md). For signatures and error
types, use the [API reference](../api-reference.md). For the reasons behind the
design, read [Design and trade-offs](../architecture.md).
