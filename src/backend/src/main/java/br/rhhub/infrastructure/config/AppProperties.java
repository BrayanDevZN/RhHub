package br.rhhub.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("production") String environment,
        Jwt jwt,
        Topics topics,
        Ai ai,
        Mail mail) {

    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl, Duration twoFactorTtl) {

        public Jwt {
            if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException("app.jwt.secret precisa ter pelo menos 32 bytes (JWT_SECRET).");
            }
        }
    }

    public record Topics(String email, String resumeAnalysis, int partitions, String consumerGroup) {
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

    public record Mail(boolean enabled, String from) {
    }

    public boolean isTest() {
        return "test".equalsIgnoreCase(environment);
    }
}
