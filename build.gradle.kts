plugins {
    kotlin("jvm") version "2.4.0" apply false
    kotlin("plugin.spring") version "2.4.0" apply false
    id("org.jetbrains.dokka") version "1.9.20"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.8" apply false
    id("com.vanniktech.maven.publish") version "0.30.0" apply false
}

allprojects {
    group = "org.ihawu"

    val gitTag: String? = System.getenv("GITHUB_REF_NAME")
    val isRelease = System.getenv("GITHUB_REF_TYPE") == "tag" && gitTag != null && gitTag.startsWith("v")
    version = if (isRelease) gitTag.removePrefix("v") else "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    val subproject = this

    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        pluginManager.apply("org.jlleitschuh.gradle.ktlint")
        // The API reference must only expose published, consumer-facing artifacts, so Dokka is
        // applied to an explicit allowlist rather than every module. Excluded on purpose:
        //   - samples:snippets / spring-boot-sample — sample code, not a consumable library
        //     (snippets is still wired into ihawu-core's @sample resolution below, which needs
        //     only its source path, not the Dokka plugin).
        //   - ihawu-spring-boot-starter — a real consumer module, but Dokka 1.9.20 embeds an older
        //     Jackson that clashes with Spring Boot's and crashes on any Spring classpath. Add it
        //     here once the Dokka 2.x upgrade (issue #51) removes that clash.
        val apiReferenceModules = setOf("ihawu-core")
        if (subproject.name in apiReferenceModules) {
            pluginManager.apply("org.jetbrains.dokka")
        }
    }
}

// Dokka multi-module output directory
tasks.withType<org.jetbrains.dokka.gradle.DokkaMultiModuleTask>().configureEach {
    outputDirectory.set(layout.buildDirectory.dir("dokka/htmlMultiModule"))
    // The aggregation writes into the output dir without wiping it first, and the root project has
    // no base plugin so `./gradlew clean` never removes root `build/`. Together that lets a prior
    // run's packages survive a rebuild (e.g. stale pre-migration com.ihawu.* alongside org.ihawu.*).
    // Wipe the output ourselves so a local build can never publish stale API-reference pages.
    doFirst { delete(outputDirectory) }
}

// Feed samples source into Dokka's @sample resolution for ihawu-core
subprojects {
    if (name == "ihawu-core") {
        // Package-level docs (Dokka "Module and Package documentation") that frame the policy
        // package's contracts vs. provided implementations at the top of its reference page.
        val moduleDoc = file("dokka/module.md")
        plugins.withType<org.jetbrains.dokka.gradle.DokkaPlugin> {
            tasks.withType<org.jetbrains.dokka.gradle.AbstractDokkaLeafTask>().configureEach {
                dokkaSourceSets.configureEach {
                    samples.from(project(":samples:snippets").file("src/main/kotlin"))
                    includes.from(moduleDoc)
                }
            }
        }
    }
}