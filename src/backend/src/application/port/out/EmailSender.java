package br.rhhub.application.port.out;

import reactor.core.publisher.Mono;

/** Porta de saída: envia um e-mail. A implementação real chama o n8n (infra/email). */
public interface EmailSender {

    /**
     * Envia o e-mail. Erro DomainException.Invalid se a mensagem for inválida (nada é enviado) e
     * EmailException se o envio falhar.
     */
    Mono<Void> send(EmailMessage message);

    /** Nome do envio ativo, para log (n8n, fixed ou none). */
    String name();

    class EmailException extends RuntimeException {
        public EmailException(String message) {
            super(message);
        }

        public EmailException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
