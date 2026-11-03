package br.rhhub.infra.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.rhhub.infra.config.AppProperties;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.junit.jupiter.api.Test;

/**
 * Conecta no Kafka REAL apontado pelas variáveis de ambiente KAFKA_URL e KAFKA_API_KEY (e KAFKA_API_USER), cria os
 * tópicos que a aplicação usa e confere as partições. A URL nunca fica no código.
 *
 * <p>Cria recursos no broker, então só roda com {@code -Drhhub.kafka.setup=true}; sem isso é pulado. É idempotente:
 * tópico que já existe não é alterado. Não publica nem consome mensagens.
 *
 * <p>Uso: {@code KAFKA_URL=host:porta mvn test -Dtest=RemoteKafkaSetupTest -Drhhub.kafka.setup=true}
 */
class RemoteKafkaSetupTest {

    @Test
    void connectsCreatesTheTopicsAndChecksThePartitions() throws Exception {
        if (!"true".equals(System.getProperty("rhhub.kafka.setup"))) {
            System.out.println("KAFKA-REMOTE PULADO: passe -Drhhub.kafka.setup=true (cria tópicos no broker apontado por KAFKA_URL)");
        }
        assumeTrue("true".equals(System.getProperty("rhhub.kafka.setup")), "Sem -Drhhub.kafka.setup=true: pulado");

        String url = System.getenv("KAFKA_URL");
        String apiKey = System.getenv("KAFKA_API_KEY");
        String user = System.getenv("KAFKA_API_USER");
        var kafka = new AppProperties.Kafka(url, user == null || user.isBlank() ? "rhhub" : user, apiKey, 3,
                Duration.ofSeconds(2), "email.send", "resume.analysis");
        var props = KafkaConfig.connectionProperties(kafka);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 15_000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 25_000);
        System.out.println("KAFKA-REMOTE conectando em " + props.get("bootstrap.servers") + " ("
                + (kafka.hasApiKey() ? "SASL/PLAIN" : "sem autenticação") + ")");

        var config = new KafkaConfig();
        var topics = new KafkaTopics(kafka.emailTopic(), kafka.analysisTopic(), kafka.partitions());
        List<NewTopic> wanted = List.of(config.emailTopic(topics), config.emailDeadLetterTopic(topics),
                config.resumeAnalysisTopic(topics), config.resumeAnalysisDeadLetterTopic(topics));

        try (var admin = AdminClient.create(props)) {
            var cluster = admin.describeCluster();
            var nodes = cluster.nodes().get(30, java.util.concurrent.TimeUnit.SECONDS);
            System.out.println("KAFKA-REMOTE conectou. Broker(s) anunciado(s): "
                    + nodes.stream().map(n -> n.host() + ":" + n.port()).toList());

            for (NewTopic topic : wanted) {
                try {
                    admin.createTopics(List.of(topic)).all().get(30, java.util.concurrent.TimeUnit.SECONDS);
                    System.out.println("KAFKA-REMOTE criado: " + topic.name());
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof TopicExistsException) {
                        System.out.println("KAFKA-REMOTE já existia: " + topic.name());
                    } else {
                        throw e;
                    }
                }
            }

            var described = admin.describeTopics(wanted.stream().map(NewTopic::name).toList()).allTopicNames()
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);
            described.forEach((name, d) -> System.out.println("KAFKA-REMOTE " + name + ": " + d.partitions().size() + " partições"));
            assertThat(described).hasSize(4);
            described.values().forEach(d -> assertThat(d.partitions()).hasSize(3));
        }
    }
}
