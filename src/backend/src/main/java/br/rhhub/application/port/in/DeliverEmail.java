package br.rhhub.application.port.in;

import br.rhhub.application.port.out.EmailMessage;

public interface DeliverEmail {

    void execute(EmailMessage message);
}
