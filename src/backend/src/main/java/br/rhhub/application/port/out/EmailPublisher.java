package br.rhhub.application.port.out;

/** Enfileira o e-mail no topic email.send. O envio de fato é feito pelo consumer. */
public interface EmailPublisher {

    void publish(EmailMessage message);
}
