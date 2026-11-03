package br.rhhub.application.port.out;

import br.rhhub.domain.model.DeadLetter;
import br.rhhub.domain.model.DeadLetterStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DeadLetterRepository {

    /**
     * Grava a falha. Se já existir uma com o mesmo (topic, partition, offset), devolve a que já estava gravada,
     * então o mesmo job morto nunca vira dois registros.
     */
    Mono<DeadLetter> save(DeadLetter deadLetter);

    Mono<DeadLetter> findById(Long id);

    /** Histórico de falhas de uma chave (por exemplo, de uma candidatura), mais novas primeiro. */
    Flux<DeadLetter> findByKey(String topic, String key);

    /** Filtro opcional: passe null para listar todas. Mais novas primeiro. */
    Flux<DeadLetter> findAll(DeadLetterStatus status);

    /** Marca como resolvida. Erro DomainException.NotFound se não existir. */
    Mono<DeadLetter> markResolved(Long id);
}
