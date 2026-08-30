# Kompact

Kompact defines versioned, bit-packed BLE payload contracts shared by Kotlin applications and C firmware.

## Language

**Kompact schema**:
A versioned contract that assigns each field a precise representation and location in a bit-packed payload.
_Avoid_: Model, data class

**Kompact envelope**:
The leading bits that identify the Kompact schema and its layout version before a payload is decoded.
_Avoid_: Header, discriminator

**Protocol namespace**:
The enclosing BLE service or application protocol that selects one Kompact schema registry and gives its schema IDs meaning.
_Avoid_: Global registry, repository namespace

**Layout version**:
An immutable numbered representation of one Kompact schema within a protocol namespace.
_Avoid_: Revision, format version

**Fixed aggregate**:
A positive, fixed-count composition whose complete bit size and every element position are known from its Kompact schema.
_Avoid_: Collection, variable array

**Nested schema**:
The body of one exact Kompact schema version embedded inside another schema without a second envelope.
_Avoid_: Embedded packet, child message

**Reserved range**:
A named span of payload bits that must remain zero until a new layout version assigns them meaning.
_Avoid_: Padding, unused gap

**Kompact view**:
A live, typed, non-owning interpretation of caller-owned bytes according to one Kompact schema.
_Avoid_: Model, wrapper, snapshot

**Kompact writer**:
A typed interface that exclusively updates caller-owned bytes according to one Kompact schema while it is in use.
_Avoid_: Builder, serializer, shared mutator
