@file:OptIn(
    kotlinx.validation.ExperimentalBCVApi::class,
    org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class,
)

import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Zip
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.util.Base64

plugins {
    alias(libs.plugins.kmp)
    alias(libs.plugins.bcv)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlinPowerAssert)
    `maven-publish`
    `signing`
}

kotlin {
    // Ticket 13: pin JVM target to 21 LTS so BCV (ASM 9.8 / v0.18.2) can parse the
    // emitted class files on hosts running JDK 25 (Kotlin 2.4.20 otherwise emits v69).
    jvm {
        // Ticket 13: pin JVM target to 21 LTS so BCV (ASM 9.8 / v0.18.2) can parse the
        // emitted class files on hosts running JDK 25 (Kotlin 2.4.20 otherwise emits v69).
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain =
            getByName("commonMain") {
                compilerOptions {
                    // KT-61573: expect/actual value classes are stable in 2.4; silence the Beta warning.
                    freeCompilerArgs.addAll("-Xexpect-actual-classes")
                }
            }
        val commonTest =
            getByName("commonTest") {
                dependencies {
                    // kotlin("test") is version-aligned to the Kotlin Gradle plugin (catalog'd).
                    implementation(kotlin("test"))
                }
            }
        val jvmMain = getByName("jvmMain")
        val jvmTest =
            getByName("jvmTest") {
                dependencies {
                    // kotlin("test") (from commonTest) provides kotlin.test assertions
                    // and JUnit 4 transitively on the JVM. No explicit JUnit dep needed.
                }
            }
        // Shared iOS source set (Ticket 03 expect/actual value class).
        // gradle.properties: kotlin.mpp.applyDefaultHierarchyTemplate=false so this
        // intermediate is the sole iosMain (avoids the default-template conflict).
        val iosMain = create("iosMain")
        iosMain.dependsOn(commonMain)
        getByName("iosArm64Main") { dependsOn(iosMain) }
        getByName("iosSimulatorArm64Main") { dependsOn(iosMain) }
    }
}

// --- SKIE (iOS/Swift interop improvements) ---
// SKIE (co.touchlab.skie) provides KMP-to-Swift interop via a Gradle plugin
// extension. SKIE 0.10.14 (latest) supports Kotlin up to 2.4.10; Kotlin 2.4.20
// is not yet supported. SKIE is declared as `apply false` in the root
// build.gradle.kts and is NOT yet applied to this module. When a compatible
// version is released, add `alias(libs.plugins.skie)` to this module's plugins
// block and enable the desired features:
//   - Sealed class → Swift enum conversion (e.g. KompactDecodeError)
//   - Value class Swift-friendliness (e.g. BooleanResult, ByteResult, etc.)

// --- Kover (100 % line + branch coverage on the JVM target) ---
// Kover measures coverage from the jvmTest execution via its JVM TI agent
// (not JaCoCo). For KMP projects, coverage is collected from the compiled JVM
// bytecode (commonMain + jvmMain). The verify rules enforce strict 100 % thresholds.
// See: kotlinx.kover.gradle.plugin.dsl (KoverProjectExtension → reports → total/verify)
//
// Kover filters exclude test-only utility classes that intentionally contain
// never-called constructors and assertion branches — these are coverage-pinning
// scaffolding (see JvmCoveragePinning.java), not production logic. The JVM TI
// agent tracks INVOKEVIRTUAL (synthetic @JvmInline getters), not GETFIELD, so
// the Java scaffolding forces method-level coverage.
kover {
    reports {
        filters {
            excludes {
                classes("ch.trancee.kompact.runtime.JvmCoveragePinning*")
            }
        }
        total {
            xml {
                onCheck.set(true)
            }
            html {
                onCheck.set(true)
            }
        }
        verify {
            rule {
                minBound(100, CoverageUnit.LINE)
            }
            rule {
                minBound(100, CoverageUnit.BRANCH)
            }
        }
    }
}

// --- Kotlin Power-Assert (enhanced test failure messages) ---
// Power-Assert transforms assertion calls in test source sets, rendering
// sub-expressions and intermediate values in failure messages.
// The compilationFilter defaults to TESTS (commonTest, jvmTest, iosTest).
powerAssert {
    functions =
        listOf(
            "kotlin.assert",
            "kotlin.require",
            "kotlin.requireNotNull",
            "kotlin.check",
            "kotlin.checkNotNull",
            "kotlin.test.assertTrue",
            "kotlin.test.assertFalse",
            "kotlin.test.assertEquals",
            "kotlin.test.assertNotEquals",
            "kotlin.test.assertNull",
            "kotlin.test.assertNotNull",
            "kotlin.test.assertContentEquals",
            "kotlin.test.assertContentNotEquals",
            "kotlin.test.assertContains",
            "kotlin.test.assertNotContains",
            "kotlin.test.assertFails",
            "kotlin.test.assertFailsWith",
        )
}

