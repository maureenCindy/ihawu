import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlinx.kover")
    id("com.vanniktech.maven.publish")
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.21.1")
    api("org.slf4j:slf4j-api:2.0.18")
    testImplementation(kotlin("test"))
    // No logging binding dependency: test sources provide an in-memory SLF4JServiceProvider
    // (org.ihawu.core.common.RecordingServiceProvider) so log assertions need no extra library.
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
    manifest { attributes["Automatic-Module-Name"] = "org.ihawu.core" }
}

kotlin {
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

// Feed the snippets source into @sample resolution. This MUST be configured in the module's own
// build script: Dokka 2 silently drops `samples` set via the root `subprojects { }` block (unlike
// `includes`, which propagates fine from there), so every @sample link renders as a raw path
// instead of code. Configuring it here on the module makes all @sample links resolve.
pluginManager.withPlugin("org.jetbrains.dokka") {
    extensions.configure<org.jetbrains.dokka.gradle.DokkaExtension> {
        dokkaSourceSets.named("main") {
            samples.from(project(":samples:snippets").file("src/main/kotlin"))
        }
    }
}
