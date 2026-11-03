package br.rhhub.infra.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.rhhub.application.port.in.AnalyzeResume;
import br.rhhub.application.port.in.DeliverEmail;
import br.rhhub.application.port.out.DeadLetterRepository;
import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.DeadLetter;
import br.rhhub.domain.model.DeadLetterStatus;
import br.rhhub.infra.TestSupport;
import br.rhhub.infra.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Sobe um broker Kafka de verdade (embutido, KRaft, sem Docker) e roda o fluxo inteiro: tópicos, publicação,
 * consumers, novas tentativas, DLT e a tabela dead_letters. O banco é um fake em memória.
 */
class KafkaMessagingIntegrationTest {

    private static EmbeddedKafkaKraftBroker broker;
    private static AnnotationConfigApplicationContext ctx;
    private static final FakeAnalyze analyze = new FakeAnalyze();
    private static final FakeDeliver deliver = new FakeDeliver();
    private static final InMemoryDeadLetters deadLetters = new InMemoryDeadLetters();

    @Configuration
    @Import({KafkaConfig.class, KafkaPublisher.class, KafkaResumeAnalysisPublisher.class, KafkaEmailPublisher.class,
            AnalysisConsumer.class, EmailConsumer.class})
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
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        DeadLetterRepository deadLetterRepository() {
            return deadLetters;
        }

        @Bean
        AnalyzeResume analyzeResume() {
            return analyze;
        }

