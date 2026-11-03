package br.rhhub.application.port.out;

public interface MailSender {

    void send(EmailMessage message);
}
