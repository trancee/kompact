#ifndef KOMPACT_VEHICLE_TELEMETRY_V0_H
#define KOMPACT_VEHICLE_TELEMETRY_V0_H

#include "kompact_runtime.h"

#if KOMPACT_RUNTIME_INTERFACE_VERSION != 1
#error "incompatible Kompact runtime header"
#endif
#define KOMPACT_VEHICLE_TELEMETRY_V0_GENERATOR_VERSION "0.1.0"
#define KOMPACT_VEHICLE_TELEMETRY_V0_DESCRIPTOR_SHA256 "9c4c3ffdf1eab8254a7835d09b2770b626e99b8e78fc80922aa0a9917373bb3c"
#define KOMPACT_VEHICLE_TELEMETRY_V0_SCHEMA_ID UINT16_C(42)
#define KOMPACT_VEHICLE_TELEMETRY_V0_LAYOUT_VERSION UINT8_C(0)
#define KOMPACT_VEHICLE_TELEMETRY_V0_BODY_BITS UINT32_C(16)
#define KOMPACT_VEHICLE_TELEMETRY_V0_PACKET_BYTES ((size_t)4)

typedef struct { const uint8_t *packet; } kompact_vehicle_telemetry_v0_view_t;
typedef struct { uint8_t *packet; } kompact_vehicle_telemetry_v0_writer_t;

static inline kompact_status_t kompact_vehicle_telemetry_v0_wrap(
    const uint8_t *packet,
    size_t packet_size,
    kompact_vehicle_telemetry_v0_view_t *out_view)
{
    uint16_t envelope;
    if (packet == NULL || out_view == NULL) return KOMPACT_STATUS_NULL_ARGUMENT;
    if (packet_size < 2u) return KOMPACT_STATUS_INVALID_PACKET_LENGTH;
    envelope = (uint16_t)kompact_internal_read_u64(packet, 0u, 16u);
    if ((envelope & UINT16_C(0x0FFF)) != KOMPACT_VEHICLE_TELEMETRY_V0_SCHEMA_ID) return KOMPACT_STATUS_UNKNOWN_SCHEMA_ID;
    if ((envelope >> 12u) != KOMPACT_VEHICLE_TELEMETRY_V0_LAYOUT_VERSION) return KOMPACT_STATUS_UNSUPPORTED_LAYOUT_VERSION;
    if (packet_size != (size_t)4) return KOMPACT_STATUS_INVALID_PACKET_LENGTH;
    if (kompact_internal_read_u64(packet, 31u, 1u) != 0u) return KOMPACT_STATUS_NONZERO_RESERVED_BITS;
    out_view->packet = packet;
    return KOMPACT_STATUS_OK;
}

static inline kompact_status_t kompact_vehicle_telemetry_v0_initialize(
    uint8_t *packet,
    size_t packet_size,
    kompact_vehicle_telemetry_v0_writer_t *out_writer)
{
    if (packet == NULL || out_writer == NULL) return KOMPACT_STATUS_NULL_ARGUMENT;
    if (packet_size != KOMPACT_VEHICLE_TELEMETRY_V0_PACKET_BYTES) return KOMPACT_STATUS_INVALID_PACKET_LENGTH;
    memset(packet, 0, packet_size);
    kompact_internal_write_u64(packet, 0u, 12u, KOMPACT_VEHICLE_TELEMETRY_V0_SCHEMA_ID);
    kompact_internal_write_u64(packet, 12u, 4u, KOMPACT_VEHICLE_TELEMETRY_V0_LAYOUT_VERSION);
    out_writer->packet = packet;
    return KOMPACT_STATUS_OK;
}

static inline uint32_t kompact_vehicle_telemetry_v0_speed(kompact_vehicle_telemetry_v0_view_t view)
{
    return (uint32_t)kompact_internal_read_u64(view.packet, 20u, 10u);
}

static inline kompact_status_t kompact_vehicle_telemetry_v0_write_speed(
    kompact_vehicle_telemetry_v0_writer_t writer,
    uint32_t value)
{
    if (value > UINT32_C(1023)) return KOMPACT_STATUS_VALUE_OUT_OF_RANGE;
    kompact_internal_write_u64(writer.packet, 20u, 10u, value);
    return KOMPACT_STATUS_OK;
}

static inline uint32_t kompact_vehicle_telemetry_v0_battery_status(
    kompact_vehicle_telemetry_v0_view_t view)
{
    return (uint32_t)kompact_internal_read_u64(view.packet, 16u, 4u);
}

static inline kompact_status_t kompact_vehicle_telemetry_v0_write_battery_status(
    kompact_vehicle_telemetry_v0_writer_t writer,
    uint32_t value)
{
    if (value > UINT32_C(15)) return KOMPACT_STATUS_VALUE_OUT_OF_RANGE;
    kompact_internal_write_u64(writer.packet, 16u, 4u, value);
    return KOMPACT_STATUS_OK;
}

static inline bool kompact_vehicle_telemetry_v0_is_malfunctioning(
    kompact_vehicle_telemetry_v0_view_t view)
{
    return kompact_internal_read_u64(view.packet, 30u, 1u) != 0u;
}

static inline kompact_status_t kompact_vehicle_telemetry_v0_write_is_malfunctioning(
    kompact_vehicle_telemetry_v0_writer_t writer,
    bool value)
{
    kompact_internal_write_u64(writer.packet, 30u, 1u, value ? 1u : 0u);
    return KOMPACT_STATUS_OK;
}

static inline kompact_vehicle_telemetry_v0_view_t kompact_vehicle_telemetry_v0_writer_view(
    kompact_vehicle_telemetry_v0_writer_t writer)
{
    kompact_vehicle_telemetry_v0_view_t view = { writer.packet };
    return view;
}

#endif
