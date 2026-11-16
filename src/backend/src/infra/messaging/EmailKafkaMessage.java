package br.rhhub.infra.messaging;

import br.rhhub.application.port.out.EmailMessage;

/** Mensagem do tópico de e-mail: o resultado de uma candidatura, pronto para o n8n enviar. */
public record EmailKafkaMessage(Long resumeId, String to, String subject, String content, EmailMessage.Type type) {

    /**
     * Monta a mensagem da fila a partir do e-mail e do id da candidatura (que serve de chave).
     */
    public static EmailKafkaMessage of(Long resumeId, EmailMessage message) {
        return new EmailKafkaMessage(resumeId, message.to(), message.subject(), message.content(), message.type());
    }

    /**
     * Converte a mensagem lida da fila de volta em um e-mail pronto para enviar.
     */
    public EmailMessage toEmail() {
        return new EmailMessage(to, subject, content, type);
    }
}
