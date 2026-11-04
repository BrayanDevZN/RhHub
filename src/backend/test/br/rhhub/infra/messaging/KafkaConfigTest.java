package br.rhhub.infra.messaging;

import br.rhhub.testing.UnitTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.domain.exception.DomainException;
import br.rhhub.infra.TestSupport;
import br.rhhub.infra.config.AppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

@UnitTest
class KafkaConfigTest {

    private static AppProperties.Kafka kafka(String url, String user, String key) {
        return new AppProperties.Kafka(url, user, key, 3, Duration.ofSeconds(2), "email.send", "resume.analysis");
    }

    @Test
    void withoutUrlOrKeyConnectsLocallyWithoutAuthentication() {
        var props = KafkaConfig.connectionProperties(kafka("none", "rhhub", null));
        assertThat(props).containsEntry("bootstrap.servers", "localhost:9092");
        assertThat(props).doesNotContainKeys("security.protocol", "sasl.mechanism", "sasl.jaas.config");
    }

    @Test
    void apiKeyTurnsOnSaslPlain() {
        String key = "k".repeat(32);
        var props = KafkaConfig.connectionProperties(kafka("kafka.railway.internal:9092", "rhhub", key));
        assertThat(props).containsEntry("bootstrap.servers", "kafka.railway.internal:9092")
                .containsEntry("security.protocol", "SASL_PLAINTEXT").containsEntry("sasl.mechanism", "PLAIN");
        assertThat(props.get("sasl.jaas.config").toString())
                .isEqualTo("org.apache.kafka.common.security.plain.PlainLoginModule required username=\"rhhub\" password=\""
                        + key + "\";");
    }

    @Test
    void blankNoneOrNullKeyCountsAsNoKey() {
        for (String key : new String[] {"", "  ", "none", "NONE", "null"}) {
            assertThat(KafkaConfig.connectionProperties(kafka("none", "rhhub", key))).as("[%s]", key)
                    .doesNotContainKey("security.protocol");
        }
    }

    @Test
    void unsafeCharactersInCredentialsAreRejected() {
        assertThatThrownBy(() -> KafkaConfig.connectionProperties(kafka("none", "rhhub", "abc\" ; evil=\"x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KafkaConfig.connectionProperties(kafka("none", "u\"ser", "abc")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsBothTopicsAndTheirDeadLetterTopicsWithThreePartitions() {
        var config = new KafkaConfig();
        var topics = new KafkaTopics("email.send", "resume.analysis", 3);
        var created = java.util.List.of(config.emailTopic(topics), config.emailDeadLetterTopic(topics),
                config.resumeAnalysisTopic(topics), config.resumeAnalysisDeadLetterTopic(topics));

        assertThat(created).extracting(t -> t.name())
                .containsExactly("email.send", "email.send.DLT", "resume.analysis", "resume.analysis.DLT");
        assertThat(created).allSatisfy(t -> assertThat(t.numPartitions()).isEqualTo(3));
    }

    @Test
    void attemptsFollowTheTopicAndSkipRetriesForUselessErrors() {
        var topics = new KafkaTopics("email.send", "resume.analysis", 3);
        assertThat(KafkaConfig.attemptsFor("resume.analysis", new IllegalStateException("x"), topics)).isEqualTo(4);
        assertThat(KafkaConfig.attemptsFor("email.send", new IllegalStateException("x"), topics)).isEqualTo(3);
        assertThat(KafkaConfig.attemptsFor("email.send",
                new RuntimeException(new DomainException.Invalid("to inválido")), topics)).isEqualTo(1);
        assertThat(KafkaConfig.attemptsFor("resume.analysis",
                new RuntimeException(new ObjectMapperFailure().failure()), topics)).isEqualTo(1);
    }

    /** Só para ter uma JsonProcessingException de verdade. */
    private static final class ObjectMapperFailure {
        JsonProcessingException failure() {
            try {
                new ObjectMapper().readTree("{nao-json");
                return null;
            } catch (JsonProcessingException e) {
                return e;
            }
        }
    }

    @Test
    void consumersAreOnByDefaultAndTheApiTurnsThemOff() {
        var env = new org.springframework.mock.env.MockEnvironment();
        assertThat(KafkaConfig.consumersEnabled(env)).isTrue();
        env.setProperty("app.kafka.consumers-enabled", "false");
        assertThat(KafkaConfig.consumersEnabled(env)).isFalse();
        var factory = new KafkaConfig().kafkaListenerContainerFactory(
                org.mockito.Mockito.mock(org.springframework.kafka.core.ConsumerFactory.class),
                org.mockito.Mockito.mock(org.springframework.kafka.listener.CommonErrorHandler.class), env);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(factory, "autoStartup")).isEqualTo(false);
        env.setProperty("app.kafka.consumers-enabled", "true");
        var on = new KafkaConfig().kafkaListenerContainerFactory(
                org.mockito.Mockito.mock(org.springframework.kafka.core.ConsumerFactory.class),
                org.mockito.Mockito.mock(org.springframework.kafka.listener.CommonErrorHandler.class), env);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(on, "autoStartup")).isEqualTo(true);
    }

    @Test
    void producerNeverWaitsAMinuteForAnUnreachableBroker() {
        var props = TestSupport.props();
        var factory = (org.springframework.kafka.core.DefaultKafkaProducerFactory<String, String>)
                new KafkaConfig().kafkaProducerFactory(props);
        var config = factory.getConfigurationProperties();
        assertThat(config).containsEntry("max.block.ms", KafkaConfig.PRODUCER_MAX_BLOCK_MS)
                .containsEntry("request.timeout.ms", KafkaConfig.PRODUCER_REQUEST_TIMEOUT_MS)
                .containsEntry("delivery.timeout.ms", KafkaConfig.PRODUCER_DELIVERY_TIMEOUT_MS)
                .containsEntry("connections.max.idle.ms", KafkaConfig.PRODUCER_MAX_IDLE_MS);
        assertThat(KafkaConfig.PRODUCER_MAX_BLOCK_MS).isLessThanOrEqualTo(10_000); // o padrão eram 60 000
        assertThat(KafkaConfig.PRODUCER_DELIVERY_TIMEOUT_MS).isGreaterThanOrEqualTo(KafkaConfig.PRODUCER_REQUEST_TIMEOUT_MS);
    }
}
