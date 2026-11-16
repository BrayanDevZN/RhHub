package br.rhhub.service;

import br.rhhub.application.port.in.DeliverEmail;
import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.application.port.out.EmailSender;
import reactor.core.publisher.Mono;

/** Junta a fila de e-mails com o envio: o consumer entrega a mensagem e o EmailSender manda pelo n8n. */
public class DeliverEmailService implements DeliverEmail {

    private final EmailSender sender;

    public DeliverEmailService(EmailSender sender) {
        this.sender = sender;
    }

    @Override
    public Mono<Void> execute(EmailMessage message) {
        return sender.send(message);
    }
}
