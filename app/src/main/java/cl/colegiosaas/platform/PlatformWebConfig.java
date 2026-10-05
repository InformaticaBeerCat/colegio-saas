package cl.colegiosaas.platform;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class PlatformWebConfig implements WebMvcConfigurer {

    private final SchoolRepository schools;

    PlatformWebConfig(SchoolRepository schools) {
        this.schools = schools;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new FeatureGuard(schools));
    }
}
