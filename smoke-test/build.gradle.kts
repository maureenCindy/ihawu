// Shared configuration for every smoke-test module.
subprojects {
    repositories {
        mavenLocal() // the locally published org.ihawu:*:SNAPSHOT
        mavenCentral()
    }
}
