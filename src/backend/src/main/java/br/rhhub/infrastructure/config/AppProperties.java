package br.rhhub.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String environment, Jwt jwt, Topics topics, Ai ai, Mail mail) {

    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl, Duration twoFactorTtl) {
    }

    public record Topics(String email, String resumeAnalysis, int emailPartitions, int analysisPartitions) {
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
