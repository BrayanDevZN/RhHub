package br.rhhub.infrastructure.cache;

import br.rhhub.application.port.out.CodePurpose;
import br.rhhub.application.port.out.CodeStore;
import java.util.Locale;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Chaves {@code email_sender:<purpose>:<email>} (o mesmo formato do AgentRH) e {@code ...:attempts}. */
@Component
public class RedisCodeStore implements CodeStore {

    private final StringRedisTemplate redis;

    public RedisCodeStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void put(CodePurpose purpose, String email, String code) {
        redis.delete(attemptsKey(purpose, email));
        redis.opsForValue().set(key(purpose, email), code, purpose.ttl());
    }

    @Override
    public Optional<String> find(CodePurpose purpose, String email) {
        return Optional.ofNullable(redis.opsForValue().get(key(purpose, email)));
    }

    @Override
    public long registerAttempt(CodePurpose purpose, String email) {
        String attempts = attemptsKey(purpose, email);
        Long total = redis.opsForValue().increment(attempts);
        if (total != null && total == 1L) {
            redis.expire(attempts, purpose.ttl());
        }
        return total == null ? 0L : total;
    }

    @Override
    public void delete(CodePurpose purpose, String email) {
        redis.delete(java.util.List.of(key(purpose, email), attemptsKey(purpose, email)));
    }

    static String key(CodePurpose purpose, String email) {
        return "email_sender:" + purpose.name().toLowerCase(Locale.ROOT) + ":" + email.toLowerCase(Locale.ROOT);
    }

    static String attemptsKey(CodePurpose purpose, String email) {
        return key(purpose, email) + ":attempts";
    }
}
