package br.rhhub.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Web (prefixo app.web).
 * frontendOrigin: FRONTEND_ORIGIN, uma ou mais origens separadas por vírgula, liberadas no CORS (vazio: nenhuma).
 * cookieSecure: COOKIE_SECURE. Vazio: Secure ligado, exceto em ENVIRONMENT=test. Em http local use false.
 * cookieSameSite: COOKIE_SAMESITE (Strict, Lax ou None). Strict exige front e API no mesmo domínio.
 */
@ConfigurationProperties(prefix = "app.web")
public record WebProperties(String frontendOrigin, Boolean cookieSecure, @DefaultValue("Strict") String cookieSameSite) {

    public java.util.List<String> origins() {
        if (frontendOrigin == null || frontendOrigin.isBlank()) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(frontendOrigin.split(",")).map(String::strip)
                .map(o -> o.endsWith("/") ? o.substring(0, o.length() - 1) : o).filter(o -> !o.isEmpty()).toList();
    }

    /** Para onde o login com Google volta. */
    public String homeUrl() {
        var list = origins();
        return list.isEmpty() ? "/" : list.get(0) + "/";
    }
}
