package br.rhhub.application.usecase;

import br.rhhub.application.port.in.DeliverEmail;
import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.application.port.out.MailSender;

public class DeliverEmailService implements DeliverEmail {

    private final MailSender mailSender;

    public DeliverEmailService(MailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void execute(EmailMessage message) {
        mailSender.send(message);
    }
}
