#include <stddef.h>
#include <stdint.h>

#include "vehicle_telemetry_v0.h"

static uint32_t next_value(uint32_t *state)
{
    uint32_t value = *state;
    value ^= value << 13u;
    value ^= value >> 17u;
    value ^= value << 5u;
    *state = value;
    return value;
}

int main(void)
{
    uint32_t state = UINT32_C(0x6D2B79F5);
    size_t iteration;
    for (iteration = 0u; iteration < 100000u; ++iteration) {
        uint8_t packet[5];
        size_t byte;
        size_t size = (size_t)(next_value(&state) % UINT32_C(6));
        uint8_t sentinel = 0u;
        kompact_vehicle_telemetry_v0_view_t view = { &sentinel };
        kompact_status_t status;
        for (byte = 0u; byte < sizeof packet; ++byte) {
            packet[byte] = (uint8_t)next_value(&state);
        }
        status = kompact_vehicle_telemetry_v0_wrap(packet, size, &view);
        if (status != KOMPACT_STATUS_OK && view.packet != &sentinel) return 1;
    }
    return 0;
}
