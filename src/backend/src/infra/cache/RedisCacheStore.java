package br.rhhub.infra.cache;

import br.rhhub.infra.redis.RedisStore;
import java.time.Duration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Cache no Redis. As chaves levam o prefixo "cache:" para nunca se misturarem com códigos, sessões e rate limit. */
@Component
public class RedisCacheStore implements CacheStore {

    private final RedisStore store;
    private final ReactiveStringRedisTemplate redis;

    /**
     * Cria o armazenamento de cache sobre o Redis.
     */
    public RedisCacheStore(RedisStore store, ReactiveStringRedisTemplate redis) {
        this.store = store;
        this.redis = redis;
    }

    /**
     * Lê o texto da chave no Redis.
     */
    @Override
    public Mono<String> get(String key) {
        return store.get(key);
    }

    /**
     * Guarda o texto no Redis com prazo de expiração.
     */
    @Override
    public Mono<Void> set(String key, String value, Duration ttl) {
        return store.set(key, value, ttl);
    }

    /**
     * Apaga as chaves no Redis.
     */
    @Override
    public Mono<Void> delete(String... keys) {
        return store.delete(keys).then();
    }

    /** SCAN (nunca KEYS): não trava o Redis. As famílias de chave do cache são pequenas. */
    @Override
    public Mono<Void> deleteByPrefix(String prefix) {
        return redis.scan(ScanOptions.scanOptions().match(prefix + "*").count(200).build())
                .buffer(100)
                .concatMap(batch -> store.delete(batch.toArray(String[]::new)))
                .then();
    }
}
