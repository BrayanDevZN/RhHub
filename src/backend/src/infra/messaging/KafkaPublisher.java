package br.rhhub.infra.messaging;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Publica e só termina quando o broker confirma (acks=all). O KafkaProducer.send bloqueia a thread que o chama enquanto
 * busca os metadados do tópico, por isso roda em thread própria (boundedElastic) e nunca na do servidor web.
 */
@Component
public class KafkaPublisher {

    private final KafkaTemplate<String, String> kafka;

    /**
     * Cria o publicador sobre o KafkaTemplate.
     */
    public KafkaPublisher(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    /**
     * Publica uma mensagem e só termina quando o broker confirma. Roda em thread própria (boundedElastic) porque o envio do cliente Kafka bloqueia enquanto busca informações do tópico.
     *
     * @param key chave da mensagem (o id da candidatura); mensagens com a mesma chave vão para a mesma partição, mantendo a ordem.
     */
    public Mono<Void> send(String topic, String key, String payload) {
        return Mono.defer(() -> Mono.fromFuture(kafka.send(topic, key, payload)))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
