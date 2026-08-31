#ifndef KOMPACT_VEHICLE_TELEMETRY_V0_H
#define KOMPACT_VEHICLE_TELEMETRY_V0_H

#include "kompact_runtime.h"

#if KOMPACT_RUNTIME_INTERFACE_VERSION != 1
#error "incompatible Kompact runtime header"
#endif
#define KOMPACT_VEHICLE_TELEMETRY_V0_GENERATOR_VERSION "0.1.0"
#define KOMPACT_VEHICLE_TELEMETRY_V0_DESCRIPTOR_SHA256 "23d2ee1e222cc72beb4dffa5d9dbf9335aea966ef6c6a1e6beb7f1a9c746bf76"
#define KOMPACT_VEHICLE_TELEMETRY_V0_SCHEMA_ID UINT16_C(42)
#define KOMPACT_VEHICLE_TELEMETRY_V0_LAYOUT_VERSION UINT8_C(0)
#define KOMPACT_VEHICLE_TELEMETRY_V0_BODY_BITS UINT32_C(16)
#define KOMPACT_VEHICLE_TELEMETRY_V0_PACKET_BYTES ((size_t)4)

typedef struct { const uint8_t *packet; } kompact_vehicle_telemetry_v0_view_t;
typedef struct { uint8_t *packet; } kompact_vehicle_telemetry_v0_writer_t;

static inline kompact_status_t kompact_vehicle_telemetry_v0_wrap(const uint8_t *packet, size_t packet_size, kompact_vehicle_telemetry_v0_view_t *out_view) {
    uint16_t envelope;
    if (packet == NULL || out_view == NULL) return KOMPACT_STATUS_NULL_ARGUMENT;
    if (packet_size < 2u) return KOMPACT_STATUS_INVALID_PACKET_LENGTH;
    envelope = (uint16_t)kompact_internal_read_u64(packet, 0u, 16u);
    if ((envelope & UINT16_C(0x0FFF)) == UINT16_C(0)) return KOMPACT_STATUS_RESERVED_SCHEMA_ID;
    if ((envelope & UINT16_C(0x0FFF)) != KOMPACT_VEHICLE_TELEMETRY_V0_SCHEMA_ID) return KOMPACT_STATUS_UNKNOWN_SCHEMA_ID;
    if ((envelope >> 12u) != KOMPACT_VEHICLE_TELEMETRY_V0_LAYOUT_VERSION) return KOMPACT_STATUS_UNSUPPORTED_LAYOUT_VERSION;
    if (packet_size != (size_t)4) return KOMPACT_STATUS_INVALID_PACKET_LENGTH;
    {
        uint64_t code = kompact_internal_read_u64(packet, 16u, 4u);
        if (code != UINT64_C(0) && code != UINT64_C(1) && code != UINT64_C(2)) return KOMPACT_STATUS_UNKNOWN_ENUM_CODE;
    }
    if (kompact_internal_read_u64(packet, 31u, 1u) != 0u) return KOMPACT_STATUS_NONZERO_RESERVED_BITS;
    out_view->packet = packet;
    return KOMPACT_STATUS_OK;
}

static inline kompact_status_t kompact_vehicle_telemetry_v0_initialize(uint8_t *packet, size_t packet_size, kompact_vehicle_telemetry_v0_writer_t *out_writer) {
    if (packet == NULL || out_writer == NULL) return KOMPACT_STATUS_NULL_ARGUMENT;
    if (packet_size != KOMPACT_VEHICLE_TELEMETRY_V0_PACKET_BYTES) return KOMPACT_STATUS_INVALID_PACKET_LENGTH;
    memset(packet, 0, packet_size);
    kompact_internal_write_u64(packet, 0u, 12u, KOMPACT_VEHICLE_TELEMETRY_V0_SCHEMA_ID);
    kompact_internal_write_u64(packet, 12u, 4u, KOMPACT_VEHICLE_TELEMETRY_V0_LAYOUT_VERSION);
    out_writer->packet = packet;
    return KOMPACT_STATUS_OK;
}

static inline kompact_status_t kompact_vehicle_telemetry_v0_edit(uint8_t *packet, size_t packet_size, kompact_vehicle_telemetry_v0_writer_t *out_writer) {
    kompact_vehicle_telemetry_v0_view_t view;
    kompact_status_t status;
    if (out_writer == NULL) return KOMPACT_STATUS_NULL_ARGUMENT;
    status = kompact_vehicle_telemetry_v0_wrap(packet, packet_size, &view);
    if (status != KOMPACT_STATUS_OK) return status;
    out_writer->packet = packet;
    return KOMPACT_STATUS_OK;
}

