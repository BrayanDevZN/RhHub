package br.rhhub.infra.database.adapter;

import br.rhhub.application.port.out.VacancyRepository;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.Vacancy;
import br.rhhub.infra.database.Params;
import br.rhhub.infra.database.Row;
import br.rhhub.infra.database.SqlExecutor;
import java.time.Clock;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class VacancyRepositoryAdapter implements VacancyRepository {

    private static final String COLUMNS = "id, created_by, name, description, created_at";

    private final SqlExecutor sql;
    private final Clock clock;

    public VacancyRepositoryAdapter(SqlExecutor sql, Clock clock) {
        this.sql = sql;
        this.clock = clock;
    }

    @Override
    public Mono<Vacancy> findById(Long id) {
        return sql.query("SELECT " + COLUMNS + " FROM vacancies WHERE id = :id", Params.of().add("id", id)).next()
                .map(VacancyRepositoryAdapter::toDomain);
    }

    @Override
    public Flux<Vacancy> findAll() {
        return sql.query("SELECT " + COLUMNS + " FROM vacancies ORDER BY created_at DESC, id DESC", Params.of())
                .map(VacancyRepositoryAdapter::toDomain);
    }

    @Override
    public Mono<Boolean> existsByName(String name) {
        return sql.query("SELECT 1 AS found FROM vacancies WHERE name = :name LIMIT 1", Params.of().add("name", name))
                .hasElements();
    }

    @Override
    public Mono<Vacancy> save(Vacancy v) {
        var params = Params.of()
                .add("name", v.name())
                .add("description", v.description())
                .add("created_by", v.createdBy(), Long.class);
        Flux<Row> result;
        if (v.id() == null) {
            params.add("created_at", v.createdAt() != null ? v.createdAt() : clock.instant());
            result = sql.query("""
                    INSERT INTO vacancies (created_by, name, description, created_at)
                    VALUES (:created_by, :name, :description, :created_at)
                    RETURNING\s""" + COLUMNS, params);
        } else {
            params.add("id", v.id());
            result = sql.query("""
                    UPDATE vacancies SET created_by = :created_by, name = :name, description = :description
                    WHERE id = :id RETURNING\s""" + COLUMNS, params);
        }
        return result.next()
                .map(VacancyRepositoryAdapter::toDomain)
                .switchIfEmpty(Mono.error(new DomainException.NotFound("Vaga não encontrada")))
                .onErrorMap(SqlExecutor.ConstraintViolation.class,
                        e -> new DomainException.Conflict("Já existe uma vaga com esse nome", e));
    }

    @Override
    public Mono<Void> deleteById(Long id) {
        return sql.update("DELETE FROM vacancies WHERE id = :id", Params.of().add("id", id)).then();
    }

    static Vacancy toDomain(Row r) {
        return new Vacancy(r.longValue("id"), r.string("name"), r.string("description"), r.longValue("created_by"),
                r.instant("created_at"));
    }
}
