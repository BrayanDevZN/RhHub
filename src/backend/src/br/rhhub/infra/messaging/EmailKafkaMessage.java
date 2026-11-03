package br.rhhub.infra.messaging;

import br.rhhub.application.port.out.EmailMessage;

/** Mensagem do tópico de e-mail: o resultado de uma candidatura, pronto para o n8n enviar. */
public record EmailKafkaMessage(Long resumeId, String to, String subject, String content, EmailMessage.Type type) {

    public static EmailKafkaMessage of(Long resumeId, EmailMessage message) {
        return new EmailKafkaMessage(resumeId, message.to(), message.subject(), message.content(), message.type());
    }

    public EmailMessage toEmail() {
        return new EmailMessage(to, subject, content, type);
    }
}
