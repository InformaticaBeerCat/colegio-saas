package cl.colegiosaas.shared.mail;

/**
 * Correo a enviar. Los servicios lo publican como evento y {@link MailDispatcher} lo envía recién
 * cuando la transacción se confirma: si algo falla y se revierte, no sale un correo con un enlace inválido.
 */
public record OutgoingMail(String to, String subject, String body) {
}
