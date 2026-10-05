package cl.colegiosaas.setup;

import cl.colegiosaas.shared.web.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
class SetupConfig {

    private static final Logger log = LoggerFactory.getLogger(SetupConfig.class);

    /** Antes que el filtro de Spring Security (orden -100). */
    @Bean
    FilterRegistrationBean<SetupRedirectFilter> setupRedirectFilter(InstallationService installation) {
        FilterRegistrationBean<SetupRedirectFilter> registration = new FilterRegistrationBean<>(new SetupRedirectFilter(installation));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 50);
        return registration;
    }

    @EventListener(ApplicationReadyEvent.class)
    void announceSetup(ApplicationReadyEvent event) {
        InstallationService installation = event.getApplicationContext().getBean(InstallationService.class);
        if (!installation.isInstalled()) {
            SetupToken token = event.getApplicationContext().getBean(SetupToken.class);
            AppProperties app = event.getApplicationContext().getBean(AppProperties.class);
            log.warn("""

                    ==============================================================
                     Instalación pendiente. Abre {} e ingresa este token:
                       {}
                    ==============================================================""", app.url("/setup"), token.value());
        }
    }
}
