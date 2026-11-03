package br.rhhub.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.rhhub.application.port.in.DeliverEmail;
import br.rhhub.application.port.out.DeadLetterRepository;
import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.application.port.out.EmailPublisher;
import br.rhhub.domain.model.DeadLetter;
import br.rhhub.domain.model.DeadLetterStatus;
import br.rhhub.infra.TestSupport;
import br.rhhub.infra.config.AppProperties;
import br.rhhub.infra.email.N8nEmailSender;
import br.rhhub.infra.messaging.EmailConsumer;
import br.rhhub.infra.messaging.KafkaConfig;
import br.rhhub.infra.messaging.KafkaEmailPublisher;
import br.rhhub.infra.messaging.KafkaPublisher;
import br.rhhub.service.DeliverEmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * ENVIA UM E-MAIL DE VERDADE: producer Kafka → tópico email.send (broker embutido) → EmailConsumer → DeliverEmail →
 * N8nEmailSender → webhook do workflow "gmail" do n8n → Gmail.
 *
 * <p>Só roda quando TODAS as condições valem, senão é pulado (o motivo aparece como {@code EMAIL-LIVE PULADO}):
 * <ul>
 *   <li>{@code -Drhhub.test.email=destinatario@gmail.com} (sem isso nunca envia nada);</li>
 *   <li>{@code gmail_secret} (ou GMAIL_SECRET) no ambiente: o segredo da credencial JWT do webhook;</li>
 *   <li>o n8n acessível desta máquina.</li>
 * </ul>
 * Uso: {@code mvn test -Dtest=EmailEndToEndLiveTest -Drhhub.test.email=voce@gmail.com}.
 *
 * <p>O n8n responde 200 assim que recebe, antes de enviar: este teste prova que o n8n ACEITOU o pedido. Que o Gmail
 * entregou se confere na execução do workflow no n8n e na caixa de entrada.
 */
class EmailEndToEndLiveTest {

    private static final String WEBHOOK = "https://n8n.nexoaicompany.com/webhook/a043ad9f-9a0c-4bbd-a142-f64a1cd524c0";

    private static String to;
    private static String secret;
    private static EmbeddedKafkaKraftBroker broker;
    private static AnnotationConfigApplicationContext ctx;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final List<EmailMessage> accepted = new CopyOnWriteArrayList<>();
    private static final ConcurrentHashMap<Long, DeadLetter> dead = new ConcurrentHashMap<>();

    @Configuration
    @Import({KafkaConfig.class, KafkaPublisher.class, KafkaEmailPublisher.class, EmailConsumer.class})
    static class Context {

        @Bean
        AppProperties appProperties() {
            var base = TestSupport.props();
            return new AppProperties("production", base.database(), base.redis(),
                    new AppProperties.Kafka(broker.getBrokersAsString(), "rhhub", null, 3, Duration.ofMillis(200),
                            "email.send", "resume.analysis"),
                    base.ai(), new AppProperties.Email(WEBHOOK, secret, Duration.ofSeconds(30)));
        }

        @Bean
        ObjectMapper objectMapper() {
            return mapper;
        }

        /** O mesmo DeliverEmailService da aplicação, com o N8nEmailSender de verdade; só anota o que o n8n aceitou. */
        @Bean
        DeliverEmail deliverEmail(AppProperties props) {
            var real = new DeliverEmailService(new N8nEmailSender(props.email().webhookUrl(), props.email().jwtSecret(),
                    props.email().timeout(), Clock.systemUTC(), mapper));
            return message -> real.execute(message).doOnSuccess(done -> accepted.add(message));
        }

        @Bean
        DeadLetterRepository deadLetters() {
            return new DeadLetterRepository() {
                @Override
                public Mono<DeadLetter> save(DeadLetter d) {
                    var saved = new DeadLetter((long) dead.size() + 1, d.topic(), d.key(), d.partition(), d.offset(),
                            d.payload(), d.errorType(), d.errorMessage(), d.attempts(), d.status(), Instant.now(), null);
                    dead.put(saved.id(), saved);
                    return Mono.just(saved);
                }

                @Override
                public Mono<DeadLetter> findById(Long id) {
                    return Mono.justOrEmpty(dead.get(id));
                }

                @Override
                public Flux<DeadLetter> findByKey(String topic, String key) {
                    return Flux.fromIterable(dead.values());
                }

                @Override
                public Flux<DeadLetter> findAll(DeadLetterStatus status) {
                    return Flux.fromIterable(dead.values());
                }

                @Override
                public Mono<DeadLetter> markResolved(Long id) {
                    return Mono.empty();
                }
            };
        }
    }

