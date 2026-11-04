package br.rhhub.infra.cache;

import static org.assertj.core.api.Assertions.assertThat;

import br.rhhub.infra.TestSupport;
import br.rhhub.infra.redis.RedisStore;
import br.rhhub.testing.IntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/** Redis de verdade (localhost:6379); é pulado se ele não estiver de pé. */
@IntegrationTest
@EnabledIf("br.rhhub.infra.TestSupport#redisUp")
class RedisCacheStoreIntegrationTest {

    private static LettuceConnectionFactory factory;
    private static RedisCacheStore store;
    private final String ns = "cache:test:" + UUID.randomUUID() + ":";

    @BeforeAll
    static void open() {
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 6379));
        factory.afterPropertiesSet();
        var template = new ReactiveStringRedisTemplate(factory);
        store = new RedisCacheStore(new RedisStore(template), template);
    }

    @AfterAll
    static void close() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void setGetDeleteAndTtl() {
        store.set(ns + "a", "1", Duration.ofSeconds(30)).block();
        assertThat(store.get(ns + "a").block()).isEqualTo("1");
        store.delete(ns + "a").block();
        assertThat(store.get(ns + "a").block()).isNull();
    }

    @Test
    void deleteByPrefixRemovesOnlyThatFamilyAcrossManyKeys() {
        for (int i = 0; i < 250; i++) {
            store.set(ns + "vacancy:" + i, "x", Duration.ofSeconds(30)).block();
        }
        store.set(ns + "user:1", "keep", Duration.ofSeconds(30)).block();

        store.deleteByPrefix(ns + "vacancy:").block();

        assertThat(store.get(ns + "vacancy:0").block()).isNull();
        assertThat(store.get(ns + "vacancy:249").block()).isNull();
        assertThat(store.get(ns + "user:1").block()).isEqualTo("keep");
        store.delete(ns + "user:1").block();
    }

    @Test
    void queryCacheWorksEndToEndOnRealRedis() {
        var cache = new QueryCache(store);
        record Box(String v) {
        }
        assertThat(cache.one(ns + "box", Duration.ofSeconds(30), Box.class, () -> reactor.core.publisher.Mono.just(new Box("x"))).block())
                .isEqualTo(new Box("x"));
        assertThat(cache.one(ns + "box", Duration.ofSeconds(30), Box.class,
                () -> reactor.core.publisher.Mono.<Box>error(new IllegalStateException("devia vir do Redis"))).block())
                .isEqualTo(new Box("x"));
        cache.evict(ns + "box").block();
        assertThat(store.get(ns + "box").block()).isNull();
    }
}
