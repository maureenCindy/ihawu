plugins {
    kotlin("jvm") version "2.4.0" apply false
    kotlin("plugin.spring") version "2.4.0" apply false
    id("org.jetbrains.dokka") version "2.2.0"
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
        // The starter is documented too: Dokka 2.x isolates its own classpath in a worker, so the
        // Jackson clash that blocked it under Dokka 1.9.x is gone. Its internal Spring wiring is
        // marked `internal`, so the reference shows only the extension points and config surface.
        val apiReferenceModules = setOf("ihawu-core", "ihawu-spring-boot-starter")
        if (subproject.name in apiReferenceModules) {
            pluginManager.apply("org.jetbrains.dokka")
        }
    }
}

// Dokka 2.x aggregates the multi-module HTML site from the modules declared as `dokka`
// dependencies here; keep this list in sync with the allowlist above.
dependencies {
    dokka(project(":ihawu-core"))
    dokka(project(":ihawu-spring-boot-starter"))
}

// Per-module Dokka configuration (only fires for allowlisted modules — the rest never apply the
// plugin). Every documented module contributes its package-level doc (Dokka "Module and Package
// documentation", dokka/module.md) framing extension points vs. provided implementations;
// ihawu-core additionally feeds the snippets source into @sample resolution.
subprojects {
    pluginManager.withPlugin("org.jetbrains.dokka") {
        val moduleDoc = file("dokka/module.md")
        val samplesDir = project(":samples:snippets").file("src/main/kotlin")
        val isCore = name == "ihawu-core"
        extensions.configure<org.jetbrains.dokka.gradle.DokkaExtension> {
            dokkaSourceSets.named("main") {
                if (moduleDoc.exists()) includes.from(moduleDoc)
                if (isCore) samples.from(samplesDir)
            }
        }
    }
}