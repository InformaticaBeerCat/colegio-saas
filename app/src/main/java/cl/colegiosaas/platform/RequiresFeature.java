package cl.colegiosaas.platform;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un controlador (o método) que pertenece a un módulo contratable (CFG-07). Si el colegio no
 * tiene el módulo activo, la página no existe: responde 404, tanto en el sitio como en el panel.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresFeature {

    Feature value();
}
