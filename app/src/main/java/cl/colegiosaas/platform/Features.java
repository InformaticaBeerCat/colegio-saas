package cl.colegiosaas.platform;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pregunta si un módulo contratable está activo (CFG-07), para mostrar u ocultar partes de una página. Para
 * páginas completas se usa {@link RequiresFeature}. Las plantillas lo usan como {@code @features.on('EVENTS')}.
 */
@Component("features")
public class Features {

    private final SchoolRepository schools;

    Features(SchoolRepository schools) {
        this.schools = schools;
    }

    @Transactional(readOnly = true)
    public boolean on(Feature feature) {
        return schools.findSingleton().map(school -> school.hasFeature(feature)).orElse(false);
    }

    public boolean on(String feature) {
        return on(Feature.valueOf(feature));
    }
}
