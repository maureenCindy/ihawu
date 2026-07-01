plugins {
    kotlin("jvm") version "2.4.0" apply false
    kotlin("plugin.spring") version "2.4.0" apply false
    id("org.jetbrains.dokka") version "1.9.20"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.8" apply false
}

allprojects {
    group = "org.ihawu"

    val gitTag: String? = System.getenv("GITHUB_REF_NAME")
    val isRelease = System.getenv("GITHUB_REF_TYPE") == "tag" && gitTag != null && gitTag.startsWith("v")
    version = if (isRelease) gitTag.removePrefix("v") else "1.0.0-SNAPSHOT"

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

    // Only the published library modules (ihawu-*) get Maven publishing/signing; samples never do.
    if (subproject.name.startsWith("ihawu-")) {
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            pluginManager.apply("maven-publish")
            pluginManager.apply("signing")
            // Mandatory generation of sources JAR for Maven Central compliance
            extensions.configure<JavaPluginExtension> {
                withSourcesJar()
            }
            // Configure Maven Central POM metadata
            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("mavenJava") {
                        from(components["java"])
                        pom {
                            name.set(subproject.name)
                            description.set("Ihawu Policy Enforcement and Dynamic Data Masking Component")
                            url.set("https://github.com/maureenCindy/ihawu")
                            licenses {
                                license {
                                    name.set("The Apache License, Version 2.0")
                                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                                }
                            }
                            developers {
                                developer {
                                    id.set("maureencindy")
                                    name.set("Maureen")
                                    email.set("maureencindy01@gmail.com")
                                }
                            }

                            scm {
                                connection.set("scm:git:git://github.com/maureenCindy/ihawu.git")
                                developerConnection.set("scm:git:ssh://git@github.com/maureenCindy/ihawu.git")
                                url.set("https://github.com/maureenCindy/ihawu")
                            }
                        }
                    }
                }

                repositories {
                    maven {
                        name = "Sonatype"
                        val releasesRepoUrl = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                        val snapshotsRepoUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                        url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)

                        credentials {
                            username = System.getenv("ORG_GRADLE_PROJECT_sonatypeUsername")
                            password = System.getenv("ORG_GRADLE_PROJECT_sonatypePassword")
                        }
                    }
                }
            }

            // GPG signing via GitHub secrets
            extensions.configure<SigningExtension> {
                val signingKey = System.getenv("GPG_PRIVATE_KEY")
                val signingPassword = System.getenv("GPG_PASSPHRASE")

                if (!signingKey.isNullOrBlank()) {
                    useInMemoryPgpKeys(signingKey, signingPassword)
                    sign(extensions.getByType<PublishingExtension>().publications["mavenJava"])
                }
            }
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