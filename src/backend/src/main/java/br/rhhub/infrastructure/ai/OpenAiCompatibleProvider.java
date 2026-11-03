package br.rhhub.infrastructure.ai;

import br.rhhub.application.port.out.AiProvider;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Cliente da API /chat/completions, que OpenAI e Groq expõem do mesmo jeito. */
public class OpenAiCompatibleProvider implements AiProvider {

    private final String name;
    private final String model;
    private final RestClient client;

    public OpenAiCompatibleProvider(String name, String apiKey, String model, String baseUrl, Duration timeout) {
        this.name = name;
        this.model = model;
        var http = HttpClient.newBuilder().connectTimeout(timeout).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String complete(String systemPrompt, String userInput) {
        var body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userInput)));
        try {
            JsonNode response = client.post().uri("/chat/completions").body(body).retrieve().body(JsonNode.class);
            JsonNode content = response == null ? null : response.path("choices").path(0).path("message").path("content");
            if (content == null || content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
                throw new AiException("Resposta vazia de " + name);
            }
            return content.asText();
        } catch (RestClientException e) {
            throw new AiException("Falha ao chamar " + name, e);
        }
    }

    @Override
    public String name() {
        return name;
    }
}
