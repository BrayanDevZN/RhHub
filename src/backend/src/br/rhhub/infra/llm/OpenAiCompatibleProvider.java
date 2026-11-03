package br.rhhub.infra.llm;

import br.rhhub.application.port.out.AiProvider;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/** Cliente assíncrono da API /chat/completions, que OpenAI e Groq expõem do mesmo jeito. */
public class OpenAiCompatibleProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);

    /** Esperas por limite de taxa (429) antes de desistir e deixar o job tentar de novo mais tarde. */
    static final int RATE_LIMIT_RETRIES = 2;
    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(20);
    private static final Duration MAX_WAIT = Duration.ofSeconds(60);

    private final String name;
    private final String model;
    private final Duration timeout;
    private final WebClient client;

    public OpenAiCompatibleProvider(String name, String apiKey, String model, String baseUrl, Duration timeout) {
        this.name = name;
        this.model = model;
        this.timeout = timeout;
        this.client = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().proxyWithSystemProperties().responseTimeout(timeout)))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Mono<String> complete(String systemPrompt, String userInput) {
        var body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userInput)));
        return Mono.defer(() -> client.post().uri("/chat/completions").bodyValue(body).retrieve().bodyToMono(JsonNode.class)
                        .timeout(timeout))
                .retryWhen(Retry.from(signals -> signals.concatMap(signal -> {
                    Throwable failure = signal.failure();
                    if (failure instanceof WebClientResponseException.TooManyRequests limited
                            && signal.totalRetries() < RATE_LIMIT_RETRIES) {
                        Duration wait = retryAfter(limited);
                        log.warn("{}: limite de taxa (429), esperando {}s para tentar de novo", name, wait.toSeconds());
                        return Mono.delay(wait);
                    }
                    return Mono.<Long>error(failure);
                })))
                .map(response -> {
                    JsonNode content = response.path("choices").path(0).path("message").path("content");
                    if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
                        throw new AiException("Resposta vazia de " + name);
                    }
                    return content.asText();
                })
                .onErrorMap(e -> !(e instanceof AiException), e -> new AiException(describe(e), e));
    }

    /** O Retry-After do provedor, em segundos (padrão 20 s, no máximo 60 s). */
    static Duration retryAfter(WebClientResponseException e) {
        String header = e.getHeaders().getFirst("Retry-After");
        if (header != null) {
            try {
                double seconds = Double.parseDouble(header.strip());
                return Duration.ofMillis((long) (Math.min(Math.max(seconds, 1), MAX_WAIT.toSeconds()) * 1000));
            } catch (NumberFormatException ignored) {
                // cai no padrão
            }
        }
        return DEFAULT_WAIT;
    }

    /** Mensagem útil para o log e para a DLQ: o provedor, o status HTTP e o começo do corpo do erro. */
    private String describe(Throwable e) {
        if (e instanceof WebClientResponseException http) {
            String body = http.getResponseBodyAsString();
            return "Falha ao chamar " + name + ": HTTP " + http.getStatusCode().value()
                    + (body.isBlank() ? "" : " " + (body.length() > 300 ? body.substring(0, 300) + "…" : body));
        }
        return "Falha ao chamar " + name + ": " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " " + e.getMessage());
    }

    @Override
    public String name() {
        return name;
    }
}
