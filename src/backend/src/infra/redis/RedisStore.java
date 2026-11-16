package br.rhhub.infra.redis;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Operações do Redis, todas assíncronas (Mono). É a base dos adapters que vêm depois (códigos de verificação,
 * refresh token, rate limit): eles só montam as chaves e usam estes métodos.
 */
@Component
public class RedisStore {

    /** Soma 1 e, só na primeira vez, define a expiração: nunca deixa contador sem prazo. */
    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>("""
            local total = redis.call('INCR', KEYS[1])
            if total == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return total
            """, Long.class);

    private final ReactiveStringRedisTemplate redis;

    public RedisStore(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Grava sem expiração. */
    public Mono<Void> set(String key, String value) {
        return redis.opsForValue().set(key, value).then();
    }

    /** Grava com expiração. */
    public Mono<Void> set(String key, String value, Duration ttl) {
        return redis.opsForValue().set(key, value, ttl).then();
    }

    /** Grava só se a chave ainda não existir. Devolve true se gravou. */
    public Mono<Boolean> setIfAbsent(String key, String value, Duration ttl) {
        return redis.opsForValue().setIfAbsent(key, value, ttl);
    }

    /** O valor, ou vazio se a chave não existe ou expirou. */
    public Mono<String> get(String key) {
        return redis.opsForValue().get(key);
    }

    public Mono<Boolean> exists(String key) {
        return redis.hasKey(key);
    }

    /** Apaga as chaves e devolve quantas existiam. */
    public Mono<Long> delete(String... keys) {
        return keys.length == 0 ? Mono.just(0L) : redis.delete(keys);
    }

    /** Soma 1 (começa em 1 se não existir) e devolve o total. Sem expiração. */
    public Mono<Long> increment(String key) {
        return redis.opsForValue().increment(key);
    }

    /** Soma 1 e, se foi a primeira vez, define a expiração, tudo de forma atômica. Devolve o total. */
    public Mono<Long> incrementWithTtl(String key, Duration ttl) {
        return redis.execute(INCREMENT_WITH_TTL, List.of(key), List.of(String.valueOf(ttl.toMillis()))).next();
    }

    public Mono<Boolean> expire(String key, Duration ttl) {
        return redis.expire(key, ttl);
    }

    /** Tempo que falta para expirar. Vazio se a chave não existe ou não tem expiração. */
    public Mono<Duration> ttl(String key) {
        return redis.getExpire(key).filter(Duration::isPositive);
    }

    /** Responde "PONG" se o Redis está de pé. */
    public Mono<String> ping() {
        return redis.execute(connection -> connection.ping()).next();
    }
}
