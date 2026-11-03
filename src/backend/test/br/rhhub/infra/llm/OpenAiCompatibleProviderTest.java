package br.rhhub.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.application.port.out.AiProvider;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleProviderTest {

    private record Reply(int status, String retryAfter, String body) {
    }

    private HttpServer server;
    private final Queue<Reply> replies = new ArrayDeque<>();
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            Reply reply = replies.size() > 1 ? replies.poll() : replies.peek();
            byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            if (reply.retryAfter() != null) {
                exchange.getResponseHeaders().add("Retry-After", reply.retryAfter());
            }
            exchange.sendResponseHeaders(reply.status(), out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AiProvider provider() {
        return new OpenAiCompatibleProvider("groq", "k", "m", "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(5));
    }

    private static Reply ok(String content) {
        return new Reply(200, null, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + content + "\"}}]}");
    }

    private static Reply limited(String retryAfter) {
        return new Reply(429, retryAfter, "{\"error\":{\"message\":\"Rate limit reached for tokens per minute (TPM)\"}}");
    }

    @Test
    void waitsForRetryAfterOnRateLimitAndThenSucceeds() {
        replies.add(limited("1"));
        replies.add(ok("pronto"));

        long start = System.currentTimeMillis();
        assertThat(provider().complete("s", "u").block()).isEqualTo("pronto");

        assertThat(calls.get()).isEqualTo(2);
        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(900); // esperou o Retry-After
    }

    @Test
    void givesUpAfterTheConfiguredNumberOfRateLimitWaitsAndExplainsWhy() {
        replies.add(limited("1"));

        assertThatThrownBy(() -> provider().complete("s", "u").block()).isInstanceOf(AiProvider.AiException.class)
                .hasMessageContaining("groq").hasMessageContaining("HTTP 429").hasMessageContaining("tokens per minute");
        assertThat(calls.get()).isEqualTo(1 + OpenAiCompatibleProvider.RATE_LIMIT_RETRIES);
    }

    @Test
    void otherHttpErrorsAreNotRetriedAndCarryTheBody() {
        replies.add(new Reply(500, null, "{\"error\":\"boom\"}"));

        assertThatThrownBy(() -> provider().complete("s", "u").block()).isInstanceOf(AiProvider.AiException.class)
                .hasMessageContaining("HTTP 500").hasMessageContaining("boom");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void emptyAnswerIsAnAiException() {
        replies.add(ok(""));
        assertThatThrownBy(() -> provider().complete("s", "u").block()).isInstanceOf(AiProvider.AiException.class)
                .hasMessageContaining("vazia");
    }

    @Test
    void retryAfterIsClampedAndFallsBackToTheDefault() {
        var headers = new org.springframework.http.HttpHeaders();
        headers.add("Retry-After", "500");
        assertThat(OpenAiCompatibleProvider.retryAfter(rateLimit(headers))).isEqualTo(Duration.ofSeconds(60));
        headers.set("Retry-After", "0.2");
        assertThat(OpenAiCompatibleProvider.retryAfter(rateLimit(headers))).isEqualTo(Duration.ofSeconds(1));
        headers.set("Retry-After", "daqui a pouco");
        assertThat(OpenAiCompatibleProvider.retryAfter(rateLimit(headers))).isEqualTo(Duration.ofSeconds(20));
        assertThat(OpenAiCompatibleProvider.retryAfter(rateLimit(new org.springframework.http.HttpHeaders())))
                .isEqualTo(Duration.ofSeconds(20));
    }

    private static org.springframework.web.reactive.function.client.WebClientResponseException rateLimit(
            org.springframework.http.HttpHeaders headers) {
        return org.springframework.web.reactive.function.client.WebClientResponseException.create(429, "Too Many Requests",
                headers, new byte[0], StandardCharsets.UTF_8);
    }
}