        @Bean
        DeliverEmail deliverEmail() {
            return deliver;
        }
    }

    @BeforeAll
    static void start() {
        broker = new EmbeddedKafkaKraftBroker(1, 3);
        broker.afterPropertiesSet();
        ctx = new AnnotationConfigApplicationContext(Context.class);
        // espera os dois consumers (3 threads cada) pegarem as partições
        await().atMost(Duration.ofSeconds(60)).until(() -> br.rhhub.infra.TestSupport.allConsumersAssigned(
                ctx.getBean(org.springframework.kafka.config.KafkaListenerEndpointRegistry.class)));
    }

    @AfterAll
    static void stop() {
        ctx.close();
        broker.destroy();
    }

    private static String id() {
        return String.valueOf(Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000));
    }

    @Test
    void createsTheFourTopicsWithThreePartitions() throws Exception {
        try (var admin = AdminClient.create(KafkaConfig.connectionProperties(ctx.getBean(AppProperties.class).kafka()))) {
            var descriptions = admin.describeTopics(List.of("email.send", "email.send.DLT", "resume.analysis",
                    "resume.analysis.DLT")).allTopicNames().get();
            assertThat(descriptions).hasSize(4);
            descriptions.values().forEach(d -> assertThat(d.partitions()).hasSize(3));
            // o padrão do Spring Kafka ("<topico>-dlt") não pode ter sido usado
            assertThat(admin.listTopics().names().get()).noneMatch(name -> name.endsWith("-dlt"));
        }
    }

    @Test
    void analysisMessageReachesTheUseCaseWithTheResumeId() {
        long resumeId = Long.parseLong(id());
        ctx.getBean(KafkaResumeAnalysisPublisher.class).publish(resumeId).block();

        await().atMost(Duration.ofSeconds(30)).until(() -> analyze.calls.contains(resumeId));
    }

    @Test
    void emailMessageReachesTheUseCaseIntact() {
        long resumeId = Long.parseLong(id());
        var email = EmailMessage.html("ana@gmail.com", "Resultado " + resumeId, "<p>Aprovada</p>");
        ctx.getBean(KafkaEmailPublisher.class).publish(resumeId, email).block();

        await().atMost(Duration.ofSeconds(30)).until(() -> deliver.delivered.contains(email));
    }

    @Test
    void failingAnalysisIsRetriedThenSavedInTheDlqAndSentToTheDltTopic() {
        long resumeId = 900_000 + Long.parseLong(id());
        analyze.failFor.put(resumeId, new IllegalStateException("LLM fora do ar"));
        ctx.getBean(KafkaResumeAnalysisPublisher.class).publish(resumeId).block();

        await().atMost(Duration.ofSeconds(30)).until(() -> !deadLetters.byKey("resume.analysis", String.valueOf(resumeId)).isEmpty());
        DeadLetter saved = deadLetters.byKey("resume.analysis", String.valueOf(resumeId)).get(0);
        assertThat(saved.status()).isEqualTo(DeadLetterStatus.FAILED);
        assertThat(saved.attempts()).isEqualTo(4); // 1 + 3 repetições
        assertThat(analyze.callsFor(resumeId)).isEqualTo(4);
        assertThat(saved.errorType()).isEqualTo("java.lang.IllegalStateException");
        assertThat(saved.errorMessage()).isEqualTo("LLM fora do ar");
        assertThat(saved.payload()).isEqualTo("{\"resumeId\":" + resumeId + "}");
        assertThat(saved.partition()).isNotNull();
        assertThat(saved.offset()).isNotNull();
        assertThat(topicContains("resume.analysis.DLT", "\"resumeId\":" + resumeId)).isTrue();
    }

    @Test
    void failingEmailGetsFewerAttempts() {
        long resumeId = 800_000 + Long.parseLong(id());
        deliver.failFor.put("falha-" + resumeId + "@gmail.com", new IllegalStateException("n8n fora do ar"));
        ctx.getBean(KafkaEmailPublisher.class)
                .publish(resumeId, EmailMessage.text("falha-" + resumeId + "@gmail.com", "S", "t")).block();

        await().atMost(Duration.ofSeconds(30)).until(() -> !deadLetters.byKey("email.send", String.valueOf(resumeId)).isEmpty());
        assertThat(deadLetters.byKey("email.send", String.valueOf(resumeId)).get(0).attempts()).isEqualTo(3); // 1 + 2
        assertThat(deliver.callsFor("falha-" + resumeId + "@gmail.com")).isEqualTo(3);
    }

    @Test
    void invalidEmailAndMalformedMessagesGoStraightToTheDlq() {
        long resumeId = 700_000 + Long.parseLong(id());
        deliver.failFor.put("ruim-" + resumeId + "@gmail.com", new DomainException.Invalid("to inválido"));
        ctx.getBean(KafkaEmailPublisher.class)
                .publish(resumeId, EmailMessage.text("ruim-" + resumeId + "@gmail.com", "S", "t")).block();
        ctx.getBean(KafkaPublisher.class).send("resume.analysis", "lixo-" + resumeId, "isso-nao-e-json").block();

        await().atMost(Duration.ofSeconds(30)).until(() -> !deadLetters.byKey("email.send", String.valueOf(resumeId)).isEmpty()
                && !deadLetters.byKey("resume.analysis", "lixo-" + resumeId).isEmpty());
        assertThat(deadLetters.byKey("email.send", String.valueOf(resumeId)).get(0).attempts()).isEqualTo(1);
        assertThat(deliver.callsFor("ruim-" + resumeId + "@gmail.com")).isEqualTo(1); // sem repetição
        var malformed = deadLetters.byKey("resume.analysis", "lixo-" + resumeId).get(0);
        assertThat(malformed.attempts()).isEqualTo(1);
        assertThat(malformed.errorType()).contains("Json");
        assertThat(malformed.payload()).isEqualTo("isso-nao-e-json");
    }

    @Test
    void sameKeyAlwaysLandsInTheSamePartition() {
        long resumeId = 600_000 + Long.parseLong(id());
        var publisher = ctx.getBean(KafkaResumeAnalysisPublisher.class);
        analyze.failFor.put(resumeId, new IllegalStateException("x"));
        publisher.publish(resumeId).block();
        publisher.publish(resumeId).block();

        await().atMost(Duration.ofSeconds(30)).until(() -> deadLetters.byKey("resume.analysis", String.valueOf(resumeId)).size() == 2);
        var partitions = deadLetters.byKey("resume.analysis", String.valueOf(resumeId)).stream().map(DeadLetter::partition).distinct().toList();
        assertThat(partitions).hasSize(1);
    }

    /** Lê o tópico do começo, com as mesmas propriedades de conexão da aplicação, até achar o trecho (ou 20 s). */
    private static boolean topicContains(String topic, String fragment) {
        Map<String, Object> config = KafkaConfig.connectionProperties(ctx.getBean(AppProperties.class).kafka());
        config.put("key.deserializer", StringDeserializer.class);
        config.put("value.deserializer", StringDeserializer.class);
        config.put("group.id", "leitor-" + UUID.randomUUID());
        config.put("auto.offset.reset", "earliest");
        try (var consumer = new KafkaConsumer<String, String>(config)) {
            consumer.subscribe(List.of(topic));
            long end = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < end) {
                for (var record : consumer.poll(Duration.ofMillis(300))) {
                    if (record.value().contains(fragment)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static class FakeAnalyze implements AnalyzeResume {
        final List<Long> calls = new CopyOnWriteArrayList<>();
        final Map<Long, RuntimeException> failFor = new ConcurrentHashMap<>();

        @Override
        public Mono<Void> execute(Long resumeId) {
            calls.add(resumeId);
            var error = failFor.get(resumeId);
            return error == null ? Mono.empty() : Mono.error(error);
        }

        long callsFor(long id) {
            return calls.stream().filter(c -> c == id).count();
        }
    }

    static class FakeDeliver implements DeliverEmail {
        final List<EmailMessage> delivered = new CopyOnWriteArrayList<>();
        final List<String> calls = new CopyOnWriteArrayList<>();
        final Map<String, RuntimeException> failFor = new ConcurrentHashMap<>();

        @Override
        public Mono<Void> execute(EmailMessage message) {
            calls.add(message.to());
            var error = failFor.get(message.to());
            if (error != null) {
                return Mono.error(error);
            }
            delivered.add(message);
            return Mono.empty();
        }

        long callsFor(String to) {
            return calls.stream().filter(to::equals).count();
        }
    }

    /** Mesma regra do banco: (topic, partition, offset) único. */
    static class InMemoryDeadLetters implements DeadLetterRepository {
        private final Map<String, DeadLetter> store = new ConcurrentHashMap<>();
        private long next = 1;

        @Override
        public synchronized Mono<DeadLetter> save(DeadLetter d) {
            String origin = d.topic() + "/" + d.partition() + "/" + d.offset();
            return Mono.just(store.computeIfAbsent(origin, o -> new DeadLetter(next++, d.topic(), d.key(), d.partition(),
                    d.offset(), d.payload(), d.errorType(), d.errorMessage(), d.attempts(), d.status(),
                    java.time.Instant.now(), null)));
        }

        List<DeadLetter> byKey(String topic, String key) {
            return store.values().stream().filter(d -> d.topic().equals(topic) && key.equals(d.key())).toList();
        }

        @Override
        public Mono<DeadLetter> findById(Long id) {
            return Flux.fromIterable(store.values()).filter(d -> d.id().equals(id)).next();
        }

        @Override
        public Flux<DeadLetter> findByKey(String topic, String key) {
            return Flux.fromIterable(byKey(topic, key));
        }

        @Override
        public Flux<DeadLetter> findAll(DeadLetterStatus status) {
            Collection<DeadLetter> all = store.values();
            return Flux.fromIterable(all).filter(d -> status == null || d.status() == status);
        }

        @Override
        public Mono<DeadLetter> markResolved(Long id) {
            return Mono.error(new UnsupportedOperationException());
        }
    }
}
