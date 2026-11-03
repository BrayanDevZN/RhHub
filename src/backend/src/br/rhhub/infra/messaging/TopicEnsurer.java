package br.rhhub.infra.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recria os tópicos que sumirem. O Kafka daqui não cria tópico sozinho (auto.create.topics.enable=false) e quem os cria
 * é o app, uma vez, na subida. Se o Kafka reiniciar e perder os dados (sem volume no Railway isso acontece a cada
 * deploy dele), os consumers ficam recebendo UNKNOWN_TOPIC_OR_PARTITION para sempre. A cada minuto confere e recria o
 * que faltar; é idempotente. Só no serviço consumer (KAFKA_CONSUMERS_ENABLED=true).
 */
@Component
@ConditionalOnProperty(name = "app.kafka.consumers-enabled", havingValue = "true", matchIfMissing = true)
public class TopicEnsurer {

    private static final Logger log = LoggerFactory.getLogger(TopicEnsurer.class);

    private final KafkaAdmin admin;

    public TopicEnsurer(KafkaAdmin admin) {
        this.admin = admin;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    void ensure() {
        try {
            admin.initialize();
        } catch (RuntimeException e) {
            log.warn("Não foi possível conferir os tópicos do Kafka: {}", e.toString());
        }
    }
}
