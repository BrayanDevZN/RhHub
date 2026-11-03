package br.rhhub.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Web (prefixo app.web).
 * frontendOrigin: FRONTEND_URL (nome antigo: FRONTEND_ORIGIN), uma ou mais origens separadas por vírgula, liberadas no CORS (vazio: nenhuma).
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
                .map(o -> o.replaceAll("^[\"']+|[\"']+$", ""))
                .map(WebProperties::normalize).filter(o -> !o.isEmpty()).toList();
    }

    /**
     * Garante o esquema e tira a barra do fim. Sem esquema, vira https:// (http:// só para localhost e IPs locais):
     * sem isso o redirecionamento do login com Google seria tratado como caminho relativo.
     */
    static String normalize(String origin) {
        String value = origin.strip();
        if (value.isEmpty()) {
            return value;
        }
        if (!value.contains("://")) {
            String host = value.split("[/:]")[0];
            boolean local = host.equals("localhost") || host.startsWith("127.") || host.startsWith("192.168.")
                    || host.startsWith("10.");
            value = (local ? "http://" : "https://") + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /** Para onde o login com Google volta. */
    public String homeUrl() {
        var list = origins();
        return list.isEmpty() ? "/" : list.get(0) + "/";
    }
}
