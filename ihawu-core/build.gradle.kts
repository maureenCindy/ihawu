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