    @BeforeAll
    static void preflight() throws Exception {
        to = System.getProperty("rhhub.test.email");
        if (to == null || to.isBlank()) {
            System.out.println("EMAIL-LIVE PULADO: passe -Drhhub.test.email=destinatario@gmail.com para enviar de verdade");
        }
        assumeTrue(to != null && !to.isBlank(), "Sem -Drhhub.test.email: teste de envio real pulado");
        secret = System.getenv("gmail_secret") != null ? System.getenv("gmail_secret") : System.getenv("GMAIL_SECRET");
        if (secret == null || secret.isBlank()) {
            System.out.println("EMAIL-LIVE PULADO: sem gmail_secret/GMAIL_SECRET no ambiente");
        }
        assumeTrue(secret != null && !secret.isBlank(), "Sem gmail_secret: teste de envio real pulado");
        try {
            HttpClient.newBuilder().proxy(ProxySelector.getDefault()).connectTimeout(Duration.ofSeconds(10)).build()
                    .send(HttpRequest.newBuilder(URI.create("https://n8n.nexoaicompany.com/healthz")).timeout(Duration.ofSeconds(15))
                            .GET().build(), HttpResponse.BodyHandlers.discarding());
        } catch (java.io.IOException e) {
            System.out.println("EMAIL-LIVE PULADO: n8n.nexoaicompany.com inacessível daqui (" + e.getClass().getSimpleName() + ")");
            assumeTrue(false, "n8n inacessível: teste de envio real pulado");
        }

        broker = new EmbeddedKafkaKraftBroker(1, 3);
        broker.afterPropertiesSet();
        ctx = new AnnotationConfigApplicationContext(Context.class);
        await().atMost(Duration.ofSeconds(60)).until(() -> br.rhhub.infra.TestSupport.allConsumersAssigned(
                ctx.getBean(org.springframework.kafka.config.KafkaListenerEndpointRegistry.class)));
    }

    @AfterAll
    static void stop() {
        if (ctx != null) {
            ctx.close();
        }
        if (broker != null) {
            broker.destroy();
        }
    }

    @Test
    void emailTravelsFromTheProducerThroughKafkaToTheN8nWebhook() {
        String stamp = Instant.now().toString();
        String subject = "Teste RhHub: e-mail pelo Kafka e pelo n8n (" + stamp.substring(11, 19) + " UTC)";
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Teste RhHub</title></head>"
                + "<body style=\"font-family:Arial,sans-serif;color:#0f172a;max-width:560px;margin:24px auto\">"
                + "<h2 style=\"color:#0f766e\">Teste do RhHub</h2>"
                + "<p>Este e-mail percorreu o caminho completo do back end:</p>"
                + "<ol><li>producer Kafka (<code>KafkaEmailPublisher</code>) publicou no tópico <code>email.send</code>;</li>"
                + "<li>o <code>EmailConsumer</code> leu a mensagem;</li>"
                + "<li>o <code>N8nEmailSender</code> chamou o webhook do n8n com um JWT assinado;</li>"
                + "<li>o n8n enviou pelo Gmail.</li></ol>"
                + "<p style=\"color:#64748b;font-size:13px\">Enviado em " + stamp + ". Nenhuma ação é necessária.</p>"
                + "</body></html>";

        ctx.getBean(EmailPublisher.class).publish(0L, EmailMessage.html(to, subject, html)).block(Duration.ofSeconds(30));

        await().atMost(Duration.ofSeconds(60)).until(() -> !accepted.isEmpty() || !dead.isEmpty());
        assertThat(dead.values()).as("a mensagem foi para a DLQ: %s", dead.values()).isEmpty();
        assertThat(accepted).hasSize(1);
        assertThat(accepted.get(0).to()).isEqualTo(to);
        assertThat(accepted.get(0).subject()).isEqualTo(subject);
        System.out.println("EMAIL-LIVE aceito pelo n8n: para=" + to + " assunto=\"" + subject + "\"");
    }
}
