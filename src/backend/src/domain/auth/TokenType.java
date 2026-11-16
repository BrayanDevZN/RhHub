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

    /**
     * Define um tipo de token: o valor do campo typ dentro do JWT, o nome do cookie e a validade.
     */
    TokenType(String claim, String cookie, Duration lifetime) {
        this.claim = claim;
        this.cookie = cookie;
        this.lifetime = lifetime;
    }

    /**
     * Valor gravado no campo typ do JWT ("access", "refresh" ou "2fa").
     */
    public String claim() {
        return claim;
    }

    /**
     * Nome do cookie HTTP onde o token viaja.
     */
    public String cookie() {
        return cookie;
    }

    /**
     * Validade do token: 15 min (acesso), 7 dias (refresh) e 5 min (2FA).
     */
    public Duration lifetime() {
        return lifetime;
    }

    /**
     * Descobre o tipo a partir do campo typ lido do JWT.
     *
     * @return o tipo, ou nulo se o valor não existir (o token é então recusado).
     */
    static TokenType fromClaim(String value) {
        for (TokenType type : values()) {
            if (type.claim.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
