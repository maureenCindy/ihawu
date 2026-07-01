package com.ihawu.sample

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Runnable Spring Boot app demonstrating Ihawu masking end to end: a secured endpoint returns an
 * `@IhawuResource` DTO that each role sees with different field visibility. The starter on the
 * classpath auto-configures masking; this app only supplies the policy rules and security.
 */
@SpringBootApplication
class SampleApplication

fun main(args: Array<String>) {
    runApplication<SampleApplication>(*args)
}
