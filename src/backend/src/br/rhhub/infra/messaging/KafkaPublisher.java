package br.rhhub.infra.messaging;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Publica de forma assíncrona e só termina quando o broker confirma (acks=all). */
@Component
public class KafkaPublisher {

    private final KafkaTemplate<String, String> kafka;

    public KafkaPublisher(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    public Mono<Void> send(String topic, String key, String payload) {
        return Mono.defer(() -> Mono.fromFuture(kafka.send(topic, key, payload))).then();
    }
}
