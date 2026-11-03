package br.rhhub.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Autenticação (prefixo app.auth).
 * jwtSecret: JWT_SECRET. Vazio: um segredo aleatório é gerado a cada subida (as sessões não sobrevivem a um reinício).
 * adminEmail e adminPassword: o admin raiz, criado na subida se ainda não existir.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String jwtSecret,
        String adminEmail,
        String adminPassword,
        @DefaultValue("60") int rateLimit,
        @DefaultValue("1000") int globalRateLimit,
        Google google) {

    /** enabled: GOOGLE_OAUTH_ENABLED. Ligado, exige clientId, clientSecret e redirectUri. */
    public record Google(
            @DefaultValue("false") boolean enabled,
            String clientId,
            String clientSecret,
            String redirectUri) {
    }

    public Google google() {
        return google == null ? new Google(false, null, null, null) : google;
    }
}
