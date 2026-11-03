package br.rhhub.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Cria os topics na subida (o broker roda com auto-create desligado) e define a política de erro:
 * 3 novas tentativas com espera crescente e, depois, a mensagem vai para o topic {@code <nome>.DLT}.
 */
@Configuration
public class KafkaConfig {

    static final String DLT_SUFFIX = ".DLT";

    @Bean
    NewTopic emailTopic(AppProperties props) {
        return topic(props.topics().email(), props.topics().partitions());
    }

    @Bean
    NewTopic emailDeadLetterTopic(AppProperties props) {
        return topic(props.topics().email() + DLT_SUFFIX, 1);
    }

    @Bean
    NewTopic resumeAnalysisTopic(AppProperties props) {
        return topic(props.topics().resumeAnalysis(), props.topics().partitions());
    }

    @Bean
    NewTopic resumeAnalysisDeadLetterTopic(AppProperties props) {
        return topic(props.topics().resumeAnalysis() + DLT_SUFFIX, 1);
    }

    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> operations) {
        // A DLT tem 1 partição, então toda mensagem morta vai para a partição 0.
        var recoverer = new DeadLetterPublishingRecoverer(operations,
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, 0));
        var backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxElapsedTime(15_000L);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    private static NewTopic topic(String name, int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
}
