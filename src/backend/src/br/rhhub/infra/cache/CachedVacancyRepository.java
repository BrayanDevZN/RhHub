package br.rhhub.infra.cache;

import br.rhhub.application.port.out.VacancyRepository;
import br.rhhub.domain.model.Vacancy;
import br.rhhub.infra.database.adapter.VacancyRepositoryAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Cache-aside das vagas (a lista e cada uma por id), lidas por todo candidato. Dado público, sem credenciais. Salvar ou
 * apagar uma vaga apaga todas as entradas de vaga. O contador de candidatos também vive aqui, então criar ou apagar
 * candidatura apaga o cache de vagas (ver CachedResumeRepository). A expiração é conferida no código, não no cache.
 */
@Repository
@Primary
@DependsOn("tableCreator")
@ConditionalOnProperty(name = "cache.enabled", havingValue = "true", matchIfMissing = true)
public class CachedVacancyRepository implements VacancyRepository {

    private final VacancyRepository db;
    private final QueryCache cache;

    @Autowired
    public CachedVacancyRepository(VacancyRepositoryAdapter db, QueryCache cache) {
        this((VacancyRepository) db, cache);
    }

    /** Para testes: qualquer implementação da porta serve de origem. */
    public CachedVacancyRepository(VacancyRepository db, QueryCache cache) {
        this.db = db;
        this.cache = cache;
    }

    @Override
    public Mono<Vacancy> findById(Long id) {
        return cache.one(CacheKeys.vacancy(id), CacheKeys.LONG, Vacancy.class, () -> db.findById(id));
    }

    @Override
    public Flux<Vacancy> findAll() {
        return cache.list(CacheKeys.VACANCY_ALL, CacheKeys.LONG, Vacancy.class, db::findAll);
    }

    @Override
    public Mono<Boolean> existsByName(String name) {
        return db.existsByName(name);
    }

    @Override
    public Mono<Vacancy> save(Vacancy vacancy) {
        return db.save(vacancy).flatMap(saved -> cache.evictPrefix(CacheKeys.VACANCY_PREFIX).thenReturn(saved));
    }

    @Override
    public Mono<Void> deleteById(Long id) {
        return db.deleteById(id).then(cache.evictPrefix(CacheKeys.VACANCY_PREFIX));
    }
}
