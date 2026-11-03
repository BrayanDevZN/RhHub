package br.rhhub.infrastructure.mail;

import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.application.port.out.MailSender;
import br.rhhub.infrastructure.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Envia HTML por SMTP. Com app.mail.enabled=false só registra no log (dev e testes). */
@Component
public class SmtpMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailSender.class);

    private final JavaMailSender javaMail;
    private final AppProperties.Mail config;

    public SmtpMailSender(JavaMailSender javaMail, AppProperties props) {
        this.javaMail = javaMail;
        this.config = props.mail();
    }

    @Override
    public void send(EmailMessage message) {
        if (!config.enabled()) {
            log.info("E-mail desligado (app.mail.enabled=false): '{}' não enviado a {}", message.subject(), message.to());
            return;
        }
        try {
            MimeMessage mime = javaMail.createMimeMessage();
            var helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom(config.from());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.body(), true);
            javaMail.send(mime);
            log.info("E-mail '{}' enviado a {}", message.subject(), message.to());
        } catch (MessagingException e) {
            throw new IllegalStateException("Falha ao montar o e-mail para " + message.to(), e);
        }
    }
}
