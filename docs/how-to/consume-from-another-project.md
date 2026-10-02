# How to add Kompact to a project

Add the runtime dependency to a Kotlin/JVM, Android, or Kotlin Multiplatform
module. The latest Maven Central release is `0.4.0`.

## Use the published runtime

For a JVM or Android module, add Maven Central and the runtime dependency:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("ch.trancee.kompact:kompact:0.4.0")
}
```

For Kotlin Multiplatform, put the same dependency in `commonMain`:

```kotlin
kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation("ch.trancee.kompact:kompact:0.4.0")
            }
        }
    }
}
```

Add only the targets your application uses. The root KMP coordinate lets
Gradle select a published platform variant from module metadata; do not guess
target-specific artifact names. A target must be published for the version
you consume.

## Try the development snapshot

The `0.5.0-SNAPSHOT` runtime, schema processor, and KMP code-generation plugin
are not available from Maven Central. To try runtime-only changes, publish the
runtime from the repository root:

```bash
./gradlew :kompact:publishToMavenLocal
```

Then add Maven Local to your dependency repositories before Maven Central and
use the snapshot coordinate:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("ch.trancee.kompact:kompact:0.5.0-SNAPSHOT")
}
```

### Enable schema generation

For generated schemas, publish all three snapshot components to Maven Local:

```bash
./gradlew \
  :kompact:publishToMavenLocal \
  :kompact-ksp:publishToMavenLocal \
  :kompact-gradle-plugin:publishToMavenLocal
```

Make the plugin marker available through `pluginManagement` in the consuming
build's `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
```

Then apply the plugin after Kotlin Multiplatform and add the runtime to
`commonMain`:

```kotlin
plugins {
    kotlin("multiplatform") version "2.4.20"
    id("ch.trancee.kompact.codegen") version "0.5.0-SNAPSHOT"
}

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation("ch.trancee.kompact:kompact:0.5.0-SNAPSHOT")
            }
        }
    }
}
```

Add any other supported targets required by your application. The plugin
supports JVM, Android JVM, iOS Arm64, iOS Simulator Arm64, and Android Native
Arm64. Do not also apply standard target-specific KSP processing to the same
Kompact schemas. See [How to define a framed schema](define-framed-schema.md)
for a complete schema example.

## Verify the dependency

This small JVM program writes a frame, checks its bytes, and reads one field
back:

```kotlin
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.KompactWriter
import ch.trancee.kompact.runtime.ScalarType

fun main() {
    val writer = KompactWriter()
    writer.writeScalar(ScalarType.of(4, signed = false), 5L)
    writer.writeScalar(ScalarType.of(10, signed = false), 10L)
    writer.writeBool(true)

    val bytes = writer.build()
    check(bytes.contentEquals(byteArrayOf(0xA5.toByte(), 0x40.toByte())))
    check(KompactRuntime.readScalar(bytes, 4, ScalarType.of(10, signed = false)).getOrThrow() == 10)
}
```

If the snapshot does not resolve, check that `mavenLocal()` is configured for
both plugin resolution and dependencies. For Maven Central, remove
`mavenLocal()` and use the published `0.4.0` version.

## Next steps

- [Build your first frame](../getting-started.md)
- [Define a fixed-layout model](define-message.md)
- [Define a sequential framed schema](define-framed-schema.md)
- [Find a public signature](../api-reference.md)
