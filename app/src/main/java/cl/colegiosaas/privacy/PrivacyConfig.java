package cl.colegiosaas.privacy;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PrivacyProperties.class)
class PrivacyConfig {
}
