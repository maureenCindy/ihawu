plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    // JMH generates subclasses of @State classes, which Kotlin makes final by default; allopen opens them.
    kotlin("plugin.allopen")
    id("me.champeau.jmh") version "0.7.3"
}

// JMH benchmarks comparing the masking backends (issue #102, ADR 0008). NOT a published artifact —
// no maven-publish, no Dokka, no kover. `check` compiles the benchmarks so they cannot rot, but the
// `jmh` task itself is manual: run `./gradlew :benchmark:jmh` (see README.md).
description = "ihawu-benchmark"

dependencies {
    jmh(project(":ihawu-jackson"))
    jmh(project(":ihawu-kotlinx"))
    jmh("com.fasterxml.jackson.module:jackson-module-kotlin:2.22.1")
    // ihawu-kotlinx keeps kotlinx-serialization as an implementation detail; the benchmark uses it directly.
    jmh("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

kotlin {
    jvmToolchain(17)
}

jmh {
    // One profiled run yields both throughput and allocation (gc.alloc.rate.norm).
    profilers = listOf("gc")
    fork = 2
    warmupIterations = 5
    warmup = "1s"
    iterations = 5
    timeOnIteration = "1s"
    resultFormat = "TEXT"
}

// Keep the benchmark sources compiling in every build without running them.
tasks.named("check") { dependsOn("jmhClasses") }
