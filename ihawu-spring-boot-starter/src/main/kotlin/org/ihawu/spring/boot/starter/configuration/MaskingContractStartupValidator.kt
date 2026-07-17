package org.ihawu.spring.boot.starter.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import org.ihawu.core.annotation.IhawuResource
import org.ihawu.core.exception.IhawuCoreException
import org.ihawu.core.serialization.MaskingContractValidator
import org.ihawu.core.serialization.MaskingContractViolation
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.util.ClassUtils

/**
 * Checks every configured masking policy against its resource at startup, and fails the application
 * context (hard-fail; ADR 0005) if any policy is unenforceable — one that would emit schema-invalid
 * output (`REDACT` on a non-nullable non-`String` field, or `HIDE` on a non-nullable field), or that
 * targets a field the resource does not have (so it masks nothing). It surfaces a misconfiguration
 * during development rather than as a fail-closed omission — or a silent leak — at request time.
 *
 * Discovery is a classpath scan of [basePackages] for [IhawuResource] types; the check itself is core's
 * [MaskingContractValidator], run with the application's [objectMapper] so the verdict matches exactly
 * what serialization will do (including `@JsonProperty` renames and any naming strategy). Only the
 * statically-known rules from [provider] are checked — a dynamic resolver's rules are not visible here
 * and remain guarded by the runtime fail-closed backstop.
 *
 * A policy whose `resource` matches no scanned type is skipped (it cannot be type-checked); the runtime
 * still fails such a resource closed if it is ever serialized.
 */
internal class MaskingContractStartupValidator(
    private val provider: ResourcePolicyProvider,
    private val objectMapper: ObjectMapper,
    private val basePackages: List<String>,
) : SmartInitializingSingleton {
    override fun afterSingletonsInstantiated() {
        val resourceTypes = scanResourceTypes()
        val violations =
            provider.getResourcePolicies().flatMap { policy ->
                val type = resourceTypes[policy.resourceName] ?: return@flatMap emptyList()
                val fields =
                    policy.roleFieldPolicies
                        ?.values
                        ?.flatten()
                        .orEmpty()
                MaskingContractValidator.validate(objectMapper, policy.resourceName, type, fields)
            }
        if (violations.isNotEmpty()) {
            throw IhawuCoreException(formatMessage(violations))
        }
        logger.debug("Ihawu masking policy contract validated against {} resource type(s)", resourceTypes.size)
    }

    /** resource name -> its [IhawuResource] class, discovered by scanning [basePackages]. */
    private fun scanResourceTypes(): Map<String, Class<*>> {
        if (basePackages.isEmpty()) {
            logger.warn(
                "Ihawu found no base packages to scan for @IhawuResource types; masking policy contract " +
                    "validation is skipped. Set ihawu.resource-base-packages if resources are not under the " +
                    "application's auto-configuration packages.",
            )
            return emptyMap()
        }
        val scanner = ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(AnnotationTypeFilter(IhawuResource::class.java))
        val loader = javaClass.classLoader
        return basePackages
            .asSequence()
            .flatMap { scanner.findCandidateComponents(it).asSequence() }
            .mapNotNull { it.beanClassName }
            .distinct()
            .map { ClassUtils.forName(it, loader) }
            .associateBy { it.getAnnotation(IhawuResource::class.java).name }
    }

    private fun formatMessage(violations: List<MaskingContractViolation>): String =
        buildString {
            append("Ihawu masking policy cannot be enforced:")
            violations.forEach { append("\n  - ${it.resource}.${it.field}: ${it.reason}") }
            append("\nFix the policy or the resource declaration, or set ihawu.validate-resource-contract=false. See ADR 0005.")
        }

    private companion object {
        private val logger = LoggerFactory.getLogger(MaskingContractStartupValidator::class.java)
    }
}