// Ticket 13: BCV 0.18.2 — lock the public ABI for common + each Kotlin/Native target.
// strictValidation makes klibApiCheck fail on hosts that can't compile every
// target (e.g. iOS klibs on Linux) instead of silently inferring the ABI —
// which produces false greens. macOS validates all iOS targets for real;
// Linux is expected to run jvmApiCheck only (see ci.yml / docs/ci.md).
apiValidation {
    klib {
        enabled = true
        strictValidation = true
    }
}

// Exclude JVM-coverage-pinning scaffolding (JvmCoveragePinning.java) from the
// published JVM JAR. It lives in jvmMain so KMP compiles it before the Kotlin
// test sources that reflectively exercise @JvmInline getters (JaCoCo/Kover
// tracks INVOKEVIRTUAL but not GETFIELD). It is package-private (excluded from
// BCV ABI) and Kover-filtered — it must not ship in the Maven artifact.
// (Ticket 10 cross-platform testing model: Kover 100 % line + branch gate.)
tasks.named<Jar>("jvmJar") {
    exclude("ch/trancee/kompact/runtime/JvmCoveragePinning*.class")
    exclude("ch/trancee/kompact/runtime/JvmCoveragePinning*.java")
}

// --- Maven Central Portal publishing ---
// Route: Portal Publisher API (direct integration — no third-party plugin).
// Signing: PGP key injected via environment variables (never in source).
// See the maven-central-publishing skill for the full workflow.

// KGP creates jvmSourcesJar and auto-attaches it to the JVM publication.
// We add commonMain sources to it so the sources JAR is complete for Central.
val commonMainSource = kotlin.sourceSets.getByName("commonMain")
afterEvaluate {
    val task = tasks.findByName("jvmSourcesJar")
    if (task != null) {
        task.withGroovyBuilder {
            invokeMethod("from", commonMainSource.kotlin)
        }
    }
}

// Javadoc JAR for the JVM target — Central requires a Javadoc artifact for JVM publications.
// Dokka V1/V2 dokkaHtml/dokkaJavadoc tasks are incompatible with KMP + JDK 25.
// Maven Central accepts minimal/empty Javadoc JARs for KMP projects (standard practice).
val dokkaJavadocJar =
    tasks.register<Jar>("dokkaJavadocJar") {
        archiveClassifier.set("javadoc")
        from(rootProject.file("README.md"))
    }

// POM metadata applied to every auto-created KMP publication (root + per-target).
// publications holds Publication (supertype), so cast to MavenPublication for pom{} .
publishing {
    publications {
        all {
            if (this is MavenPublication) {
                pom {
                    name.set("Kompact")
                    description.set(
                        "Zero-allocation bit-stream pack/unpack primitives and generated model views " +
                            "for Kotlin Multiplatform.",
                    )
                    url.set("https://github.com/trancee/kompact")
                    licenses {
                        license {
                            name.set("Apache License 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("trancee")
                            name.set("Philipp Grosswiler")
                            email.set("philipp.grosswiler@gmail.com")
                        }
                    }
                    scm {
                        url.set("https://github.com/trancee/kompact")
                        connection.set("scm:git:git://github.com/trancee/kompact.git")
                        developerConnection.set("scm:git:ssh://git@github.com/trancee/kompact.git")
                    }
                }
            }
        }
    }
    repositories {
        // Local staging directory — never touches Maven Central.
        maven {
            name = "bundleDir"
            url =
                layout.buildDirectory
                    .dir("maven-layout")
                    .get()
                    .asFile
                    .toURI()
        }
    }
}

// Attach the Dokka Javadoc JAR to the JVM publication and configure PGP signing.
// KGP auto-attaches jvmSourcesJar to the JVM publication; we only add dokkaJavadocJar.
// KGP creates KMP publications during evaluation, so this runs after.
afterEvaluate {
    publishing.publications.all {
        if (this is MavenPublication && name == "jvm") {
            artifact(dokkaJavadocJar.get())
        }
    }

    val signingKey = System.getenv("SIGNING_KEY")
    if (signingKey != null && signingKey.isNotBlank()) {
        signing {
            useInMemoryPgpKeys(
                System.getenv("SIGNING_KEY_ID"),
                signingKey,
                System.getenv("SIGNING_PASSWORD"),
            )
            sign(publishing.publications)
        }
    }
}

// --- Custom Central Portal Publisher API tasks ---
// API: https://central.sonatype.com/api/v1/publisher
// Auth: Bearer base64(portalTokenUsername:portalTokenPassword)
// Mode: USER_MANAGED (default safe — validation does not auto-publish)

val portalBuildDir = layout.buildDirectory.dir("portal")

// Generate .md5, .sha1, .sha256, .sha512 for every published file (not for existing
// checksums — those must never be signed or re-checksummed).
tasks.register("generateChecksums") {
    group = "publication"
    description = "Generate MD5/SHA-1/SHA-256/SHA-512 checksums for all files in the Maven layout"
    dependsOn("publishAllPublicationsToBundleDirRepository")
    val mavenDir =
        layout.buildDirectory
            .get()
            .dir("maven-layout")
            .asFile
    doLast {
        if (!mavenDir.exists()) {
            throw GradleException(
                "Maven layout not found at ${mavenDir.absolutePath}. " +
                    "Ensure publishAllPublicationsToBundleDirRepository succeeded.",
            )
        }
        val checksumExts = setOf("md5", "sha1", "sha256", "sha512")
        mavenDir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension !in checksumExts) {
                val bytes = file.readBytes()
                listOf("md5", "sha1", "sha256", "sha512").forEach { algo ->
                    val digest = MessageDigest.getInstance(algo).digest(bytes)
                    val hex = digest.joinToString("") { "%02x".format(it) }
                    file.parentFile.resolve("${file.name}.$algo").writeText(hex)
                }
            }
        }
        logger.lifecycle("Checksums generated for all files in ${mavenDir.absolutePath}")
    }
}

