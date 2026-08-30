#include <stdint.h>
#include <string.h>

#include "vehicle_telemetry_v0.h"

static int nominal_packet_matches_manifest(void)
{
    uint8_t packet[4];
    const uint8_t expected[4] = { UINT8_C(0x2A), UINT8_C(0x00), UINT8_C(0xF2), UINT8_C(0x5F) };
    kompact_vehicle_telemetry_v0_writer_t writer;
    kompact_vehicle_telemetry_v0_view_t view;

    if (kompact_vehicle_telemetry_v0_initialize(packet, sizeof packet, &writer) != KOMPACT_STATUS_OK) return 1;
    if (kompact_vehicle_telemetry_v0_write_battery_status(writer, UINT32_C(2)) != KOMPACT_STATUS_OK) return 2;
    if (kompact_vehicle_telemetry_v0_write_speed(writer, UINT32_C(511)) != KOMPACT_STATUS_OK) return 3;
    if (kompact_vehicle_telemetry_v0_write_is_malfunctioning(writer, true) != KOMPACT_STATUS_OK) return 4;
    if (memcmp(packet, expected, sizeof packet) != 0) return 5;

    view = kompact_vehicle_telemetry_v0_writer_view(writer);
    if (kompact_vehicle_telemetry_v0_battery_status(view) != UINT32_C(2)) return 6;
    if (kompact_vehicle_telemetry_v0_speed(view) != UINT32_C(511)) return 7;
    if (!kompact_vehicle_telemetry_v0_is_malfunctioning(view)) return 8;
    return 0;
}

static int rejected_write_preserves_packet(void)
{
    uint8_t packet[4] = { UINT8_C(0x2A), UINT8_C(0x00), UINT8_C(0xF2), UINT8_C(0x5F) };
    uint8_t before[4];
    kompact_vehicle_telemetry_v0_writer_t writer = { packet };
    memcpy(before, packet, sizeof packet);

    if (kompact_vehicle_telemetry_v0_write_speed(writer, UINT32_C(1024)) != KOMPACT_STATUS_VALUE_OUT_OF_RANGE) return 1;
    return memcmp(packet, before, sizeof packet) == 0 ? 0 : 2;
}

static int reserved_bit_is_rejected(void)
{
    uint8_t packet[4] = { UINT8_C(0x2A), UINT8_C(0x00), UINT8_C(0xF2), UINT8_C(0xDF) };
    kompact_vehicle_telemetry_v0_view_t view;
    return kompact_vehicle_telemetry_v0_wrap(packet, sizeof packet, &view) == KOMPACT_STATUS_NONZERO_RESERVED_BITS ? 0 : 1;
}

int main(void)
{
    int result = nominal_packet_matches_manifest();
    if (result != 0) return 10 + result;
    result = rejected_write_preserves_packet();
    if (result != 0) return 20 + result;
    result = reserved_bit_is_rejected();
    if (result != 0) return 30 + result;
    return 0;
}
