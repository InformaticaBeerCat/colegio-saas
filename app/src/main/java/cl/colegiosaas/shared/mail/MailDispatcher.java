package cl.colegiosaas.shared.mail;

import cl.colegiosaas.shared.web.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envía los correos por SMTP si está configurado ({@code SPRING_MAIL_HOST}); si no, los escribe en el
 * log, que en desarrollo basta para copiar el enlace. En el compose de desarrollo, Mailpit los captura.
 */
@Component
class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final AppProperties app;

    MailDispatcher(ObjectProvider<JavaMailSender> mailSender, AppProperties app) {
        this.mailSender = mailSender;
        this.app = app;
    }

    @TransactionalEventListener(fallbackExecution = true)
    void send(OutgoingMail mail) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("SMTP no configurado. Correo para {}:\n{}\n{}", mail.to(), mail.subject(), mail.body());
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(app.mailFrom());
        message.setTo(mail.to());
        message.setSubject(mail.subject());
        message.setText(mail.body());
        sender.send(message);
    }
}
