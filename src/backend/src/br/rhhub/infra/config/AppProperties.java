package br.rhhub.infra.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configurações lidas das variáveis de ambiente (ver application.yml e .env.example). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue("production") String environment, Database database, Ai ai, Email email) {

    /**
     * url: DATABASE_URL. Vazia, ausente ou "none" usa o SQLite local em sqlitePath.
     */
    public record Database(String url, @DefaultValue("./data/rhhub.db") String sqlitePath, @DefaultValue("10") int poolSize) {
    }

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

    /**
     * Envio de e-mail pelo workflow "gmail" do n8n (webhook com JWT).
     * jwtSecret: gmail_secret, o mesmo segredo da credencial JWT do webhook. Vazio conta como ausente.
     */
    public record Email(String webhookUrl, String jwtSecret, Duration timeout) {
    }

    public boolean isTest() {
        return "test".equalsIgnoreCase(environment);
    }
}
