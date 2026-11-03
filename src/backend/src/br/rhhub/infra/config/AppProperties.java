package br.rhhub.infra.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configurações lidas das variáveis de ambiente (ver application.yml e .env.example). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue("production") String environment, Database database, Redis redis, Kafka kafka, Ai ai, Email email) {

    /**
     * url: DATABASE_URL. Vazia, ausente ou "none" usa o SQLite local em sqlitePath.
     */
    public record Database(String url, @DefaultValue("./data/rhhub.db") String sqlitePath, @DefaultValue("10") int poolSize) {
    }

    /**
     * REDIS_HOST, REDIS_PORT e REDIS_PASSWORD. Sem as variáveis, usa o Redis local padrão (localhost:6379).
     * Senha vazia, "none" ou "null" conta como sem senha: conecta só com host e porta.
     */
    public record Redis(@DefaultValue("localhost") String host, @DefaultValue("6379") int port, String password) {

        public Redis {
            if (host == null || host.isBlank()) {
                host = "localhost";
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("REDIS_PORT inválida: " + port);
            }
        }

        public boolean hasPassword() {
            return password != null && !password.isBlank()
                    && !"none".equalsIgnoreCase(password.strip()) && !"null".equalsIgnoreCase(password.strip());
        }
    }

    /**
     * url: KAFKA_URL. Vazia, ausente ou "none" usa o Kafka local (localhost:9092). Aceita host, host:porta,
     * kafka://host:porta e uma lista separada por vírgula.
     * apiKey: KAFKA_API_KEY. Preenchida liga SASL/PLAIN com apiUser (KAFKA_API_USER); vazia conecta sem autenticação.
     */
    public record Kafka(
            String url,
            @DefaultValue("rhhub") String apiUser,
            String apiKey,
            @DefaultValue("3") int partitions,
            @DefaultValue("2s") Duration retryInterval,
            @DefaultValue("email.send") String emailTopic,
            @DefaultValue("resume.analysis") String analysisTopic) {

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank()
                    && !"none".equalsIgnoreCase(apiKey.strip()) && !"null".equalsIgnoreCase(apiKey.strip());
        }
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
