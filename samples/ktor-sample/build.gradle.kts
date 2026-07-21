plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

description = "ihawu-ktor-sample"

val ktorVersion = "3.5.1"

dependencies {
    implementation(project(":ihawu-ktor"))
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "org.ihawu.samples.ktor.ApplicationKt"
}

tasks.test {
    useJUnitPlatform()
}
