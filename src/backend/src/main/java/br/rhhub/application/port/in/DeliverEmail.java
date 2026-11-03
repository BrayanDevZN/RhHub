package br.rhhub.application.port.in;

import br.rhhub.application.port.out.EmailMessage;

/** Entrega um e-mail que veio da fila. Falhas sobem para a fila tentar de novo. */
public interface DeliverEmail {

    void execute(EmailMessage message);
}
