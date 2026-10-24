# How to add Kompact to a project

Add the Kompact runtime to a Kotlin/JVM, Android, or Kotlin Multiplatform
module. To generate views from schemas, also apply the code-generation plugin.
The latest Maven Central release is `0.11.0`.

## Add the published runtime

For a JVM or Android module, add Maven Central and the runtime dependency:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("ch.trancee.kompact:kompact:0.11.0")
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
                implementation("ch.trancee.kompact:kompact:0.11.0")
            }
        }
    }
}
```

Add only the targets that your application uses. Use the root coordinate
`ch.trancee.kompact:kompact` for every target. Gradle reads the module metadata
and selects the matching platform variant, so do not write target-specific
artifact names. The runtime publishes variants for JVM, Android, iOS Arm64,
iOS Simulator Arm64, and Android Native Arm64.

## Add the published code-generation plugin

The plugin `ch.trancee.kompact.codegen`, the runtime, and the KSP processor
are published at `0.11.0`. The plugin accepts only the JVM, Android JVM,
iOS Arm64, iOS Simulator Arm64, and Android Native Arm64 targets.

The schema examples in these guides need version `0.10.0` or later.

1. Add Maven Central to plugin and dependency resolution in
   `settings.gradle.kts`:

   ```kotlin
   pluginManagement {
       repositories {
           gradlePluginPortal()
           mavenCentral()
       }
   }

   dependencyResolutionManagement {
       repositories {
           mavenCentral()
       }
   }
   ```

2. Apply the plugin after Kotlin Multiplatform, and add the runtime to
   `commonMain`:

   ```kotlin
   plugins {
       kotlin("multiplatform") version "2.4.21"
       id("ch.trancee.kompact.codegen") version "0.11.0"
   }

   kotlin {
       jvm()
       iosArm64()
       iosSimulatorArm64()
       androidNativeArm64()

       sourceSets {
           commonMain {
               dependencies {
                   implementation("ch.trancee.kompact:kompact:0.11.0")
               }
           }
       }
   }
   ```

Do not also apply the standard KSP plugin to the same Kompact schemas.

## Try the development snapshot

To try the latest changes on the default branch, publish the current
checkout's `0.11.0` runtime, KSP processor, and code-generation plugin
to Maven Local from a clone of this repository.

### Use the snapshot runtime

1. Publish the runtime from the repository root:

   ```bash
   ./gradlew :kompact:publishToMavenLocal
   ```

2. In the consuming build, put Maven Local before Maven Central and use the
   snapshot coordinate:

   ```kotlin
   repositories {
       mavenLocal()
       mavenCentral()
   }

   dependencies {
       implementation("ch.trancee.kompact:kompact:0.11.0")
   }
   ```

### Enable schema generation

1. Publish all three snapshot components from the repository root:

   ```bash
   ./gradlew \
     :kompact:publishToMavenLocal \
     :kompact-ksp:publishToMavenLocal \
     :kompact-gradle-plugin:publishToMavenLocal
   ```

2. In the consuming build's `settings.gradle.kts`, add Maven Local to
   `pluginManagement` so that Gradle finds the plugin marker:

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

3. Apply the plugin after Kotlin Multiplatform, and add the runtime to
   `commonMain`:

   ```kotlin
   plugins {
       kotlin("multiplatform") version "2.4.21"
       id("ch.trancee.kompact.codegen") version "0.11.0"
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
                   implementation("ch.trancee.kompact:kompact:0.11.0")
               }
           }
       }
   }
   ```

Add the other targets that your application uses. The snapshot plugin adds one
generated common source per schema to `commonMain` and does not restrict
Kotlin targets. The runtime must still publish a variant for each target that
you add. Do not also apply the standard KSP plugin to the same Kompact
schemas.

If the plugin is applied before Kotlin Multiplatform, the build fails with
`Apply the Kotlin Multiplatform plugin before ch.trancee.kompact.codegen.`

Use Kotlin from the same minor line as the plugin's tested version. For the
snapshot, that is `2.4.x`, tested with `2.4.21`. Another patch release logs a
warning. A different minor line fails the build before generation. See
[ADR-0012](../adr/0012-kotlin-minor-line-compatibility.md).

For a complete schema, see
[How to define a framed model](define-framed-schema.md).

## Verify the dependency

Run this JVM program. It writes a frame, checks its bytes, and reads one field
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

If the snapshot does not resolve, check that `mavenLocal()` is in both the
`pluginManagement` repositories and the dependency repositories. To go back to
Maven Central, remove `mavenLocal()` and use the published `0.11.0` runtime and
plugin.

## Next steps

- [Build your first frame](../getting-started.md)
- [Define a fixed-layout model](define-message.md)
- [Define a framed model](define-framed-schema.md)
- [Find a public signature](../api-reference.md)
