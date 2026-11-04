package br.rhhub.infra.cache;

import static org.assertj.core.api.Assertions.assertThat;

import br.rhhub.testing.UnitTest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@UnitTest
class QueryCacheTest {

    record Item(Long id, String name) {
    }

    private MemoryCacheStore store;
    private QueryCache cache;
    private final AtomicInteger loads = new AtomicInteger();

    @BeforeEach
    void setUp() {
        store = new MemoryCacheStore();
        cache = new QueryCache(store);
        loads.set(0);
    }

    private Mono<Item> load() {
        loads.incrementAndGet();
        return Mono.just(new Item(1L, "a"));
    }

    @Test
    void theSecondReadComesFromTheCacheWithTheTtl() {
        assertThat(cache.one("k", Duration.ofMinutes(5), Item.class, this::load).block()).isEqualTo(new Item(1L, "a"));
        assertThat(cache.one("k", Duration.ofMinutes(5), Item.class, this::load).block()).isEqualTo(new Item(1L, "a"));
        assertThat(loads).hasValue(1);
        assertThat(store.ttls.get("k")).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void anEmptyResultIsNotCachedButAnEmptyListIs() {
        assertThat(cache.one("none", Duration.ofMinutes(1), Item.class, () -> {
            loads.incrementAndGet();
            return Mono.<Item>empty();
        }).block()).isNull();
        cache.one("none", Duration.ofMinutes(1), Item.class, () -> {
            loads.incrementAndGet();
            return Mono.<Item>empty();
        }).block();
        assertThat(loads).hasValue(2); // vazio nunca fica guardado: o próximo leitor pergunta ao banco

        for (int i = 0; i < 2; i++) {
            assertThat(cache.list("empty", Duration.ofMinutes(1), Item.class, () -> {
                loads.incrementAndGet();
                return Flux.<Item>empty();
            }).collectList().block()).isEmpty();
        }
        assertThat(loads).hasValue(3); // a lista vazia é um resultado válido: só a primeira foi ao banco
    }

    @Test
    void listsKeepTheirOrderAndEvictionForcesAFreshRead() {
        var items = List.of(new Item(2L, "b"), new Item(1L, "a"));
        assertThat(cache.list("l", Duration.ofMinutes(1), Item.class, () -> Flux.fromIterable(items)).collectList().block())
                .containsExactlyElementsOf(items);
        assertThat(cache.list("l", Duration.ofMinutes(1), Item.class, () -> Flux.error(new IllegalStateException("não devia ir ao banco")))
                .collectList().block()).containsExactlyElementsOf(items);

        cache.evict("l").block();
        assertThat(store.data).doesNotContainKey("l");
        assertThat(cache.list("l", Duration.ofMinutes(1), Item.class, () -> Flux.just(new Item(3L, "novo"))).collectList().block())
                .containsExactly(new Item(3L, "novo"));
    }

    @Test
    void evictPrefixClearsOnlyThatFamily() {
        store.data.put("cache:vacancy:all", "[]");
        store.data.put("cache:vacancy:id:1", "{}");
        store.data.put("cache:user:identity:x", "{}");
        cache.evictPrefix("cache:vacancy:").block();
        assertThat(store.data).containsOnlyKeys("cache:user:identity:x");
    }

    @Test
    void aBrokenRedisNeverBreaksTheReadAndIsSkippedForAWhile() {
        store.failing = true;
        assertThat(cache.one("k", Duration.ofMinutes(1), Item.class, this::load).block()).isEqualTo(new Item(1L, "a"));
        assertThat(loads).hasValue(1);
        int callsAfterFailure = store.calls.size();

        // o cache fica pausado: as próximas leituras nem tocam no Redis
        assertThat(cache.one("k", Duration.ofMinutes(1), Item.class, this::load).block()).isEqualTo(new Item(1L, "a"));
        assertThat(loads).hasValue(2);
        assertThat(store.calls).hasSize(callsAfterFailure);

        // mas apagar continua tentando, porque é o que evita dado velho; e a falha também não estoura
        cache.evict("k").block();
        assertThat(store.calls).hasSize(callsAfterFailure + 1);
    }

    @Test
    void anUnreadableCachedValueIsDroppedAndReloaded() {
        store.data.put("k", "isto não é json");
        assertThat(cache.one("k", Duration.ofMinutes(1), Item.class, this::load).block()).isEqualTo(new Item(1L, "a"));
        assertThat(loads).hasValue(1);
        assertThat(store.data.get("k")).contains("\"name\":\"a\"");
    }
}
