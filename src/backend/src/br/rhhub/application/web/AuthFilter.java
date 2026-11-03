package br.rhhub.application.web;

import br.rhhub.domain.auth.JwtService;
import br.rhhub.domain.auth.TokenType;
import br.rhhub.domain.exception.DomainException;
import java.time.Clock;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Tudo em /api exige o cookie X-user_token válido, menos as rotas públicas abaixo. Os cookies de refresh e de 2FA são
 * lidos pelos próprios controllers. Token ausente, vencido ou adulterado vira 401.
 */
@Component
public class AuthFilter implements WebFilter, Ordered {

    private static final Set<String> PUBLIC = Set.of(
            "POST /api/sender/signup-code",
            "POST /api/users",
            "POST /api/auth/login",
            "POST /api/auth/2fa/send",
            "POST /api/auth/2fa/verify",
            "POST /api/auth/refresh",
            "POST /api/auth/password/reset-code",
            "POST /api/auth/password/reset",
            "GET /api/auth/providers",
            "GET /api/health");

    private final JwtService jwt;
    private final Clock clock;
    private final WebSupport web;

    public AuthFilter(JwtService jwt, Clock clock, WebSupport web) {
        this.jwt = jwt;
        this.clock = clock;
        this.web = web;
    }

    @Override
    public int getOrder() {
        return -100;
    }

    /** Rotas que o filtro de limite trata como públicas (contadas por IP). */
    static boolean isPublic(HttpMethod method, String path) {
        return PUBLIC.contains(method + " " + path) || !path.startsWith("/api/");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        String path = request.getPath().value();
        if (request.getMethod() == HttpMethod.OPTIONS || isPublic(request.getMethod(), path)) {
            return chain.filter(exchange);
        }
        try {
            String token = web.cookieValue(exchange, TokenType.ACCESS.cookie());
            var claims = jwt.verify(token, TokenType.ACCESS, clock.instant());
            exchange.getAttributes().put(WebSupport.USER_ATTRIBUTE, claims.subject());
        } catch (DomainException e) {
            return Mono.error(e);
        }
        return chain.filter(exchange);
    }
}
