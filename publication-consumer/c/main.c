#include "benchmark_child_v0.h"
#include "benchmark_large_v0.h"
#include "benchmark_medium_v0.h"
#include "published_packet_v0.h"

int kompact_other_translation_unit(void);

int main(void)
{
    return kompact_other_translation_unit();
}
