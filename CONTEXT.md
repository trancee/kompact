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

**Kompact view**:
A typed, non-owning interpretation of bytes according to one Kompact schema.
_Avoid_: Model, wrapper

**Kompact writer**:
A typed interface that updates caller-owned bytes according to one Kompact schema.
_Avoid_: Builder, serializer
