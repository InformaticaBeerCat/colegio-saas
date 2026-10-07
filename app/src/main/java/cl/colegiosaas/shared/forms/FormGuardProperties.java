package cl.colegiosaas.shared.forms;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Antispam de los formularios públicos (COM-08).
 *
 * @param minFillTime tiempo mínimo entre mostrar el formulario y enviarlo: un bot lo envía al instante
 * @param maxAge      un formulario abierto hace más que esto se vuelve a cargar (evita reutilizar el sello)
 * @param maxPerIp    envíos aceptados por IP y formulario dentro de {@code ipWindow}
 * @param ipWindow    ventana para contar los envíos de una IP
 */
@ConfigurationProperties("app.forms")
public record FormGuardProperties(@DefaultValue("3s") Duration minFillTime,
                                  @DefaultValue("2d") Duration maxAge,
                                  @DefaultValue("5") int maxPerIp,
                                  @DefaultValue("10m") Duration ipWindow) {
}
