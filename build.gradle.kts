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
        // Dokka 1.9.20 embeds an older Jackson that clashes with Spring Boot's, so it crashes on any
        // module with Spring on the classpath. Skip those (the starter and the sample app); the pure
        // library and snippets still generate API docs.
        if (subproject.name != "ihawu-spring-boot-starter" && subproject.name != "spring-boot-sample") {
            pluginManager.apply("org.jetbrains.dokka")
        }
    }
}

// Dokka multi-module output directory
tasks.withType<org.jetbrains.dokka.gradle.DokkaMultiModuleTask>().configureEach {
    outputDirectory.set(layout.buildDirectory.dir("dokka/htmlMultiModule"))
}

// Feed samples source into Dokka's @sample resolution for ihawu-core
subprojects {
    if (name == "ihawu-core") {
        plugins.withType<org.jetbrains.dokka.gradle.DokkaPlugin> {
            tasks.withType<org.jetbrains.dokka.gradle.AbstractDokkaLeafTask>().configureEach {
                dokkaSourceSets.configureEach {
                    samples.from(project(":samples:snippets").file("src/main/kotlin"))
                }
            }
        }
    }
}