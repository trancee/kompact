# How to consume Kompact from another project

Goal: add `ch.trancee.kompact:kompact` to a Kotlin or Kotlin
Multiplatform project so you can call `KompactWriter`, `KompactRuntime`,
and the typed result value classes.

The Maven coordinates are `ch.trancee.kompact:kompact:0.1.0-SNAPSHOT`.
The artifact publishes per-target klibs (`-iosarm64`, `-iossimulatorarm64`)
and a JVM jar via standard `maven-publish`.

## 1. Install the snapshot locally

The first release to Maven Central is not yet cut (the Portal
namespace, PGP key, and user token still need authorization — see
`.scratch/kompact-spec/issues/14-maven-central-publishing.md` for the
release contract). Until then, publish the snapshot to your local
Maven repository:

```bash
# From the kompact repository root:
./gradlew :kompact:publishToMavenLocal
```

This produces the per-target artifacts under `~/.m2/repository/`.

## 2. Plain Kotlin / JVM project (`build.gradle.kts`)

```kotlin
repositories {
    mavenCentral()
    mavenLocal()    // for the 0.1.0-SNAPSHOT until first Central release
}

dependencies {
    implementation("ch.trancee.kompact:kompact:0.1.0-SNAPSHOT")
}
```

The runtime lives in package `ch.trancee.kompact.runtime`. The
`VehicleTelemetry` example lives in `ch.trancee.kompact.generated`,
and the convenience `Kompact.Result` namespace lives in
`ch.trancee.kompact`.

## 3. Kotlin Multiplatform project

```kotlin
plugins {
    kotlin("multiplatform") version "2.4.20"
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("ch.trancee.kompact:kompact:0.1.0-SNAPSHOT")
            }
        }
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}
```

The KMP artifact publishes a metadata `.module` file that resolves
the JVM jar for `jvm`, the iOS klib for `iosArm64`, and the iOS klib
for `iosSimulatorArm64` automatically. You do not need to specify
target-specific coordinates.

## 4. Android project (Gradle)

```kotlin
android {
    namespace = "com.example.myapp"
    compileSdk = 34
    defaultConfig { minSdk = 24 }
}

repositories {
    google()
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("ch.trancee.kompact:kompact:0.1.0-SNAPSHOT")
}
```

Android consumes the JVM artifact. The runtime is plain Kotlin with
no Android-specific dependencies, so the JVM jar runs unchanged on
Android 24+.

## 5. Version catalog (Gradle 7.4+)

For multi-module builds, pin the version in `gradle/libs.versions.toml`:

```toml
[versions]
kompact = "0.1.0-SNAPSHOT"

[libraries]
kompact = { module = "ch.trancee.kompact:kompact", version.ref = "kompact" }
```

Then in the module:

```kotlin
dependencies {
    implementation(libs.kompact)
}
```

## 6. Verify the install

A one-line smoke test that should print `0xA5 0x40` and three
decoded values:

```kotlin
import ch.trancee.kompact.generated.VehicleTelemetry
import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.runtime.KompactWriter
import ch.trancee.kompact.runtime.ScalarType

fun main() {
    val w = KompactWriter()
    w.writeScalar(ScalarType.of(4,  signed = false), 5L)
    w.writeScalar(ScalarType.of(10, signed = false), 10L)
    w.writeBool(true)
    val raw = w.build()
    println(raw.toHexString())                                  // → "a540"
    val tel = VehicleTelemetry(raw)
    println("${tel.batteryStatus} ${tel.speed} ${tel.isMalfunctioning}")
    // → 5 10 true
}
```

If this prints the expected output, the install is correct. If it
fails to resolve, double-check that `mavenLocal()` is in your
`repositories` block (the snapshot is *not* on Maven Central yet).

## 7. Optional: enable the preview annotations

`@KompactModel` and `@KompactField` carry `@KompactPreview`
(`@RequiresOptIn(level = WARNING)`). To use them in your own code
to annotate a model class, opt in per file:

```kotlin
@file:OptIn(KompactPreview::class)
package your.package
```

The runtime classes (`KompactWriter`, `KompactRuntime`, the result
value classes) are not preview API — no opt-in is needed for them.

## 8. Common pitfalls

- **`mavenLocal()` not declared.** The snapshot lives in `~/.m2/`,
  not on Maven Central. Without `mavenLocal()` in your
  `repositories`, Gradle reports `Could not find
  ch.trancee.kompact:kompact:0.1.0-SNAPSHOT`.
- **Wrong target coordinate on KMP.** Use
  `ch.trancee.kompact:kompact` (the root artifact), not
  `ch.trancee.kompact:kompact-jvm` or `kompact-iosarm64`. The
  metadata file selects the right per-target artifact for the
  current source set.
- **Android minSdk too low.** The runtime is pure Kotlin with no
  Android dependencies — but the `value class` representation uses
  `@JvmInline` on JVM, which requires Kotlin 1.5+ and is fine on
  Android 24+. Older minSdk values still work; the runtime does not
  bump the floor.
- **Preview warnings in your build log.** `@KompactPreview` is
  `Level.WARNING`, not `Level.ERROR`, so the build does not fail —
  but the warning is printed for every file that uses the preview
  API without an opt-in. Add `@file:OptIn(KompactPreview::class)` to
  silence them.

## What's next

- Try the [getting started tutorial](../getting-started.md) end to end.
- Define your own message:
  [`define-message.md`](define-message.md).
- Send it over BLE: [`integrate-ble.md`](integrate-ble.md).
