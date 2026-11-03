package br.rhhub.infra.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configurações lidas das variáveis de ambiente (ver application.yml e .env.example). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue("production") String environment, Ai ai) {

    /** Uma chave vazia ou só com espaços conta como ausente. */
    public record Ai(
            String openaiKey,
            String openaiModel,
            String openaiBaseUrl,
            String groqKey,
            String groqModel,
            String groqBaseUrl,
            Duration timeout) {
    }

    public boolean isTest() {
        return "test".equalsIgnoreCase(environment);
    }
}
