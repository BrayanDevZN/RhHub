package br.rhhub.application.web;

import br.rhhub.application.port.out.RateLimiter;
import br.rhhub.infra.config.AuthProperties;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Limite por minuto: rotas públicas contam por IP, as demais por usuário, e há um contador global.
 * As rotas públicas que enviam e-mail ou conferem senha têm um teto menor (10 por minuto) contra força bruta.
 */
@Component
public class RateLimitFilter implements WebFilter, Ordered {

    static final int SENSITIVE_LIMIT = 10;

    private final RateLimiter limiter;
    private final AuthProperties props;

    public RateLimitFilter(RateLimiter limiter, AuthProperties props) {
        this.limiter = limiter;
        this.props = props;
    }

    @Override
    public int getOrder() {
        return -90;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        String path = request.getPath().value();
        if (request.getMethod() == HttpMethod.OPTIONS || path.equals("/api/health")) {
            return chain.filter(exchange);
        }
        UUID user = exchange.getAttribute(WebSupport.USER_ATTRIBUTE);
        boolean publicRoute = user == null;
        boolean sensitive = publicRoute && request.getMethod() == HttpMethod.POST;
        String key = publicRoute ? "ip:" + clientIp(exchange) + (sensitive ? ":" + path : "") : "user:" + user;
        int limit = sensitive ? Math.min(SENSITIVE_LIMIT, props.rateLimit()) : props.rateLimit();
        return limiter.check("global", props.globalRateLimit())
                .then(Mono.defer(() -> limiter.check(key, limit)))
                .then(Mono.defer(() -> chain.filter(exchange)));
    }

    /**
     * Atrás de um proxy (Railway) o IP real é o último da lista X-Forwarded-For, o que o próprio proxy acrescentou;
     * os anteriores o cliente pode forjar.
     */
    static String clientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].strip();
        }
        var remote = exchange.getRequest().getRemoteAddress();
        return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
    }
}
