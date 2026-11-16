package br.rhhub.infra.cache;

import java.time.Duration;
import reactor.core.publisher.Mono;

/** O armazenamento do cache (Redis em produção, memória nos testes). Só texto: quem serializa é o QueryCache. */
public interface CacheStore {

    Mono<String> get(String key);

    Mono<Void> set(String key, String value, Duration ttl);

    Mono<Void> delete(String... keys);

    /** Apaga todas as chaves que começam com o prefixo. */
    Mono<Void> deleteByPrefix(String prefix);
}
