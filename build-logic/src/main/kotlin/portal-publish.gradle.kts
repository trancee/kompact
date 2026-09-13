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
    `maven-publish`
    signing
}

// --- Maven Central Portal local staging repository ---
// Artifacts are published here first, then bundled + uploaded via the Portal API.
// The bundleDir URL is derived from the project's build directory.
val mavenDir = layout.buildDirectory.dir("maven-layout")
publishing {
    repositories {
        maven {
            name = "bundleDir"
            url = mavenDir.get().asFile.toURI()
        }
    }
}

// --- Common POM metadata applied to every MavenPublication ---
// License, developer, and SCM fields are common across all kompact modules.
// Module-specific name/description are set in each module's build.gradle.kts.
afterEvaluate {
    publishing.publications.all {
        if (this is MavenPublication) {
            pom {
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

// --- PGP signing (conditional on env vars) ---
// See .env.example for the required variables. Signing only activates when
// SIGNING_KEY is present — CI uses an ephemeral PGP key for dry-run verification.
afterEvaluate {
    val signingKey = System.getenv("SIGNING_KEY")
    if (!signingKey.isNullOrBlank()) {
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

// --- Central Portal Publisher API helpers ---
// API: https://central.sonatype.com/api/v1/publisher
// Auth: Bearer base64(portalTokenUsername:portalTokenPassword)
// Mode: USER_MANAGED (default safe — validation does not auto-publish)

val portalBuildDir = layout.buildDirectory.dir("portal")

/** Bearer base64(tokenUsername:tokenPassword) — never logged. */
fun portalCredentials(): String {
    val tokenUsername = System.getenv("CENTRAL_PORTAL_TOKEN_USERNAME")
    val tokenPassword = System.getenv("CENTRAL_PORTAL_TOKEN_PASSWORD")
    if (tokenUsername.isNullOrBlank() || tokenPassword.isNullOrBlank()) {
        throw GradleException(
            "CENTRAL_PORTAL_TOKEN_USERNAME and CENTRAL_PORTAL_TOKEN_PASSWORD " +
                "environment variables are required.\n" +
                "Generate a token at https://central.sonatype.com/ → Account → User Tokens.",
        )
    }
    return Base64.getEncoder().encodeToString("$tokenUsername:$tokenPassword".toByteArray())
}

/** Deployment ID from env var or the file written by centralPortalDeploy. */
fun portalDeploymentId(): String {
    val idFile = portalBuildDir.get().asFile.resolve("deployment-id")
    return System.getenv("CENTRAL_PORTAL_DEPLOYMENT_ID")
        ?: run { if (idFile.exists()) idFile.readText().trim() else null }
        ?: throw GradleException(
            "No deployment ID found. Set CENTRAL_PORTAL_DEPLOYMENT_ID env var or run centralPortalDeploy first.",
        )
}

// Generate .md5, .sha1, .sha256, .sha512 for every published file (not for existing
// checksums — those must never be signed or re-checksummed).
tasks.register("generateChecksums") {
    group = "publication"
    description = "Generate MD5/SHA-1/SHA-256/SHA-512 checksums for all files in the Maven layout"
    dependsOn("publishAllPublicationsToBundleDirRepository")
    val md = mavenDir.get().asFile
    doLast {
        if (!md.exists()) {
            throw GradleException(
                "Maven layout not found at ${md.absolutePath}. " +
                    "Ensure publishAllPublicationsToBundleDirRepository succeeded.",
            )
        }
        val checksumExts = setOf("md5", "sha1", "sha256", "sha512")
        md.walkTopDown().forEach { file ->
            if (file.isFile && file.extension !in checksumExts) {
                val bytes = file.readBytes()
                listOf("md5", "sha1", "sha256", "sha512").forEach { algo ->
                    val digest = MessageDigest.getInstance(algo).digest(bytes)
                    val hex = digest.joinToString("") { "%02x".format(it) }
                    file.parentFile.resolve("${file.name}.$algo").writeText(hex)
                }
            }
        }
        logger.lifecycle("Checksums generated for all files in ${md.absolutePath}")
    }
}

// Assemble a Maven-layout ZIP bundle (artifacts + .asc + checksums) for upload.
tasks.register<Zip>("assembleCentralBundle") {
    group = "publication"
    description = "Assemble Maven-layout ZIP bundle for Central Portal upload"
    archiveBaseName.set("${project.name}-portal-bundle")
    archiveVersion.set("")
    destinationDirectory.set(layout.buildDirectory)
    from(mavenDir)
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
        val bundleFile =
            layout.buildDirectory
                .file("${project.name}-portal-bundle.zip")
                .get()
                .asFile
        if (!bundleFile.exists()) {
            throw GradleException("Bundle not found: ${bundleFile.absolutePath}")
        }

        val credentials = portalCredentials()
        val boundary = "----${project.name}Portal${System.currentTimeMillis()}"
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
                    "?name=${project.name}-${project.version}&publishingType=$publishingType",
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

        if (response.statusCode() == 201) {
            val deploymentId = response.body().trim()
            portalOut.resolve("deployment-id").writeText(deploymentId)
            logger.lifecycle("Central Portal deploy succeeded (HTTP ${response.statusCode()}):")
            logger.lifecycle("Deployment ID stored — track status on the Central Portal dashboard.")
        } else {
            logger.lifecycle("Central Portal deploy failed (HTTP ${response.statusCode()}):")
            logger.lifecycle("Response body redacted — check Central Portal dashboard for details.")
            throw GradleException("Portal deploy failed with HTTP ${response.statusCode()}")
        }
    }
}

// Check Central Portal deployment validation status by deployment ID.
// Parses the JSON "state" field and fails if the deployment is not yet
// VALIDATED — this makes the status task usable in CI poll loops.
// NOTE: not exercised in CI without real credentials + prior deploy.
tasks.register("centralPortalStatus") {
    group = "publication"
    description = "Check Central Portal deployment validation status (requires prior deploy)"
    doLast {
        val deploymentId = portalDeploymentId()
        val credentials = portalCredentials()
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

        // Parse the JSON "state" field — do NOT log the full response body (S1).
        val responseBody = response.body()
        val stateMatch = Regex("\"state\"\\s*:\\s*\"([^\"]+)\"").find(responseBody)
        val state = stateMatch?.groupValues?.get(1)

        logger.lifecycle("Central Portal status (HTTP ${response.statusCode()}):")
        when {
            state == null -> {
                logger.lifecycle("  ⚠️ Could not parse state — check Central Portal dashboard.")
                throw GradleException("Could not determine Portal deployment state.")
            }

            state == "VALIDATED" || state == "PUBLISHED" -> {
                logger.lifecycle("  ✅ State: $state — deployment is ready.")
            }

            state == "FAILED" -> {
                logger.lifecycle("  ❌ State: $state — deployment validation failed.")
                throw GradleException("Portal deployment validation FAILED — check Central Portal dashboard.")
            }

            else -> {
                logger.lifecycle("  ⏳ State: $state — not ready yet.")
                throw GradleException("Portal deployment not ready (state=$state). Retrying...")
            }
        }
    }
}

// Publish a validated USER_MANAGED deployment (irreversible — coordinates go live).
// NOTE: not exercised in CI (requires real credentials + prior deploy + validation pass).
tasks.register("centralPortalPublish") {
    group = "publication"
    description = "Publish a VALIDATED Central Portal deployment (IRREVERSIBLE)"
    doLast {
        val deploymentId = portalDeploymentId()
        val credentials = portalCredentials()
        val client = HttpClient.newHttpClient()
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("https://central.sonatype.com/api/v1/publisher/deployment/$deploymentId"))
                .header("Authorization", "Bearer $credentials")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        logger.lifecycle("Central Portal publish (HTTP ${response.statusCode()}):")
        if (response.statusCode() !in 200..299) {
            logger.lifecycle("  ❌ Publish failed.")
            throw GradleException("Portal publish failed with HTTP ${response.statusCode()}")
        }
        logger.lifecycle("  ✅ Publish succeeded — artifact is now on Maven Central.")
    }
}
