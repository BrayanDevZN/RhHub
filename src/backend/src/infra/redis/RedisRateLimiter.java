package br.rhhub.infra.redis;

import br.rhhub.application.port.out.RateLimiter;
import br.rhhub.domain.exception.DomainException;
import java.time.Duration;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Limite de requisições por minuto, contado com um contador do Redis que expira em 60 s. */
@Component
public class RedisRateLimiter implements RateLimiter {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    private final RedisStore redis;

    /**
     * Cria o limitador sobre o Redis.
     */
    public RedisRateLimiter(RedisStore redis) {
        this.redis = redis;
    }

    /**
     * Conta uma requisição para a chave (janela de 60 segundos) e confere o limite.
     *
     * @throws DomainException.TooManyRequests (429) se o total passou do limite por minuto.
     */
    @Override
    public Mono<Void> check(String key, int limitPerMinute) {
        return redis.incrementWithTtl("rate_limit:" + key, WINDOW).flatMap(total -> total > limitPerMinute
                ? Mono.error(new DomainException.TooManyRequests("Muitas requisições. Tente de novo em instantes."))
                : Mono.empty());
    }
}
