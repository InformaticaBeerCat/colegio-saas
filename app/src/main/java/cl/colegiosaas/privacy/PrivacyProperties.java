package cl.colegiosaas.privacy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración de privacidad de la instalación.
 *
 * @param responseDays     días corridos para responder una solicitud de derechos (PRV-05). La Ley 21.719 fija
 *                         30 días hábiles prorrogables; se configura en corridos para no fallar por un feriado
 *                         mal cargado. Validar con la asesoría legal.
 * @param incidentHours    horas de referencia para notificar una brecha a la Agencia (PRV-09)
 * @param retentionEnabled si la tarea diaria de retención borra y anonimiza lo vencido (PRV-06)
 */
@ConfigurationProperties("app.privacy")
public record PrivacyProperties(@DefaultValue("30") int responseDays,
                                @DefaultValue("72") int incidentHours,
                                @DefaultValue("true") boolean retentionEnabled) {
}
