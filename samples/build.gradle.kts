plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":ihawu-core"))
    // Add ihawu-spring-boot-starter when Spring-specific samples are needed
    // implementation(project(":ihawu-spring-boot-starter"))
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
