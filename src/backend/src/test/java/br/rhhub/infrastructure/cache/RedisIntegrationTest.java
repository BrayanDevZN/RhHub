package br.rhhub.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import br.rhhub.application.port.out.CodePurpose;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Roda contra um Redis em localhost:6379 (database 15); é pulado se ele não estiver de pé. */
@EnabledIf("br.rhhub.infrastructure.TestSupport#redisUp")
class RedisIntegrationTest {

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        var cfg = new RedisStandaloneConfiguration("localhost", 6379);
        cfg.setDatabase(15);
        factory = new LettuceConnectionFactory(cfg);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void close() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void codeStoreKeepsCodeAndCountsAttempts() {
        var store = new RedisCodeStore(redis);
        String email = UUID.randomUUID() + "@gmail.com";

        store.put(CodePurpose.SIGNUP, email, "123456");
        assertThat(store.find(CodePurpose.SIGNUP, email)).contains("123456");
        assertThat(store.find(CodePurpose.TWO_FACTOR, email)).isEmpty();
        assertThat(redis.getExpire(RedisCodeStore.key(CodePurpose.SIGNUP, email))).isBetween(1L, 900L);

        assertThat(store.registerAttempt(CodePurpose.SIGNUP, email)).isEqualTo(1);
        assertThat(store.registerAttempt(CodePurpose.SIGNUP, email)).isEqualTo(2);
        store.put(CodePurpose.SIGNUP, email, "654321");
        assertThat(store.registerAttempt(CodePurpose.SIGNUP, email)).isEqualTo(1);

        store.delete(CodePurpose.SIGNUP, email);
        assertThat(store.find(CodePurpose.SIGNUP, email)).isEmpty();
        assertThat(redis.hasKey(RedisCodeStore.attemptsKey(CodePurpose.SIGNUP, email))).isFalse();
    }

    @Test
    void refreshStoreHoldsOnlyTheCurrentToken() {
        var store = new RedisRefreshTokenStore(redis);
        UUID id = UUID.randomUUID();

        store.save(id, "a", Duration.ofMinutes(1));
        assertThat(store.matches(id, "a")).isTrue();
        store.save(id, "b", Duration.ofMinutes(1));
        assertThat(store.matches(id, "a")).isFalse();
        assertThat(store.matches(id, "b")).isTrue();
        store.delete(id);
        assertThat(store.matches(id, "b")).isFalse();
    }

    @Test
    void rateLimiterBlocksAfterLimitAndSetsExpiry() {
        var limiter = new RedisRateLimiter(redis);
        String key = "test:" + UUID.randomUUID();

        assertThat(limiter.tryAcquire(key, 2, Duration.ofSeconds(30))).isTrue();
        assertThat(limiter.tryAcquire(key, 2, Duration.ofSeconds(30))).isTrue();
        assertThat(limiter.tryAcquire(key, 2, Duration.ofSeconds(30))).isFalse();
        assertThat(redis.getExpire("rate_limit:" + key)).isBetween(1L, 30L);
    }
}