// Assemble a Maven-layout ZIP bundle (artifacts + .asc + checksums) for upload.
tasks.register<Zip>("assembleCentralBundle") {
    group = "publication"
    description = "Assemble Maven-layout ZIP bundle for Central Portal upload"
    archiveBaseName.set("kompact-portal-bundle")
    archiveVersion.set("")
    destinationDirectory.set(layout.buildDirectory)
    from(layout.buildDirectory.dir("maven-layout"))
    dependsOn("generateChecksums")
}

// Upload the bundle to the Sonatype Central Portal (USER_MANAGED staging).
// Credentials: CENTRAL_PORTAL_TOKEN_USERNAME + CENTRAL_PORTAL_TOKEN_PASSWORD (env vars)
// These are Portal user tokens, NOT the interactive account password.
tasks.register("centralPortalDeploy") {
    group = "publication"
    description = "Upload bundle to Central Portal (USER_MANAGED) — requires CENTRAL_PORTAL_TOKEN_USERNAME/PASSWORD"
    dependsOn("assembleCentralBundle")
    doLast {
        val tokenUsername = System.getenv("CENTRAL_PORTAL_TOKEN_USERNAME")
        val tokenPassword = System.getenv("CENTRAL_PORTAL_TOKEN_PASSWORD")
        if (tokenUsername.isNullOrBlank() || tokenPassword.isNullOrBlank()) {
            throw GradleException(
                "CENTRAL_PORTAL_TOKEN_USERNAME and CENTRAL_PORTAL_TOKEN_PASSWORD " +
                    "environment variables are required.\n" +
                    "Generate a token at https://central.sonatype.com/ → Account → User Tokens.",
            )
        }

        val bundleFile =
            layout.buildDirectory
                .file("kompact-portal-bundle.zip")
                .get()
                .asFile
        if (!bundleFile.exists()) {
            throw GradleException("Bundle not found: ${bundleFile.absolutePath}")
        }

        val credentials =
            Base64
                .getEncoder()
                .encodeToString("$tokenUsername:$tokenPassword".toByteArray())
        val boundary = "----KompactPortal${System.currentTimeMillis()}"
        val publishingType = System.getenv("CENTRAL_PORTAL_PUBLISHING_TYPE") ?: "USER_MANAGED"

        val body =
            ByteArrayOutputStream().use { out ->
                out.write(("--$boundary\r\n").toByteArray())
                out.write(
                    (
                        "Content-Disposition: form-data; name=\"bundle\"; " +
                            "filename=\"${bundleFile.name}\"\r\n"
                    ).toByteArray(),
                )
                out.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
                out.write(bundleFile.readBytes())
                out.write("\r\n".toByteArray())
                out.write(("--$boundary--\r\n".toByteArray()))
                out.toByteArray()
            }

        val portalUrl =
            URI.create(
                "https://central.sonatype.com/api/v1/publisher/upload" +
                    "?name=kompact-${project.version}&publishingType=$publishingType",
            )
        val client = HttpClient.newHttpClient()
        val request =
            HttpRequest
                .newBuilder()
                .uri(portalUrl)
                .header("Authorization", "Bearer $credentials")
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        val portalOut = portalBuildDir.get().asFile
        portalOut.mkdirs()
        portalOut.resolve("deploy-response").writeText(response.body())

        val responseBody = response.body().trim()
        if (response.statusCode() == 201 && responseBody.isNotEmpty()) {
            portalOut.resolve("deployment-id").writeText(responseBody)
            logger.lifecycle("Central Portal deploy response (HTTP ${response.statusCode()}):")
            logger.lifecycle("Deployment ID: $responseBody — track with: ./gradlew centralPortalStatus")
        } else {
            logger.lifecycle("Central Portal deploy failed (HTTP ${response.statusCode()}):")
            logger.lifecycle(response.body())
            throw GradleException("Portal deploy failed with HTTP ${response.statusCode()}: ${response.body()}")
        }
    }
}

