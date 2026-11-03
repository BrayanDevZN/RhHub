package br.rhhub.infra.redis;

import br.rhhub.application.port.out.RateLimiter;
import br.rhhub.domain.exception.DomainException;
import java.time.Duration;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RedisRateLimiter implements RateLimiter {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    private final RedisStore redis;

    public RedisRateLimiter(RedisStore redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Void> check(String key, int limitPerMinute) {
        return redis.incrementWithTtl("rate_limit:" + key, WINDOW).flatMap(total -> total > limitPerMinute
                ? Mono.error(new DomainException.TooManyRequests("Muitas requisições. Tente de novo em instantes."))
                : Mono.empty());
    }
}
