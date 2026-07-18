import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlinx.kover")
    id("com.vanniktech.maven.publish")
}

// Core has no runtime dependencies: masking failures surface through the neutral MaskingFailureSink SPI
// (a JVM/SLF4J implementation lives in ihawu-jackson), so commonMain stays kotlin-stdlib-only and
// compiles to non-JVM targets. See docs/adr/0007-no-logging-dependency-in-core.md.
kotlin {
    jvm()
    js { nodejs() } // a non-JVM target: proves commonMain carries no JVM-only assumptions.
    jvmToolchain(17)

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = true,
        ),
    )
}

tasks.named<Jar>("jvmJar") {
    manifest { attributes["Automatic-Module-Name"] = "org.ihawu.core" }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}

kover {
    reports {
        // HTML for humans, XML for CI tools (Codecov/Sonar can read Kover's XML). Kover measures the
        // JVM target; the branch gate below applies to it.
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

// Feed the snippets source into @sample resolution. This MUST be configured in the module's own build
// script: Dokka 2 silently drops the `samples` set configured via the root `subprojects { }` block, so
// every @sample link would otherwise render as a raw path instead of code.
pluginManager.withPlugin("org.jetbrains.dokka") {
    extensions.configure<org.jetbrains.dokka.gradle.DokkaExtension> {
        dokkaSourceSets.named("commonMain") {
            samples.from(project(":samples:snippets").file("src/main/kotlin"))
        }
    }
}
