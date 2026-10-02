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
- Schema annotations are preview API and require opting in to
  `KompactPreview`. Runtime reader and writer APIs do not require that opt-in.

The `0.5.0-SNAPSHOT` reference includes framed schemas and the KMP generation
plugin. Those additions are not part of the published `0.4.0` release; see the
[consumer setup guide](how-to/consume-from-another-project.md) for the version
available from Maven Central.

## Update the generated reference

KDoc in the runtime module is the source of truth. Regenerate the committed
Markdown pages with:

```bash
./gradlew :kompact:dokkaGeneratePublicationMarkdown
```

CI checks the generated tree for drift on macOS. For a quick introduction to
the API, start with the [getting-started tutorial](getting-started.md).
