package br.rhhub.infra.email;

import br.rhhub.testing.IntegrationTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.application.port.out.EmailSender;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.infra.TestSupport;
import br.rhhub.infra.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@IntegrationTest
class N8nEmailSenderTest {

    private static final String SECRET = "segredo-do-gmail-para-teste-1234567890";
    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpServer server;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 200;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] answer = "{\"message\":\"Workflow got started.\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private N8nEmailSender sender(String secret) {
        return new N8nEmailSender("http://127.0.0.1:" + server.getAddress().getPort() + "/webhook", secret,
                Duration.ofSeconds(3), Clock.fixed(NOW, ZoneOffset.UTC), mapper);
    }

    @Test
    void postsTheWorkflowContractWithAValidJwt() throws Exception {
        sender(SECRET).send(EmailMessage.html(" ana@gmail.com ", " Assunto ", "<p>oi</p>")).block();

        JsonNode sent = mapper.readTree(body.get());
        assertThat(sent.get("type").asText()).isEqualTo("html");
        assertThat(sent.get("to").asText()).isEqualTo("ana@gmail.com");
        assertThat(sent.get("subject").asText()).isEqualTo("Assunto");
        assertThat(sent.get("content").asText()).isEqualTo("<p>oi</p>");
        assertThat(sent.size()).isEqualTo(4);

        assertThat(authorization.get()).startsWith("Bearer ");
        String[] parts = authorization.get().substring(7).split("\\.");
        assertThat(parts).hasSize(3);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8)));
        assertThat(parts[2]).isEqualTo(expected);
        JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
        JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(header.get("alg").asText()).isEqualTo("HS256");
        assertThat(header.get("typ").asText()).isEqualTo("JWT");
        assertThat(claims.get("iat").asLong()).isEqualTo(NOW.getEpochSecond());
        assertThat(claims.get("exp").asLong()).isEqualTo(NOW.plusSeconds(60).getEpochSecond());
    }

    @Test
    void sendsPlainTextType() throws Exception {
        sender(SECRET).send(EmailMessage.text("ana@gmail.com", "S", "texto")).block();
        assertThat(mapper.readTree(body.get()).get("type").asText()).isEqualTo("text");
    }

    @Test
    void jwtWithAnotherSecretDoesNotMatch() {
        sender("outro-segredo-qualquer-0000000000000000").send(EmailMessage.text("a@b.co", "S", "t")).block();
        String signature = authorization.get().substring(7).split("\\.")[2];

        sender(SECRET).send(EmailMessage.text("a@b.co", "S", "t")).block();
        assertThat(authorization.get().substring(7).split("\\.")[2]).isNotEqualTo(signature);
    }

    @Test
    void invalidMessageIsRejectedBeforeAnyCall() {
        for (EmailMessage bad : new EmailMessage[] {
                EmailMessage.html("sem-arroba", "S", "c"),
                EmailMessage.html("", "S", "c"),
                EmailMessage.html(null, "S", "c"),
                EmailMessage.html("a@b.co", "  ", "c"),
                EmailMessage.html("a@b.co", "S", " "),
                new EmailMessage("a@b.co", "S", "c", null),
                null}) {
            assertThatThrownBy(() -> sender(SECRET).send(bad).block()).isInstanceOf(DomainException.Invalid.class);
        }
        assertThat(body.get()).isNull();
    }

    @Test
    void n8nRejectionBecomesEmailException() {
        status = 403;
        assertThatThrownBy(() -> sender(SECRET).send(EmailMessage.html("a@b.co", "S", "c")).block())
                .isInstanceOf(EmailSender.EmailException.class).hasMessageContaining("403");
    }

    @Test
    void unreachableN8nBecomesEmailException() {
        server.stop(0);
        assertThatThrownBy(() -> sender(SECRET).send(EmailMessage.html("a@b.co", "S", "c")).block())
                .isInstanceOf(EmailSender.EmailException.class);
    }

    @Test
    void configPicksSenderByEnvironmentAndSecret() {
        var config = new EmailConfig();
        var base = TestSupport.props();
        AppProperties prod = new AppProperties("production", base.database(), base.redis(), base.kafka(), base.ai(),
                new AppProperties.Email("http://x", SECRET, Duration.ofSeconds(1)));
        AppProperties noSecret = new AppProperties("production", base.database(), base.redis(), base.kafka(), base.ai(),
                new AppProperties.Email("http://x", "  ", Duration.ofSeconds(1)));

        assertThat(config.emailSender(prod, Clock.systemUTC(), mapper).name()).isEqualTo("n8n");
        assertThat(config.emailSender(base, Clock.systemUTC(), mapper).name()).isEqualTo("fixed");
        var none = config.emailSender(noSecret, Clock.systemUTC(), mapper);
        assertThat(none.name()).isEqualTo("none");
        assertThatThrownBy(() -> none.send(EmailMessage.html("a@b.co", "S", "c")).block())
                .isInstanceOf(EmailSender.EmailException.class).hasMessageContaining("gmail_secret");
        assertThat(config.emailSender(base, Clock.systemUTC(), mapper).send(EmailMessage.html("a@b.co", "S", "c")).block())
                .isNull();
    }
}
