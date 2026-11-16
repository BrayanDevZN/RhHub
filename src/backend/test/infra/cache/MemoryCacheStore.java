package br.rhhub.infra.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Mono;

/** Cache em memória para os testes. failing simula o Redis fora do ar; sets guarda o prazo de cada gravação. */
public class MemoryCacheStore implements CacheStore {

    public final Map<String, String> data = new ConcurrentHashMap<>();
    public final Map<String, Duration> ttls = new ConcurrentHashMap<>();
    public final List<String> calls = new ArrayList<>();
    public volatile boolean failing;

    @Override
    public Mono<String> get(String key) {
        calls.add("get " + key);
        return failing ? Mono.error(new IllegalStateException("redis fora do ar")) : Mono.justOrEmpty(data.get(key));
    }

    @Override
    public Mono<Void> set(String key, String value, Duration ttl) {
        calls.add("set " + key);
        if (failing) {
            return Mono.error(new IllegalStateException("redis fora do ar"));
        }
        data.put(key, value);
        ttls.put(key, ttl);
        return Mono.empty();
    }

    @Override
    public Mono<Void> delete(String... keys) {
        calls.add("del " + String.join(",", keys));
        if (failing) {
            return Mono.error(new IllegalStateException("redis fora do ar"));
        }
        for (String key : keys) {
            data.remove(key);
        }
        return Mono.empty();
    }

    @Override
    public Mono<Void> deleteByPrefix(String prefix) {
        calls.add("delprefix " + prefix);
        if (failing) {
            return Mono.error(new IllegalStateException("redis fora do ar"));
        }
        data.keySet().removeIf(k -> k.startsWith(prefix));
        return Mono.empty();
    }
}
