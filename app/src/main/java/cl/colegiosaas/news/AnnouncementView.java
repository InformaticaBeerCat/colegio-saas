package cl.colegiosaas.news;

import java.time.Instant;

/**
 * Comunicado listo para el sitio, armado dentro de la transacción (destinatarios y adjunto ya
 * resueltos: la vista no abre sesión con la base).
 *
 * @param audience       "Todo el colegio", "1° Básico, 2° Básico" o "1° Básico A"
 * @param attachmentHref enlace al PDF; nulo si no tiene
 */
public record AnnouncementView(
        String title,
        String body,
        Instant publishedAt,
        String audience,
        String attachmentHref,
        String attachmentName,
        long attachmentBytes) {
}