#define KOMPACT_VEHICLE_TELEMETRY_V0_BATTERY_STATUS_BIT_OFFSET UINT32_C(16)
#define KOMPACT_VEHICLE_TELEMETRY_V0_BATTERY_STATUS_BIT_WIDTH UINT8_C(4)
typedef uint8_t kompact_vehicle_telemetry_v0_battery_status_t;
#define KOMPACT_VEHICLE_TELEMETRY_V0_BATTERY_STATUS_NORMAL ((kompact_vehicle_telemetry_v0_battery_status_t)UINT64_C(0))
#define KOMPACT_VEHICLE_TELEMETRY_V0_BATTERY_STATUS_LOW ((kompact_vehicle_telemetry_v0_battery_status_t)UINT64_C(1))
#define KOMPACT_VEHICLE_TELEMETRY_V0_BATTERY_STATUS_CRITICAL ((kompact_vehicle_telemetry_v0_battery_status_t)UINT64_C(2))
static inline kompact_vehicle_telemetry_v0_battery_status_t kompact_vehicle_telemetry_v0_battery_status(kompact_vehicle_telemetry_v0_view_t view) { return (kompact_vehicle_telemetry_v0_battery_status_t)kompact_internal_read_u64(view.packet, KOMPACT_VEHICLE_TELEMETRY_V0_BATTERY_STATUS_BIT_OFFSET, KOMPACT_VEHICLE_TELEMETRY_V0_BATTERY_STATUS_BIT_WIDTH); }
static inline kompact_status_t kompact_vehicle_telemetry_v0_write_battery_status(kompact_vehicle_telemetry_v0_writer_t writer, kompact_vehicle_telemetry_v0_battery_status_t value) {
    if (value != KOMPACT_VEHICLE_TELEMETRY_V0_BATTERY_STATUS_NORMAL && value != KOMPACT_VEHICLE_TELEMETRY_V0_BATTERY_STATUS_LOW && value != KOMPACT_VEHICLE_TELEMETRY_V0_BATTERY_STATUS_CRITICAL) return KOMPACT_STATUS_UNKNOWN_ENUM_CODE;
    kompact_internal_write_u64(writer.packet, KOMPACT_VEHICLE_TELEMETRY_V0_BATTERY_STATUS_BIT_OFFSET, KOMPACT_VEHICLE_TELEMETRY_V0_BATTERY_STATUS_BIT_WIDTH, value);
    return KOMPACT_STATUS_OK;
}

#define KOMPACT_VEHICLE_TELEMETRY_V0_SPEED_BIT_OFFSET UINT32_C(20)
#define KOMPACT_VEHICLE_TELEMETRY_V0_SPEED_BIT_WIDTH UINT8_C(10)
static inline uint32_t kompact_vehicle_telemetry_v0_speed(kompact_vehicle_telemetry_v0_view_t view) { return (uint32_t)kompact_internal_read_u64(view.packet, KOMPACT_VEHICLE_TELEMETRY_V0_SPEED_BIT_OFFSET, KOMPACT_VEHICLE_TELEMETRY_V0_SPEED_BIT_WIDTH); }
static inline kompact_status_t kompact_vehicle_telemetry_v0_write_speed(kompact_vehicle_telemetry_v0_writer_t writer, uint32_t value) {
    if ((uint64_t)value >= (UINT64_C(1) << 10u)) return KOMPACT_STATUS_VALUE_OUT_OF_RANGE;
    kompact_internal_write_u64(writer.packet, KOMPACT_VEHICLE_TELEMETRY_V0_SPEED_BIT_OFFSET, KOMPACT_VEHICLE_TELEMETRY_V0_SPEED_BIT_WIDTH, (uint64_t)value);
    return KOMPACT_STATUS_OK;
}

#define KOMPACT_VEHICLE_TELEMETRY_V0_IS_MALFUNCTIONING_BIT_OFFSET UINT32_C(30)
#define KOMPACT_VEHICLE_TELEMETRY_V0_IS_MALFUNCTIONING_BIT_WIDTH UINT8_C(1)
static inline bool kompact_vehicle_telemetry_v0_is_malfunctioning(kompact_vehicle_telemetry_v0_view_t view) { return kompact_internal_read_u64(view.packet, KOMPACT_VEHICLE_TELEMETRY_V0_IS_MALFUNCTIONING_BIT_OFFSET, 1u) != 0u; }
static inline kompact_status_t kompact_vehicle_telemetry_v0_write_is_malfunctioning(kompact_vehicle_telemetry_v0_writer_t writer, bool value) { kompact_internal_write_u64(writer.packet, KOMPACT_VEHICLE_TELEMETRY_V0_IS_MALFUNCTIONING_BIT_OFFSET, 1u, value ? 1u : 0u); return KOMPACT_STATUS_OK; }

static inline kompact_vehicle_telemetry_v0_view_t kompact_vehicle_telemetry_v0_writer_view(kompact_vehicle_telemetry_v0_writer_t writer) {
    kompact_vehicle_telemetry_v0_view_t view = { writer.packet };
    return view;
}

#endif
