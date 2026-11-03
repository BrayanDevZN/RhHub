package br.rhhub.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.infra.TestSupport;
import br.rhhub.infra.config.AppProperties;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/** Roda contra um Redis em localhost:6379 (chaves com UUID, apagadas no fim); é pulado se ele não estiver de pé. */
@EnabledIf("br.rhhub.infra.TestSupport#redisUp")
class RedisStoreIntegrationTest {

    private static RedisConfig config;
    private static org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory factory;
    private static RedisStore store;
    private static Process secured;

    private static AppProperties props(int port, String password) {
        var base = TestSupport.props();
        return new AppProperties("test", base.database(), new AppProperties.Redis("localhost", port, password),
                base.kafka(), base.ai(), base.email());
    }

    private static RedisStore storeFor(AppProperties props) {
        config = new RedisConfig();
        var f = config.redisConnectionFactory(props);
        f.afterPropertiesSet();
        factory = f;
        return new RedisStore(new ReactiveStringRedisTemplate(f));
    }

    @BeforeAll
    static void connect() {
        store = storeFor(props(6379, null));
    }

    @AfterAll
    static void close() {
        factory.destroy();
        if (secured != null) {
            secured.destroy();
        }
    }

    private static String key() {
        return "test:" + UUID.randomUUID();
    }

    @Test
    void pingAnswersPong() {
        assertThat(store.ping().block()).isEqualTo("PONG");
    }

    @Test
    void setGetExistsAndDelete() {
        String key = key();
        assertThat(store.get(key).block()).isNull();
        assertThat(store.exists(key).block()).isFalse();

        store.set(key, "valor").block();
        assertThat(store.get(key).block()).isEqualTo("valor");
        assertThat(store.exists(key).block()).isTrue();
        assertThat(store.ttl(key).block()).isNull(); // sem expiração

        assertThat(store.delete(key).block()).isEqualTo(1L);
        assertThat(store.get(key).block()).isNull();
        assertThat(store.delete().block()).isZero();
    }

    @Test
    void setWithTtlExpiresAndExpireUpdates() {
        String key = key();
        store.set(key, "x", Duration.ofSeconds(30)).block();
        assertThat(store.ttl(key).block()).isBetween(Duration.ofSeconds(1), Duration.ofSeconds(30));

        assertThat(store.expire(key, Duration.ofMinutes(5)).block()).isTrue();
        assertThat(store.ttl(key).block()).isGreaterThan(Duration.ofSeconds(60));

        store.set(key, "curto", Duration.ofMillis(200)).block();
        sleep(400);
        assertThat(store.get(key).block()).isNull();
    }

    @Test
    void setIfAbsentOnlyWritesOnce() {
        String key = key();
        assertThat(store.setIfAbsent(key, "primeiro", Duration.ofSeconds(30)).block()).isTrue();
        assertThat(store.setIfAbsent(key, "segundo", Duration.ofSeconds(30)).block()).isFalse();
        assertThat(store.get(key).block()).isEqualTo("primeiro");
        store.delete(key).block();
    }

    @Test
    void incrementCountsAndIncrementWithTtlSetsExpiryOnlyOnce() {
        String plain = key();
        assertThat(store.increment(plain).block()).isEqualTo(1L);
        assertThat(store.increment(plain).block()).isEqualTo(2L);

        String counter = key();
        assertThat(store.incrementWithTtl(counter, Duration.ofSeconds(30)).block()).isEqualTo(1L);
        assertThat(store.ttl(counter).block()).isBetween(Duration.ofSeconds(1), Duration.ofSeconds(30));
        store.expire(counter, Duration.ofSeconds(100)).block();
        assertThat(store.incrementWithTtl(counter, Duration.ofSeconds(30)).block()).isEqualTo(2L);
        assertThat(store.ttl(counter).block()).isGreaterThan(Duration.ofSeconds(60)); // não reiniciou o prazo
        store.delete(plain, counter).block();
    }

    @Test
    void connectsWithPasswordWhenServerRequiresIt() throws Exception {
        int port = 16379;
        secured = startSecuredRedis(port, "s3gredo");
        try {
            var withPassword = storeFor(props(port, "s3gredo"));
            assertThat(withPassword.ping().block()).isEqualTo("PONG");
            factory.destroy();

            var wrongPassword = storeFor(props(port, "errada"));
            assertThatThrownBy(() -> wrongPassword.ping().block()).isInstanceOf(RuntimeException.class);
            factory.destroy();

            var noPassword = storeFor(props(port, "none")); // none = sem senha: só host e porta
            assertThatThrownBy(() -> noPassword.get("x").block()).isInstanceOf(RuntimeException.class);
            factory.destroy();
        } finally {
            secured.destroy();
            secured = null;
            store = storeFor(props(6379, null));
        }
    }

    private static Process startSecuredRedis(int port, String password) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("redis-server", "--port", String.valueOf(port), "--requirepass", password,
                "--save", "", "--appendonly", "no").redirectErrorStream(true).start();
        for (int i = 0; i < 50 && !TestSupport.reachable(port); i++) {
            Thread.sleep(100);
        }
        assertThat(TestSupport.reachable(port)).as("redis-server com senha subiu").isTrue();
        return process;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
