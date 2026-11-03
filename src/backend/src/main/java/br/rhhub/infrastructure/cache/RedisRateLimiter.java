package br.rhhub.infrastructure.cache;

import br.rhhub.application.port.out.RateLimiter;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Janela fixa por chave. INCR e EXPIRE rodam num script Lua para não deixar chave sem expiração. */
@Component
public class RedisRateLimiter implements RateLimiter {

    private static final DefaultRedisScript<Long> COUNT = new DefaultRedisScript<>("""
            local total = redis.call('INCR', KEYS[1])
            if total == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return total
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        Long total = redis.execute(COUNT, List.of("rate_limit:" + key), String.valueOf(window.toMillis()));
        return total != null && total <= limit;
    }
}
