plugins {
    kotlin("jvm") version "2.4.0" apply false
    kotlin("plugin.spring") version "2.4.0" apply false
    kotlin("plugin.serialization") version "2.4.0" apply false
    kotlin("plugin.allopen") version "2.4.0" apply false
    id("org.jetbrains.dokka") version "2.2.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.8" apply false
    id("com.vanniktech.maven.publish") version "0.30.0" apply false
}

allprojects {
    group = "org.ihawu"

    val gitTag: String? = System.getenv("GITHUB_REF_NAME")
    val isRelease = System.getenv("GITHUB_REF_TYPE") == "tag" && gitTag != null && gitTag.startsWith("v")
    // The docs site deploys from a `main` push (a branch build), where the version would otherwise
    // fall back to the SNAPSHOT default. deploy-docs.yml passes -PdocsVersion=<latest release tag>
    // so docs.ihawu.org shows the released version (e.g. 0.1.0). Publishing is unaffected.
    val docsVersion = findProperty("docsVersion") as String?
    version =
        if (isRelease) {
            gitTag.removePrefix("v")
        } else {
            docsVersion?.takeIf { it.isNotBlank() } ?: "0.1.0-SNAPSHOT"
        }

    repositories {
        mavenCentral()
    }
}

subprojects {
    val subproject = this

    // ktlint + Dokka for Kotlin modules, whether they apply the JVM or the Multiplatform plugin
    // (ihawu-core is a KMP module; the rest are JVM). The API reference must only expose published,
    // consumer-facing artifacts, so Dokka is applied to an explicit allowlist rather than every
    // module. Excluded on purpose:
    //   - samples:snippets / spring-boot-sample — sample code, not a consumable library
    //     (snippets is still wired into ihawu-core's @sample resolution below, which needs only its
    //     source path, not the Dokka plugin).
    // The starter is documented too: Dokka 2.x isolates its own classpath in a worker, so the
    // Jackson clash that blocked it under Dokka 1.9.x is gone. Its internal Spring wiring is marked
    // `internal`, so the reference shows only the extension points and config surface.
    val apiReferenceModules = setOf("ihawu-core", "ihawu-jackson", "ihawu-kotlinx", "ihawu-ktor", "ihawu-spring-boot-starter")
    listOf("org.jetbrains.kotlin.jvm", "org.jetbrains.kotlin.multiplatform").forEach { kotlinPlugin ->
        pluginManager.withPlugin(kotlinPlugin) {
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")
            if (subproject.name in apiReferenceModules) {
                pluginManager.apply("org.jetbrains.dokka")
            }
        }
    }
}

// Dokka 2.x aggregates the multi-module HTML site from the modules declared as `dokka`
// dependencies here; keep this list in sync with the allowlist above.
dependencies {
    dokka(project(":ihawu-core"))
    dokka(project(":ihawu-jackson"))
    dokka(project(":ihawu-kotlinx"))
    dokka(project(":ihawu-ktor"))
    dokka(project(":ihawu-spring-boot-starter"))
}

// Ihawu brand theming for the Dokka site (docs.ihawu.org), aligning it with ihawu.org: the shield
// mark (a customAsset named logo-icon.svg replaces Dokka's default nav logo AND favicon), the indigo
// accent palette, and a footer + homepage link back to the site. Applied to every documented module
// AND the root aggregation, since Dokka 2 themes each independently.
fun org.jetbrains.dokka.gradle.DokkaExtension.applyIhawuBranding(
    logo: java.io.File,
    stylesheet: java.io.File,
) {
    pluginsConfiguration.html {
        customAssets.from(logo)
        customStyleSheets.from(stylesheet)
        footerMessage.set("© 2026 Ihawu · ihawu.org")
        homepageLink.set("https://ihawu.org")
    }
}

val brandingLogo = rootDir.resolve("dokka/branding/logo-icon.svg")
val brandingStylesheet = rootDir.resolve("dokka/branding/ihawu.css")

dokka {
    moduleName.set("Ihawu")
    applyIhawuBranding(brandingLogo, brandingStylesheet)
}

// Per-module Dokka configuration (only fires for allowlisted modules — the rest never apply the
// plugin). Every documented module contributes its package-level doc (Dokka "Module and Package
// documentation", dokka/module.md) framing extension points vs. provided implementations, plus the
// shared brand theming. (ihawu-core's @sample wiring lives in its own build script — Dokka 2 drops
// `samples` configured from here.)
subprojects {
    pluginManager.withPlugin("org.jetbrains.dokka") {
        val moduleDoc = file("dokka/module.md")
        extensions.configure<org.jetbrains.dokka.gradle.DokkaExtension> {
            applyIhawuBranding(brandingLogo, brandingStylesheet)
            // configureEach (not named("main")) so this works for JVM modules and for ihawu-core's
            // KMP source sets (commonMain/jvmMain/jsMain), where no "main" source set exists.
            dokkaSourceSets.configureEach {
                if (moduleDoc.exists()) includes.from(moduleDoc)
            }
        }
    }
}