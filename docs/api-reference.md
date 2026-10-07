# API reference

Use this page to find the runtime API for a task. The generated
[KDoc reference](../kompact/docs/api/index.md) contains the complete public
runtime signatures, parameter descriptions, and member pages. It is regenerated
from the runtime source; do not edit the generated pages by hand.

## Find an API by task

| Task | Main API |
| --- | --- |
| Read or write packed bits and numeric fields | [`KompactRuntime`, `KompactWriter`, and `ScalarType`](../kompact/docs/api/kompact/ch.trancee.kompact.runtime/index.md) |
| Read length-delimited fields and nested frames | [`KompactFrame` and `KompactFraming`](../kompact/docs/api/kompact/ch.trancee.kompact.runtime/index.md) |
| Read repeated fields or borrow a byte range | [`KompactRepeatedView` and `KompactByteSlice`](../kompact/docs/api/kompact/ch.trancee.kompact.runtime/index.md) |
| Use caller-owned checked codec state | [`KompactCursor`, `KompactByteRange`, and `KompactRepeatWorkspace`](../kompact/docs/api/kompact/ch.trancee.kompact.runtime/index.md) |
| Handle a malformed value | [`KompactDecodeError` and result types](../kompact/docs/api/kompact/ch.trancee.kompact.runtime/index.md) |
| Declare a generated model | [`@KompactModel`, `@KompactField`, and `@KompactPreview`](../kompact/docs/api/kompact/ch.trancee.kompact.annotations/index.md) |
| See the bundled telemetry model | [`VehicleTelemetry`](../kompact/docs/api/kompact/ch.trancee.kompact.generated/index.md) |
| Use the `Kompact.Result` aliases | [`ch.trancee.kompact`](../kompact/docs/api/kompact/ch.trancee.kompact/index.md) |

## Contracts that affect how you use the API

- Checked scalar reads return typed results. They do not turn malformed input
  into a default value. See [How to handle malformed input](how-to/handle-decode-errors.md)
  for choosing between result handling and exceptions.
- A block passed to `KompactFrame.decode` can return a
  `KompactFrameResult`. Direct frame reads are throwing conveniences.
- Fixed-layout models use absolute `bitOffset` and `bitWidth` values.
  Sequential framed models use a contiguous `order` and explicit length or
  count prefixes. Pick the matching
  [schema guide](how-to/README.md) before defining a model.
- Generated views retain their backing `ByteArray`; borrowed slices and lazy
  repeated values depend on that array remaining unchanged while they are in
  use. The [architecture guide](architecture.md) explains this ownership tradeoff.
- Generated fixed-layout and framed views are not content-equality keys.
  Fixed-layout value-class equality follows the backing `ByteArray` identity;
  regular framed views use instance identity. Compare decoded fields or copy
  bytes explicitly when the application needs content equality.
- `KompactFrame.readBlob`, nested views, and repeated views borrow the source
  frame. `KompactByteSlice.toByteArray()` and generated blob properties that
  return `ByteArray` create owned copies; `KompactByteRange.copyTo` copies only
  into the destination array supplied by the caller.
- The raw `KompactRuntime.readBits` API assumes trusted, in-bounds input.
  `KompactCursor.writeBitsUnchecked` deliberately writes only the low bits and
  may truncate. Use checked cursor/generated writes for validation; they reject
  out-of-range values and unknown enum codes without partially changing the
  destination.
- `KompactCursor` stores its buffer and bit bounds, and reports checked
  operation status and error details through primitive properties. A
  `KompactByteRange` borrows its buffer; `copyTo` is an explicit copy into
  caller-provided storage. `KompactRepeatWorkspace` borrows caller-provided
  checkpoint storage.
- Generated `*Holder` codec operations take caller-owned cursors, ranges, and
  repeat workspaces. Probe-taking framed-holder operations, including
  scalar-only schemas, require a distinct probe cursor and preflight capacity
  and scalar value constraints before committing changes. Keep borrowed
  buffers unchanged while a holder or workspace refers to them.
- A repeat workspace needs `ceil(count / 64)` checkpoint slots; an empty repeat
  needs none. `readVariableRepeat` and `readFixedRepeat` return
  `STATUS_WORKSPACE_TOO_SMALL` without committing the cursor or workspace
  binding when capacity is insufficient.
- Variable-width repeats are validated before generated-holder decoding
  commits. The preflight and workspace-building decode each traverse the
  repeat, so this checked path favors failure atomicity over a single scan; no
  performance budget or allocation claim follows without target-specific
  measurement.
- Caller-owned cursors, ranges, holders, and repeat workspaces define the
  allocation-sensitive API boundary; allocating conveniences, `String`
  conversion, and explicit snapshots/copies are outside it. A zero-allocation
  claim is target- and workload-specific. Bounded Native probe results for
  selected operations on Android Arm64 and iOS Arm64 debug and release test
  binaries are recorded in the
  [allocation research note](research/allocation-boxing-measurement.md). They
  use Kotlin/Native GC sweep statistics, which are testing/debugging data, and
  do not establish a general zero-allocation guarantee or numeric performance
  budget.
- Generated framed encoders reject range sources backed by the destination
  buffer. The low-level `writeByteRange` operation rejects overlapping
  in-place copies. These cursor APIs are available in the current checkout;
  they are not part of the published `0.7.0` artifact.
- Schema annotations are preview API and require opting in to
  `KompactPreview`. Runtime reader and writer APIs do not require that opt-in.

This reference tracks the current checkout (`0.8.0-SNAPSHOT`); the latest
published release is `0.7.0`. See the
[consumer setup guide](how-to/consume-from-another-project.md) for the
published coordinates or instructions for trying this checkout.

## Update the generated reference

KDoc in the runtime module is the source of truth. Regenerate the committed
Markdown pages with:

```bash
./gradlew :kompact:dokkaGeneratePublicationMarkdown
```

CI checks the generated tree for drift on macOS. For a quick introduction to
the API, start with the [getting-started tutorial](getting-started.md).
