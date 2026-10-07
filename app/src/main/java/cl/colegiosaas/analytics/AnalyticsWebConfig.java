package cl.colegiosaas.analytics;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class AnalyticsWebConfig implements WebMvcConfigurer {

    private final PageViewCounter counter;
    private final AnalyticsProperties properties;

    AnalyticsWebConfig(PageViewCounter counter, AnalyticsProperties properties) {
        this.counter = counter;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (properties.enabled()) {
            registry.addInterceptor(new PageViewInterceptor(counter));
        }
    }
}
