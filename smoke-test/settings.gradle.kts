// Standalone multi-module build — intentionally NOT included in the root settings.gradle.kts, so the
// normal `./gradlew build` never touches it. Each module consumes the PUBLISHED org.ihawu artifacts
// from mavenLocal to prove the packaged jars work for a real consumer. Run after `publishToMavenLocal`:
//   ./gradlew -p smoke-test test                         (all adapters)
//   ./gradlew -p smoke-test :spring-boot-starter:test    (one adapter)
rootProject.name = "ihawu-smoke-tests"

include(":spring-boot-starter")
