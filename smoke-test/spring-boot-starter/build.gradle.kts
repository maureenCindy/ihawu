plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.spring") version "2.4.0"
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

// Repositories are configured once for all smoke-test modules in the root build.gradle.kts.

// Version of the published artifacts to consume; inherited from smoke-test/gradle.properties.
val ihawuVersion = providers.gradleProperty("ihawuVersion").getOrElse("0.1.0-SNAPSHOT")

dependencies {
    // ONLY the starter — no project references, no explicit core dependency. This proves the packaged
    // starter pulls ihawu-core transitively (the `api` dependency), so @IhawuResource compiles here.
    implementation("org.ihawu:ihawu-spring-boot-starter:$ihawuVersion")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
