package example

import ch.trancee.kompact.annotations.KompactArray
import ch.trancee.kompact.annotations.KompactBytes
import ch.trancee.kompact.annotations.KompactCode
import ch.trancee.kompact.annotations.KompactEnum
import ch.trancee.kompact.annotations.KompactField
import ch.trancee.kompact.annotations.KompactNested
import ch.trancee.kompact.annotations.KompactOptional
import ch.trancee.kompact.annotations.KompactReserved
import ch.trancee.kompact.annotations.KompactSchema

@KompactEnum(bitWidth = 4)
enum class BenchmarkMode {
    @KompactCode(stableName = "idle", code = 0) IDLE,
    @KompactCode(stableName = "active", code = 5) ACTIVE,
    @KompactCode(stableName = "fault", code = 15) FAULT,
}

@KompactSchema(registryName = "benchmark_child", id = 2, version = 0)
@KompactReserved(stableName = "future", bitOffset = 41, bitWidth = 23)
interface BenchmarkChildSchema {
    @KompactField(stableName = "value", semanticType = "value", bitOffset = 0, bitWidth = 16)
    val value: UInt

    @KompactOptional
    @KompactField(
        stableName = "temperature",
        semanticType = "temperature",
        bitOffset = 16,
        bitWidth = 9,
    )
    val temperature: Int

    @KompactBytes(count = 2)
    @KompactField(stableName = "payload", semanticType = "payload", bitOffset = 25, bitWidth = 16)
    val payload: ByteArray
}

@KompactSchema(registryName = "benchmark_medium", id = 3, version = 0)
@KompactReserved(stableName = "future", bitOffset = 219, bitWidth = 21)
interface BenchmarkMediumSchema {
    @KompactField(stableName = "enabled", semanticType = "enabled", bitOffset = 0, bitWidth = 1)
    val enabled: Boolean

    @KompactField(stableName = "signed", semanticType = "signed", bitOffset = 1, bitWidth = 7)
    val signed: Int

    @KompactField(stableName = "unsigned", semanticType = "unsigned", bitOffset = 8, bitWidth = 9)
    val unsigned: UInt

    @KompactField(stableName = "mode", semanticType = "mode", bitOffset = 17, bitWidth = 4)
    val mode: BenchmarkMode

    @KompactField(stableName = "ratio", semanticType = "ratio", bitOffset = 21, bitWidth = 32)
    val ratio: Float

    @KompactField(stableName = "distance", semanticType = "distance", bitOffset = 53, bitWidth = 64)
    val distance: Double

    @KompactBytes(count = 2)
    @KompactField(stableName = "payload", semanticType = "payload", bitOffset = 117, bitWidth = 16)
    val payload: ByteArray

    @KompactArray(count = 2)
    @KompactField(stableName = "samples", semanticType = "sample", bitOffset = 133, bitWidth = 14)
    val samples: UInt

    @KompactOptional
    @KompactField(
        stableName = "temperature",
        semanticType = "temperature",
        bitOffset = 147,
        bitWidth = 8,
    )
    val temperature: Int

    @KompactNested(registryName = "benchmark_child", schemaId = 2, version = 0)
    @KompactField(stableName = "child", semanticType = "child", bitOffset = 155, bitWidth = 64)
    val child: BenchmarkChildSchema
}

@KompactSchema(registryName = "benchmark_large", id = 4, version = 0)
@KompactReserved(stableName = "future", bitOffset = 1920, bitWidth = 16)
interface BenchmarkLargeSchema {
    @KompactArray(count = 30)
    @KompactNested(registryName = "benchmark_child", schemaId = 2, version = 0)
    @KompactField(
        stableName = "children",
        semanticType = "children",
        bitOffset = 0,
        bitWidth = 1920,
    )
    val children: BenchmarkChildSchema
}
