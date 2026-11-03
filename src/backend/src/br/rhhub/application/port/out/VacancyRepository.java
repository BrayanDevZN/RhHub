package br.rhhub.application.port.out;

import br.rhhub.domain.model.Vacancy;
import java.util.List;
import java.util.Optional;

public interface VacancyRepository {

    Optional<Vacancy> findById(Long id);

    List<Vacancy> findAll();

    boolean existsByName(String name);

    /** Insere (id nulo) ou atualiza. Lança DomainException.Conflict se o nome já existir. */
    Vacancy save(Vacancy vacancy);

    void deleteById(Long id);
}
