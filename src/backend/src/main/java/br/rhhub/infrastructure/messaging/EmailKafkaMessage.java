package br.rhhub.infrastructure.messaging;

import br.rhhub.application.port.out.EmailMessage;

/** Mensagem do topic email.send. */
public record EmailKafkaMessage(String to, String subject, String body) {

    public static EmailKafkaMessage from(EmailMessage message) {
        return new EmailKafkaMessage(message.to(), message.subject(), message.body());
    }

    public EmailMessage toDomain() {
        return new EmailMessage(to, subject, body);
    }
}
