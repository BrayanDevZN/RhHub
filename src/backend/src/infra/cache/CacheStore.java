package br.rhhub.infra.cache;

import java.time.Duration;
import reactor.core.publisher.Mono;

/** O armazenamento do cache (Redis em produção, memória nos testes). Só texto: quem serializa é o QueryCache. */
public interface CacheStore {

    /**
     * Lê o texto guardado numa chave.
     *
     * @return o texto, ou vazio se a chave não existe ou expirou.
     */
    Mono<String> get(String key);

    /**
     * Guarda um texto numa chave com prazo de validade; passado o prazo, ele some sozinho.
     */
    Mono<Void> set(String key, String value, Duration ttl);

    /**
     * Apaga as chaves informadas (as que não existem são ignoradas).
     */
    Mono<Void> delete(String... keys);

    /** Apaga todas as chaves que começam com o prefixo. */
    Mono<Void> deleteByPrefix(String prefix);
}
