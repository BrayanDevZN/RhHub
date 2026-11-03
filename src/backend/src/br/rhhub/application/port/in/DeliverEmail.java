package br.rhhub.application.port.in;

import br.rhhub.application.port.out.EmailMessage;
import reactor.core.publisher.Mono;

/** Entrega um e-mail que veio da fila. Falhas sobem para a fila tentar de novo. */
public interface DeliverEmail {

    Mono<Void> execute(EmailMessage message);
}
