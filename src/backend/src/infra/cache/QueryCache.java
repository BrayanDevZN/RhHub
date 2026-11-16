package br.rhhub.infra.cache;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Cache-aside: lê do cache; se não achar, lê do banco e grava no cache com prazo (TTL). Quem altera ou apaga um dado
 * chama {@link #evict} (ou {@link #evictPrefix}) para o próximo leitor buscar o valor novo.
 *
 * <p>O cache é só um atalho: nunca pode derrubar nem atrasar a aplicação. Toda falha do Redis (erro, lentidão) vira
 * "não achei" e a leitura segue pelo banco. Depois de uma falha, o cache fica desligado por 30 s para não pagar o
 * tempo limite a cada requisição. O TTL curto limita o estrago se uma invalidação falhar.
 *
 * <p>Valores vazios do banco (não encontrado) não são guardados.
 */
@Component
public class QueryCache {

    private static final Logger log = LoggerFactory.getLogger(QueryCache.class);

    static final Duration OP_TIMEOUT = Duration.ofMillis(300);
    static final long PAUSE_MS = 30_000;

    private final CacheStore store;
    private final ObjectMapper json = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final AtomicLong pausedUntil = new AtomicLong();

    /**
     * Cria o cache sobre um armazenamento (na prática, o Redis).
     */
    public QueryCache(CacheStore store) {
        this.store = store;
    }

    /** Um valor. Vazio do loader não é guardado. */
    public <T> Mono<T> one(String key, Duration ttl, Class<T> type, Supplier<Mono<T>> loader) {
        return this.<T>read(key, json.constructType(type)).switchIfEmpty(Mono.defer(() -> loader.get()
                .flatMap(value -> write(key, value, ttl).thenReturn(value))));
    }

    /** Uma lista. A lista vazia também é guardada (é um resultado válido). */
    public <T> Flux<T> list(String key, Duration ttl, Class<T> element, Supplier<Flux<T>> loader) {
        JavaType listType = json.getTypeFactory().constructCollectionType(List.class, element);
        return this.<List<T>>read(key, listType).switchIfEmpty(Mono.defer(() -> loader.get().collectList()
                .flatMap(values -> write(key, values, ttl).thenReturn(values)))).flatMapMany(Flux::fromIterable);
    }

    /**
     * Apaga chaves do cache para forçar a próxima leitura a ir ao banco. Falha do Redis é ignorada (só registrada).
     */
    public Mono<Void> evict(String... keys) {
        return guarded(store.delete(keys), "apagar " + String.join(",", keys));
    }

    /**
     * Apaga todas as chaves que começam com o prefixo (por exemplo, todas as vagas).
     */
    public Mono<Void> evictPrefix(String prefix) {
        return guarded(store.deleteByPrefix(prefix), "apagar o prefixo " + prefix);
    }

    /**
     * Lê e converte o valor do cache. Qualquer falha do Redis (erro ou lentidão acima de 300 ms) conta como "não achei" e liga a pausa do cache. Valor ilegível é apagado e ignorado.
     */
    private <T> Mono<T> read(String key, JavaType type) {
        if (paused()) {
            return Mono.empty();
        }
        return store.get(key).timeout(OP_TIMEOUT).flatMap(text -> {
            try {
                return Mono.<T>just(json.readValue(text, type));
            } catch (Exception e) {
                log.warn("Cache {}: valor ilegível, ignorado ({})", key, e.toString());
                return store.delete(key).onErrorResume(x -> Mono.empty()).then(Mono.<T>empty());
            }
        }).onErrorResume(error -> {
            trip("ler " + key, error);
            return Mono.empty();
        });
    }

    /**
     * Converte o valor em JSON e guarda com prazo. Falha vira só um aviso e liga a pausa, nunca derruba a requisição.
     */
    private Mono<Void> write(String key, Object value, Duration ttl) {
        if (paused()) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> json.writeValueAsString(value))
                .flatMap(text -> store.set(key, text, ttl).timeout(OP_TIMEOUT))
                .onErrorResume(error -> {
                    trip("gravar " + key, error);
                    return Mono.empty();
                });
    }

    /** A invalidação não respeita a pausa: apagar é o que evita dado velho, então sempre tenta. */
    private Mono<Void> guarded(Mono<Void> operation, String what) {
        return operation.timeout(OP_TIMEOUT).onErrorResume(error -> {
            trip(what, error);
            return Mono.empty();
        });
    }

    /**
     * Diz se o cache está em pausa. Depois de uma falha, ele fica desligado por 30 segundos para não pagar o tempo limite em cada requisição.
     */
    private boolean paused() {
        return System.currentTimeMillis() < pausedUntil.get();
    }

    /**
     * Registra a falha do cache no log (uma vez por pausa) e liga a pausa de 30 segundos.
     */
    private void trip(String what, Throwable error) {
        if (!paused()) {
            log.warn("Cache indisponível ({}): {}. Seguindo sem cache por {} s", what, error.toString(), PAUSE_MS / 1000);
        }
        pausedUntil.set(System.currentTimeMillis() + PAUSE_MS);
    }
}