// Check Central Portal deployment validation status by deployment ID.
tasks.register("centralPortalStatus") {
    group = "publication"
    description = "Check Central Portal deployment validation status"
    doLast {
        val deploymentId =
            System.getenv("CENTRAL_PORTAL_DEPLOYMENT_ID")
                ?: run {
                    val idFile = portalBuildDir.get().asFile.resolve("deployment-id")
                    if (idFile.exists()) idFile.readText().trim() else null
                }
        if (deploymentId.isNullOrBlank()) {
            throw GradleException(
                "No deployment ID found. Set CENTRAL_PORTAL_DEPLOYMENT_ID env var or run centralPortalDeploy first.",
            )
        }

        val tokenUsername = System.getenv("CENTRAL_PORTAL_TOKEN_USERNAME")
        val tokenPassword = System.getenv("CENTRAL_PORTAL_TOKEN_PASSWORD")
        if (tokenUsername.isNullOrBlank() || tokenPassword.isNullOrBlank()) {
            throw GradleException(
                "CENTRAL_PORTAL_TOKEN_USERNAME and CENTRAL_PORTAL_TOKEN_PASSWORD " +
                    "environment variables are required.",
            )
        }

        val credentials =
            Base64
                .getEncoder()
                .encodeToString("$tokenUsername:$tokenPassword".toByteArray())
        val client = HttpClient.newHttpClient()
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("https://central.sonatype.com/api/v1/publisher/status?id=$deploymentId"))
                .header("Authorization", "Bearer $credentials")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        logger.lifecycle("Central Portal deployment status for $deploymentId (HTTP ${response.statusCode()}):")
        logger.lifecycle(response.body())
    }
}

// Publish a validated USER_MANAGED deployment (irreversible — coordinates go live).
tasks.register("centralPortalPublish") {
    group = "publication"
    description = "Publish a VALIDATED Central Portal deployment (IRREVERSIBLE)"
    doLast {
        val deploymentId =
            System.getenv("CENTRAL_PORTAL_DEPLOYMENT_ID")
                ?: run {
                    val idFile = portalBuildDir.get().asFile.resolve("deployment-id")
                    if (idFile.exists()) idFile.readText().trim() else null
                }
        if (deploymentId.isNullOrBlank()) {
            throw GradleException("No deployment ID found.")
        }

        val tokenUsername = System.getenv("CENTRAL_PORTAL_TOKEN_USERNAME")
        val tokenPassword = System.getenv("CENTRAL_PORTAL_TOKEN_PASSWORD")
        if (tokenUsername.isNullOrBlank() || tokenPassword.isNullOrBlank()) {
            throw GradleException(
                "CENTRAL_PORTAL_TOKEN_USERNAME and CENTRAL_PORTAL_TOKEN_PASSWORD " +
                    "environment variables are required.",
            )
        }

        val credentials =
            Base64
                .getEncoder()
                .encodeToString("$tokenUsername:$tokenPassword".toByteArray())
        val client = HttpClient.newHttpClient()
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("https://central.sonatype.com/api/v1/publisher/deployment/$deploymentId"))
                .header("Authorization", "Bearer $credentials")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val responseBody = response.body().takeIf { it.isNotBlank() } ?: "(no body — expected for 204)"
        logger.lifecycle("Central Portal publish response (HTTP ${response.statusCode()}): $responseBody")
    }
}

// F-002 boundary tests allocate 256 MiB buffers; give the JVM test fork headroom.
// Power-Assert's expression diagram renderer needs additional headroom for
// the 256 MiB ByteArray captured in assertion expressions.
tasks.withType<Test>().configureEach {
    maxHeapSize = "4g"
}

// Ticket 13: align Java compilation target with Kotlin's JVM_21 so that
// jvmTest Java sources compile consistently (KGP emits v61; the Java compiler
// on JDK 25 defaults to v69 otherwise, triggering KGP's cross-task validation).
tasks.withType<JavaCompile>().configureEach {
    if (name.contains("JvmMain") || name.contains("JvmTest")) {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}
