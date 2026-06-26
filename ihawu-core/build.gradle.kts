import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlinx.kover")
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.21.1")
    testImplementation(kotlin("test"))
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
                        // Placeholder floor. Module-local coverage reads low because #20's
                        // serialization tests live in the samples module, so they are not
                        // counted here. Tracked follow-up: pick a coverage-measurement strategy
                        // (aggregate report, or relocate serialization tests) and raise to 90.
                        minValue = 20
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
