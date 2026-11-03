package br.rhhub.infra.database.adapter;

import br.rhhub.application.port.out.VacancyRepository;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.Vacancy;
import br.rhhub.domain.rule.Stages;
import br.rhhub.infra.database.Params;
import br.rhhub.infra.database.Row;
import br.rhhub.infra.database.SqlExecutor;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@DependsOn("tableCreator")
public class VacancyRepositoryAdapter implements VacancyRepository {

    /** has_photo vem de uma subconsulta (boolean no PostgreSQL, 0/1 no SQLite: Row.bool entende os dois). */
    private static final String COLUMNS = "id, created_by, name, description, requirements, responsibilities, stages, expires_at, created_at, "
            + "EXISTS (SELECT 1 FROM vacancy_photos p WHERE p.vacancy_id = vacancies.id) AS has_photo, "
            + "(SELECT COUNT(*) FROM resumes r WHERE r.vacancy_id = vacancies.id) AS applicants";

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
                .add("requirements", v.requirements(), String.class)
                .add("responsibilities", v.responsibilities(), String.class)
                .add("stages", Stages.join(v.stages()))
                .add("expires_at", v.expiresAt(), Instant.class)
                .add("created_by", v.createdBy(), Long.class);
        Flux<Row> result;
        if (v.id() == null) {
            params.add("created_at", v.createdAt() != null ? v.createdAt() : clock.instant());
            result = sql.query("""
                    INSERT INTO vacancies (created_by, name, description, requirements, responsibilities, stages, expires_at, created_at)
                    VALUES (:created_by, :name, :description, :requirements, :responsibilities, :stages, :expires_at,
                            :created_at)
                    RETURNING id""", params);
        } else {
            params.add("id", v.id());
            result = sql.query("""
                    UPDATE vacancies SET created_by = :created_by, name = :name, description = :description,
                                         requirements = :requirements, responsibilities = :responsibilities,
                                         stages = :stages, expires_at = :expires_at
                    WHERE id = :id RETURNING id""", params);
        }
        // O RETURNING só traz o id; a vaga completa (com has_photo) vem de uma leitura, igual nos dois bancos.
        return result.next()
                .flatMap(row -> findById(row.longValue("id")))
                .switchIfEmpty(Mono.error(new DomainException.NotFound("Vaga não encontrada")))
                .onErrorMap(SqlExecutor.ConstraintViolation.class,
                        e -> new DomainException.Conflict("Já existe uma vaga com esse nome", e));
    }

    @Override
    public Mono<Void> deleteById(Long id) {
        return sql.update("DELETE FROM vacancies WHERE id = :id", Params.of().add("id", id)).then();
    }

    static Vacancy toDomain(Row r) {
        return new Vacancy(r.longValue("id"), r.string("name"), r.string("description"), r.string("requirements"),
                r.string("responsibilities"), r.longValue("created_by"), r.instant("created_at"), r.bool("has_photo"),
                Stages.parse(r.string("stages")), r.instant("expires_at"), r.intValue("applicants"));
    }
}
