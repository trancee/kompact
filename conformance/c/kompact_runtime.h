#ifndef KOMPACT_RUNTIME_H
#define KOMPACT_RUNTIME_H

#include <stdbool.h>
#include <float.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

#define KOMPACT_RUNTIME_INTERFACE_VERSION UINT8_C(1)
typedef uint8_t kompact_status_t;
#define KOMPACT_STATUS_OK UINT8_C(0x00)
#define KOMPACT_STATUS_NULL_ARGUMENT UINT8_C(0x01)
#define KOMPACT_STATUS_INVALID_PACKET_LENGTH UINT8_C(0x02)
#define KOMPACT_STATUS_RESERVED_SCHEMA_ID UINT8_C(0x03)
#define KOMPACT_STATUS_UNKNOWN_SCHEMA_ID UINT8_C(0x04)
#define KOMPACT_STATUS_UNSUPPORTED_LAYOUT_VERSION UINT8_C(0x05)
#define KOMPACT_STATUS_NONZERO_TAIL_BITS UINT8_C(0x06)
#define KOMPACT_STATUS_UNKNOWN_ENUM_CODE UINT8_C(0x07)
#define KOMPACT_STATUS_NONZERO_RESERVED_BITS UINT8_C(0x08)
#define KOMPACT_STATUS_NONZERO_ABSENT_OPTIONAL UINT8_C(0x09)
#define KOMPACT_STATUS_VALUE_OUT_OF_RANGE UINT8_C(0x0A)
#define KOMPACT_STATUS_INDEX_OUT_OF_RANGE UINT8_C(0x0B)
#define KOMPACT_STATUS_INTERNAL_INVARIANT_FAILURE UINT8_C(0x0C)

static inline uint64_t kompact_internal_read_u64(
    const uint8_t *packet,
    uint32_t bit_offset,
    uint8_t bit_width)
{
    uint64_t value = UINT64_C(0);
    uint8_t value_bit;
    for (value_bit = 0; value_bit < bit_width; ++value_bit) {
        uint32_t packet_bit = bit_offset + value_bit;
        uint8_t bit = (uint8_t)(((uint32_t)packet[packet_bit >> 3] >> (packet_bit & 7u)) & UINT32_C(1));
        value |= ((uint64_t)bit) << value_bit;
    }
    return value;
}

static inline void kompact_internal_write_u64(
    uint8_t *packet,
    uint32_t bit_offset,
    uint8_t bit_width,
    uint64_t value)
{
    uint8_t value_bit;
    for (value_bit = 0; value_bit < bit_width; ++value_bit) {
        uint32_t packet_bit = bit_offset + value_bit;
        uint8_t mask = (uint8_t)(UINT8_C(1) << (packet_bit & 7u));
        uint8_t *target = &packet[packet_bit >> 3];
        if (((value >> value_bit) & UINT64_C(1)) == 0u) {
            *target = (uint8_t)(*target & (uint8_t)~mask);
        } else {
            *target = (uint8_t)(*target | mask);
        }
    }
}

#if FLT_RADIX != 2 || FLT_MANT_DIG != 24 || FLT_MAX_EXP != 128
#error "Kompact requires IEEE binary32 float"
#endif
#if DBL_MANT_DIG != 53 || DBL_MAX_EXP != 1024
#error "Kompact requires IEEE binary64 double"
#endif
typedef char kompact_float_must_be_4_bytes[(sizeof(float) == 4u) ? 1 : -1];
typedef char kompact_double_must_be_8_bytes[(sizeof(double) == 8u) ? 1 : -1];

static inline float kompact_internal_read_f32(const uint8_t *packet, uint32_t bit_offset)
{
    uint32_t bits = (uint32_t)kompact_internal_read_u64(packet, bit_offset, 32u);
    float value;
    memcpy(&value, &bits, sizeof value);
    return value;
}

static inline double kompact_internal_read_f64(const uint8_t *packet, uint32_t bit_offset)
{
    uint64_t bits = kompact_internal_read_u64(packet, bit_offset, 64u);
    double value;
    memcpy(&value, &bits, sizeof value);
    return value;
}

static inline void kompact_internal_write_f32(uint8_t *packet, uint32_t bit_offset, float value)
{
    uint32_t bits;
    memcpy(&bits, &value, sizeof bits);
    if ((bits & UINT32_C(0x7F800000)) == UINT32_C(0x7F800000) &&
        (bits & UINT32_C(0x007FFFFF)) != 0u) {
        bits = UINT32_C(0x7FC00000);
    }
    kompact_internal_write_u64(packet, bit_offset, 32u, bits);
}

static inline void kompact_internal_write_f64(uint8_t *packet, uint32_t bit_offset, double value)
{
    uint64_t bits;
    memcpy(&bits, &value, sizeof bits);
    if ((bits & UINT64_C(0x7FF0000000000000)) == UINT64_C(0x7FF0000000000000) &&
        (bits & UINT64_C(0x000FFFFFFFFFFFFF)) != 0u) {
        bits = UINT64_C(0x7FF8000000000000);
    }
    kompact_internal_write_u64(packet, bit_offset, 64u, bits);
}

#endif
