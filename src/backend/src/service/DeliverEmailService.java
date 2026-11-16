package br.rhhub.service;

import br.rhhub.application.port.in.DeliverEmail;
import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.application.port.out.EmailSender;
import reactor.core.publisher.Mono;

/** Junta a fila de e-mails com o envio: o consumer entrega a mensagem e o EmailSender manda pelo n8n. */
public class DeliverEmailService implements DeliverEmail {

    private final EmailSender sender;

    /**
     * Cria o caso de uso com o envio de e-mail que será usado (na prática, o n8n).
     */
    public DeliverEmailService(EmailSender sender) {
        this.sender = sender;
    }

    /**
     * Entrega um e-mail que veio da fila do Kafka ao envio real. Se o envio falhar, o erro sobe e o Kafka tenta de novo; esgotadas as tentativas a mensagem vai para a dead letter.
     *
     * @param message e-mail já montado (tipo, destinatário, assunto e corpo).
     */
    @Override
    public Mono<Void> execute(EmailMessage message) {
        return sender.send(message);
    }
}
