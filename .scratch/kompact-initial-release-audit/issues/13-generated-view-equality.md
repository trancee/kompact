---
Type: grilling
---

## Question

What equality and hashing semantics should generated fixed-layout value
classes and framed views expose? Decide whether they compare schema field
values, bounded wire bytes, or backing-array identity, and whether `toString`
should follow the same model. The current fixed views wrap `ByteArray`, while
framed views do not generate value equality; document and test the chosen
contract consistently.
