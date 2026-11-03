package br.rhhub.infra.database.adapter;

import br.rhhub.application.port.out.VacancyPhotoRepository;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.ProfilePhoto;
import br.rhhub.infra.database.Params;
import br.rhhub.infra.database.SqlExecutor;
import java.time.Clock;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@DependsOn("tableCreator")
public class VacancyPhotoRepositoryAdapter implements VacancyPhotoRepository {

    private final SqlExecutor sql;
    private final Clock clock;

    public VacancyPhotoRepositoryAdapter(SqlExecutor sql, Clock clock) {
        this.sql = sql;
        this.clock = clock;
    }

    @Override
    public Mono<Void> save(Long vacancyId, ProfilePhoto photo) {
        return sql.update("""
                INSERT INTO vacancy_photos (vacancy_id, content, content_type, updated_at)
                VALUES (:vacancy_id, :content, :content_type, :updated_at)
                ON CONFLICT (vacancy_id) DO UPDATE SET content = excluded.content,
                    content_type = excluded.content_type, updated_at = excluded.updated_at""",
                Params.of().add("vacancy_id", vacancyId).add("content", photo.content())
                        .add("content_type", photo.contentType()).add("updated_at", clock.instant()))
                .onErrorMap(SqlExecutor.ConstraintViolation.class, e -> new DomainException.NotFound("Vaga não encontrada"))
                .then();
    }

    @Override
    public Mono<ProfilePhoto> find(Long vacancyId) {
        return sql.query("SELECT content, content_type FROM vacancy_photos WHERE vacancy_id = :vacancy_id",
                Params.of().add("vacancy_id", vacancyId)).next()
                .map(r -> new ProfilePhoto(r.bytes("content"), r.string("content_type")));
    }

    @Override
    public Mono<Void> delete(Long vacancyId) {
        return sql.update("DELETE FROM vacancy_photos WHERE vacancy_id = :vacancy_id",
                Params.of().add("vacancy_id", vacancyId)).then();
    }
}
