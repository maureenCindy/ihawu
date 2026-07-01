plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.spring")
    `java-library`
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

description = "ihawu-spring-boot-starter"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xemit-jvm-type-annotations", "-java-parameters")
    }
}

kapt {
    arguments {
        // kapt runs the Spring configuration processor without src/main/resources on its CLASS_OUTPUT
        // path, so it can't find additional-spring-configuration-metadata.json by default. Point it at
        // the resource root explicitly so the handwritten metadata (e.g. defaultValue) is merged in.
        arg(
            "org.springframework.boot.configurationprocessor.additionalMetadataLocations",
            "$projectDir/src/main/resources",
        )
    }
}

dependencies {
    compileOnly("org.springframework.security:spring-security-core")
    compileOnly("org.springframework.boot:spring-boot-starter-web")

    // api, not implementation: core types (@IhawuResource, FieldPolicy, MaskingStrategy) are part of
    // the starter's public surface, so consumers compile against them with only the starter on the path.
    api(project(":ihawu-core"))
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter")

    kapt("org.springframework.boot:spring-boot-configuration-processor")
    kapt("org.springframework.boot:spring-boot-autoconfigure-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
    archiveBaseName.set("ihawu-spring-boot-starter")
    manifest {
        // For Java 9+ module path compatibility
        attributes["Automatic-Module-Name"] = "org.ihawu.spring.boot.starter"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
