package br.rhhub.application.port.out;

import reactor.core.publisher.Mono;

/** Enfileira a candidatura para análise. A mensagem leva só o id, que também é a chave no Kafka. */
public interface ResumeAnalysisPublisher {

    Mono<Void> publish(Long resumeId);
}
