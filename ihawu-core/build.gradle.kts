plugins {
    kotlin("jvm")
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.21.1")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
