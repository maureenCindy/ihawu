package org.ihawu.spring.boot.starter.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfigurationPackages
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Registers the startup masking-policy contract validator, unless `ihawu.validate-resource-contract`
 * is `false`.
 *
 * Resource types are discovered from `ihawu.resource-base-packages` when set, otherwise from the
 * application's auto-configuration base packages.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "ihawu",
    name = ["validate-resource-contract"],
    havingValue = "true",
    matchIfMissing = true,
)
internal class MaskingContractValidationConfig {
    @Bean
    @ConditionalOnMissingBean
    fun maskingContractStartupValidator(
        provider: ResourcePolicyProvider,
        objectMapperProvider: ObjectProvider<ObjectMapper>,
        properties: IhawuProperties,
        beanFactory: ConfigurableListableBeanFactory,
    ): MaskingContractStartupValidator {
        val basePackages =
            properties.resourceBasePackages.ifEmpty {
                if (AutoConfigurationPackages.has(beanFactory)) AutoConfigurationPackages.get(beanFactory) else emptyList()
            }
        // Fall back to a plain mapper only if the app somehow has none — introspection still works for
        // the default naming strategy, and masking itself would be inert without a mapper anyway.
        val mapper = objectMapperProvider.ifAvailable ?: ObjectMapper()
        return MaskingContractStartupValidator(provider, mapper, basePackages)
    }
}
