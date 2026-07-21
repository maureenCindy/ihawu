import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.kover")
    id("com.vanniktech.maven.publish")
}

// The Ktor server adapter for Ihawu masking. Ktor server plugins are JVM-only, so this is a plain
// kotlin("jvm") module built on the multiplatform kotlinx backend (ADR 0009).
val ktorVersion = "3.5.1"
val micrometerVersion = "1.13.6"

dependencies {
    api(project(":ihawu-kotlinx"))
    api("io.ktor:ktor-server-core:$ktorVersion")
    api("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    api("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    api("io.ktor:ktor-server-auth:$ktorVersion")

    // Optional: the Micrometer failure-metrics sink is only referenced when the app supplies a registry.
    compileOnly("io.micrometer:micrometer-core:$micrometerVersion")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("io.micrometer:micrometer-core:$micrometerVersion")
}

mavenPublishing {
    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = true,
        ),
    )
}

tasks.named<Jar>("jar") {
    manifest { attributes["Automatic-Module-Name"] = "org.ihawu.ktor" }
}

kotlin {
    explicitApi() // every public declaration is deliberate (#118, road to 1.0)
    jvmToolchain(17)
}

kover {
    reports {
        // HTML for humans, XML for CI tools (Codecov/Sonar can read Kover's XML).
        total {
            html { onCheck = false }
            xml { onCheck = false }

            verify {
                rule {
                    bound {
                        minValue = 90
                        coverageUnits = CoverageUnit.BRANCH
                    }
                }
            }
        }
    }
}

tasks.named("check") { dependsOn("koverVerify") }

tasks.test {
    useJUnitPlatform()
}
