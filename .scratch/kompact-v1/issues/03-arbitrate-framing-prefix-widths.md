---
Type: grilling
Status: resolved
Labels:
  - wayfinder:grilling
  - scope:wire
Blocked by:
  - none
Decides:
  - "v1 framing length-prefix scheme"
---

## Question

Should v1.0 framing keep kompact-spec [ticket 05](../../kompact-spec/issues/05-variable-length-framing.md)'s
**fixed-width little-endian length prefixes** (`{8,16,32}` bit), or adopt
**arbitrary / LEB128 / bit-length prefixes** (the external review's suggestion)?

This is **wire-incompatible** with the locked format → MAJOR. It changes the ABI
golden, so it **blocks** the scaffold step.

## Context

- [ticket 05](../../kompact-spec/issues/05-variable-length-framing.md): decided
  fixed-width LE length prefix per field, sequential parse-forward, count-prefixed
  repeats. Uniform prefix width enables older readers to skip unknown trailing
  length-delimited fields.
- External review: "restricting prefixes to {8,16,32} is arbitrary; 24-bit or
  variable-length (LEB128-style) prefixes would be more compact."
- The review also proposed bit-length prefixes for denser packing, but
  byte-count prefixes force byte alignment (trading density for simplicity).

## Acceptance

- Decision recorded (keep ticket 05 fixed-width; or adopt an alternative width
  scheme — which also forces a version-prefix/contract amendment under
  [ticket 09](../../kompact-spec/issues/09-versioning-schema-evolution.md)).
- Decision pins the v1 framing format for the ABI golden.
- This ticket closed; the scaffold ticket can be claimed.

## Resolution

**Reject the framing-width change** (recommended-default; veto invited). v1.0
**keeps** kompact-spec [ticket 05](../../kompact-spec/issues/05-variable-length-framing.md):
fixed-width little-endian length prefixes ({8,16,32} bits), sequential
parse-forward, count-prefixed repeats.

Rationale: 24-bit / LEB128 / bit-length prefixes are **wire-incompatible (MAJOR)**
with the locked format for a density gain that is marginal on BLE-style frames
(tens of bytes), and they break ticket 05's skip-unknown-trailing-field property
(uniform widths let older readers skip unknown length-delimited fields — essential
for the additive-only evolution model in [ticket 09](../../kompact-spec/issues/09-versioning-schema-evolution.md)).
Bit-length prefixes additionally force byte alignment, negating their density
premise. Arbitrary/LEB128 widths stay deferred (out of v1 scope). Ticket 05
closed with no change; the v1 ABI golden uses the decided format.
