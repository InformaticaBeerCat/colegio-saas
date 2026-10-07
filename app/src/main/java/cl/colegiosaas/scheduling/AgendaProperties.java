package cl.colegiosaas.scheduling;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Reglas de la agenda pública de citas (AGE-03, AGE-04).
 *
 * @param minNotice    anticipación mínima para reservar: nadie reserva para dentro de diez minutos
 * @param horizonDays  cuántos días hacia adelante se ofrecen horas
 * @param reminderLead cuánto antes de la cita se envía el recordatorio
 * @param holidaysUrl  fuente oficial de feriados de Chile; {@code {year}} se reemplaza por el año
 */
@ConfigurationProperties("app.agenda")
public record AgendaProperties(@DefaultValue("2h") Duration minNotice,
                               @DefaultValue("30") int horizonDays,
                               @DefaultValue("24h") Duration reminderLead,
                               @DefaultValue("https://apis.digital.gob.cl/fl/feriados/{year}") String holidaysUrl) {
}
