import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.kover")
    id("com.vanniktech.maven.publish")
}

// The kotlinx.serialization backend for Ihawu masking. Multiplatform (jvm + js), so masking runs on
// non-JVM targets too — the payoff of the serialization-neutral core (ADR 0008).
kotlin {
    explicitApi() // every public declaration is deliberate (#118, road to 1.0)
    jvm()
    js { nodejs() }
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            api(project(":ihawu-core"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        }
        jvmMain.dependencies {
            // The coroutine->thread-local bridge (ThreadLocal.asContextElement) for the Ktor path (#82).
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            // The benchmark compares this backend against the Jackson one on the same payload.
            implementation(project(":ihawu-jackson"))
            implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.22.1")
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
    manifest { attributes["Automatic-Module-Name"] = "org.ihawu.kotlinx" }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    testLogging { showStandardStreams = true } // surfaces the benchmark's println output
}

kover {
    reports {
        // Kover measures the JVM target; the branch gate below applies to it.
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
