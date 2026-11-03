package br.rhhub.infra.llm;

import br.rhhub.application.port.out.AiProvider;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/** Cliente assíncrono da API /chat/completions, que OpenAI e Groq expõem do mesmo jeito. */
public class OpenAiCompatibleProvider implements AiProvider {

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
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().responseTimeout(timeout)))
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
        return client.post().uri("/chat/completions").bodyValue(body).retrieve().bodyToMono(JsonNode.class)
                .timeout(timeout)
                .map(response -> {
                    JsonNode content = response.path("choices").path(0).path("message").path("content");
                    if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
                        throw new AiException("Resposta vazia de " + name);
                    }
                    return content.asText();
                })
                .onErrorMap(e -> !(e instanceof AiException), e -> new AiException("Falha ao chamar " + name, e));
    }

    @Override
    public String name() {
        return name;
    }
}
