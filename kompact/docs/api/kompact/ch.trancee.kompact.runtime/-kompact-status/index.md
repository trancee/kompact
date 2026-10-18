//[kompact](../../../index.md)/[ch.trancee.kompact.runtime](../index.md)/[KompactStatus](index.md)

# KompactStatus

[common]\
enum [KompactStatus](index.md) : [Enum](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-enum/index.html)&lt;[KompactStatus](index.md)&gt; 

Outcome of a [KompactCursor](../-kompact-cursor/index.md) operation.

Entries are singletons, so returning or storing a status never allocates. Malformed-input statuses expose the matching shared [KompactDecodeError](../-kompact-decode-error/index.md) through decodeError; success and caller-contract statuses (invalid widths, bounds, values, alignment, aliasing, or workspace capacity) expose `null`.

## Entries

| | |
|---|---|
| [OK](-o-k/index.md) | [common]<br>[OK](-o-k/index.md)<br>Operation completed successfully. |
| [INVALID_BOUNDS](-i-n-v-a-l-i-d_-b-o-u-n-d-s/index.md) | [common]<br>[INVALID_BOUNDS](-i-n-v-a-l-i-d_-b-o-u-n-d-s/index.md)<br>A reset region was outside the representable buffer bit bounds. |
| [INVALID_WIDTH](-i-n-v-a-l-i-d_-w-i-d-t-h/index.md) | [common]<br>[INVALID_WIDTH](-i-n-v-a-l-i-d_-w-i-d-t-h/index.md)<br>A bit width or prefix width is unsupported. |
| [BOUNDS_ERROR](-b-o-u-n-d-s_-e-r-r-o-r/index.md) | [common]<br>[BOUNDS_ERROR](-b-o-u-n-d-s_-e-r-r-o-r/index.md)<br>An operation exceeded the cursor's bounded region. |
| [INVALID_VALUE](-i-n-v-a-l-i-d_-v-a-l-u-e/index.md) | [common]<br>[INVALID_VALUE](-i-n-v-a-l-i-d_-v-a-l-u-e/index.md)<br>A value cannot be represented by the requested wire width. |
| [BAD_LENGTH_PREFIX](-b-a-d_-l-e-n-g-t-h_-p-r-e-f-i-x/index.md) | [common]<br>[BAD_LENGTH_PREFIX](-b-a-d_-l-e-n-g-t-h_-p-r-e-f-i-x/index.md)<br>A length prefix is malformed or cannot be represented by cursor offsets. |
| [UNALIGNED_REGION](-u-n-a-l-i-g-n-e-d_-r-e-g-i-o-n/index.md) | [common]<br>[UNALIGNED_REGION](-u-n-a-l-i-g-n-e-d_-r-e-g-i-o-n/index.md)<br>A byte-oriented operation began at a non-byte-aligned position. |
| [INVALID_ARGUMENT](-i-n-v-a-l-i-d_-a-r-g-u-m-e-n-t/index.md) | [common]<br>[INVALID_ARGUMENT](-i-n-v-a-l-i-d_-a-r-g-u-m-e-n-t/index.md)<br>Mutable cursors or source and destination buffers alias incompatibly. |
| [INVALID_UTF8](-i-n-v-a-l-i-d_-u-t-f8/index.md) | [common]<br>[INVALID_UTF8](-i-n-v-a-l-i-d_-u-t-f8/index.md)<br>A string payload contains malformed UTF-8. |
| [WORKSPACE_TOO_SMALL](-w-o-r-k-s-p-a-c-e_-t-o-o_-s-m-a-l-l/index.md) | [common]<br>[WORKSPACE_TOO_SMALL](-w-o-r-k-s-p-a-c-e_-t-o-o_-s-m-a-l-l/index.md)<br>The caller-owned repeat workspace has too few checkpoint slots. |
| [TRUNCATED_INPUT](-t-r-u-n-c-a-t-e-d_-i-n-p-u-t/index.md) | [common]<br>[TRUNCATED_INPUT](-t-r-u-n-c-a-t-e-d_-i-n-p-u-t/index.md)<br>A repeated element is truncated or its payload crosses its region. |

## Properties

| Name | Summary |
|---|---|
| [decodeError](decode-error.md) | [common]<br>val [decodeError](decode-error.md): [KompactDecodeError](../-kompact-decode-error/index.md)?<br>Shared decode error for malformed input, or `null` for success and caller-contract statuses. |
| [entries](entries.md) | [common]<br>val [entries](entries.md): [EnumEntries](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.enums/-enum-entries/index.html)&lt;[KompactStatus](index.md)&gt;<br>Returns a representation of an immutable list of all enum entries, in the order they're declared. |

## Functions

| Name | Summary |
|---|---|
| [valueOf](value-of.md) | [common]<br>fun [valueOf](value-of.md)(value: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)): [KompactStatus](index.md)<br>Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |
| [values](values.md) | [common]<br>fun [values](values.md)(): [Array](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-array/index.html)&lt;[KompactStatus](index.md)&gt;<br>Returns an array containing the constants of this enum type, in the order they're declared. |