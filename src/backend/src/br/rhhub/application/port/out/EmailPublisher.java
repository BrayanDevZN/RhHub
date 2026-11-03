package br.rhhub.application.port.out;

import reactor.core.publisher.Mono;

/** Enfileira o e-mail do resultado de uma candidatura. A chave no Kafka é o id da candidatura. */
public interface EmailPublisher {

    Mono<Void> publish(Long resumeId, EmailMessage message);
}
