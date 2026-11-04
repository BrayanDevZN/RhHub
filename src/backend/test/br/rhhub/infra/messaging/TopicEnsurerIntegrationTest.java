package br.rhhub.infra.messaging;

import br.rhhub.testing.IntegrationTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.rhhub.infra.TestSupport;
import br.rhhub.infra.config.AppProperties;
import java.time.Duration;
import java.util.List;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

/** Kafka que reinicia sem volume perde os tópicos: o app tem de recriá-los sozinho, sem precisar reiniciar. */
@IntegrationTest
class TopicEnsurerIntegrationTest {

    private static final List<String> TOPICS = List.of("email.send", "email.send.DLT", "resume.analysis", "resume.analysis.DLT");

    private static EmbeddedKafkaKraftBroker broker;
    private static AnnotationConfigApplicationContext ctx;

    @Configuration
    @Import({KafkaConfig.class, TopicEnsurer.class})
    static class Context {
        @Bean
        AppProperties appProperties() {
            var base = TestSupport.props();
            return new AppProperties("test", base.database(), base.redis(),
                    new AppProperties.Kafka(broker.getBrokersAsString(), "rhhub", null, 3, Duration.ofMillis(50),
                            "email.send", "resume.analysis"),
                    base.ai(), base.email());
        }

        @Bean
        com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }

        /** Só para o KafkaConfig montar o error handler; este teste não processa mensagem. */
        @Bean
        br.rhhub.application.port.out.DeadLetterRepository deadLetterRepository() {
            return org.mockito.Mockito.mock(br.rhhub.application.port.out.DeadLetterRepository.class);
        }
    }

    @BeforeAll
    static void start() {
        broker = new EmbeddedKafkaKraftBroker(1, 3);
        broker.afterPropertiesSet();
        ctx = new AnnotationConfigApplicationContext(Context.class);
    }

    @AfterAll
    static void stop() {
        ctx.close();
        broker.destroy();
    }

    @Test
    void recreatesTopicsThatDisappearedAfterAKafkaRestartWithoutAVolume() throws Exception {
        try (var admin = AdminClient.create(KafkaConfig.connectionProperties(ctx.getBean(AppProperties.class).kafka()))) {
            assertThat(admin.listTopics().names().get()).containsAll(TOPICS); // criados na subida

            admin.deleteTopics(TOPICS).all().get(); // o que um Kafka sem volume faz ao reiniciar
            await().atMost(Duration.ofSeconds(20)).until(() -> admin.listTopics().names().get().stream().noneMatch(TOPICS::contains));

            ctx.getBean(TopicEnsurer.class).ensure();

            await().atMost(Duration.ofSeconds(20)).untilAsserted(
                    () -> assertThat(admin.listTopics().names().get()).containsAll(TOPICS));
            admin.describeTopics(TOPICS).allTopicNames().get().values().forEach(d -> assertThat(d.partitions()).hasSize(3));
        }
    }

    @Test
    void doesNothingWhenEverythingIsThere() {
        var ensurer = ctx.getBean(TopicEnsurer.class);
        ensurer.ensure();
        ensurer.ensure(); // idempotente: não falha nem duplica
    }
}
