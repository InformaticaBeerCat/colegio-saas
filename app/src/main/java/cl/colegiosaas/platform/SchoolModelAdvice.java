package cl.colegiosaas.platform;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Pone el nombre del colegio en todas las vistas (encabezados, títulos). En la fase 3 lo reemplaza
 * un contexto de sitio en caché que además trae el diseño publicado.
 */
@ControllerAdvice
class SchoolModelAdvice {

    private final SchoolRepository schools;

    SchoolModelAdvice(SchoolRepository schools) {
        this.schools = schools;
    }

    @ModelAttribute("schoolName")
    String schoolName() {
        return schools.findSingleton().map(School::getName).orElse("Colegio");
    }
}
