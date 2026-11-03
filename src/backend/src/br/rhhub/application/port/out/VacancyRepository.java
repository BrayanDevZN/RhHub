package br.rhhub.application.port.out;

import br.rhhub.domain.model.Vacancy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface VacancyRepository {

    Mono<Vacancy> findById(Long id);

    /** Mais novas primeiro. */
    Flux<Vacancy> findAll();

    Mono<Boolean> existsByName(String name);

    /** Insere (id nulo) ou atualiza. Erro DomainException.Conflict se o nome já existir. */
    Mono<Vacancy> save(Vacancy vacancy);

    Mono<Void> deleteById(Long id);
}
