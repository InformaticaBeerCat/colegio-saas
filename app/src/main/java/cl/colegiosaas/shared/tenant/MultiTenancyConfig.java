package cl.colegiosaas.shared.tenant;

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conecta {@link TenantContext} con el multi-tenant por columna de Hibernate ({@code @TenantId}). */
@Configuration(proxyBeanMethods = false)
class MultiTenancyConfig {

    @Bean
    HibernatePropertiesCustomizer schoolTenantResolver() {
        CurrentTenantIdentifierResolver<Long> resolver = new CurrentTenantIdentifierResolver<>() {
            @Override
            public Long resolveCurrentTenantIdentifier() {
                return TenantContext.idForHibernate();
            }

            @Override
            public boolean validateExistingCurrentSessions() {
                return false;
            }

            @Override
            public boolean isRoot(Long schoolId) {
                return schoolId == TenantContext.PLATFORM;
            }
        };
        return properties -> properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
