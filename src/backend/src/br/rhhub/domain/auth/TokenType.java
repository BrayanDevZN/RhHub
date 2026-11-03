package br.rhhub.domain.auth;

import java.time.Duration;

/** Os três JWT de sessão (seção 4.3): cada um tem cookie, duração e uso próprios. */
public enum TokenType {
    ACCESS("access", "X-user_token", Duration.ofMinutes(15)),
    REFRESH("refresh", "X-user_refresh_token", Duration.ofDays(7)),
    TWO_FACTOR("2fa", "X-auth2_token", Duration.ofMinutes(5));

    private final String claim;
    private final String cookie;
    private final Duration lifetime;

    TokenType(String claim, String cookie, Duration lifetime) {
        this.claim = claim;
        this.cookie = cookie;
        this.lifetime = lifetime;
    }

    public String claim() {
        return claim;
    }

    public String cookie() {
        return cookie;
    }

    public Duration lifetime() {
        return lifetime;
    }

    static TokenType fromClaim(String value) {
        for (TokenType type : values()) {
            if (type.claim.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
