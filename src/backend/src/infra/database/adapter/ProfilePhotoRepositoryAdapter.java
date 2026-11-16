package br.rhhub.infra.database.adapter;

import br.rhhub.application.port.out.ProfilePhotoRepository;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.ProfilePhoto;
import br.rhhub.infra.database.Params;
import br.rhhub.infra.database.SqlExecutor;
import java.time.Clock;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/** Adapter: guarda a foto de perfil na tabela user_photos. */
@Repository
@DependsOn("tableCreator")
public class ProfilePhotoRepositoryAdapter implements ProfilePhotoRepository {

    private final SqlExecutor sql;
    private final Clock clock;

    /**
     * Cria o adapter com o executor de SQL e o relógio.
     */
    public ProfilePhotoRepositoryAdapter(SqlExecutor sql, Clock clock) {
        this.sql = sql;
        this.clock = clock;
    }

    /**
     * Grava a foto de perfil. Se o usuário já tem foto, troca (INSERT ... ON CONFLICT DO UPDATE), então há sempre no máximo uma por usuário.
     *
     * @throws DomainException.NotFound se o usuário não existe.
     */
    @Override
    public Mono<Void> save(Long userId, ProfilePhoto photo) {
        return sql.update("""
                INSERT INTO user_photos (user_id, content, content_type, updated_at)
                VALUES (:user_id, :content, :content_type, :updated_at)
                ON CONFLICT (user_id) DO UPDATE SET content = excluded.content,
                    content_type = excluded.content_type, updated_at = excluded.updated_at""",
                Params.of().add("user_id", userId).add("content", photo.content())
                        .add("content_type", photo.contentType()).add("updated_at", clock.instant()))
                .onErrorMap(SqlExecutor.ConstraintViolation.class, e -> new DomainException.NotFound("Usuário não encontrado"))
                .then();
    }

    /**
     * Busca a foto (bytes e tipo).
     *
     * @return a foto, ou vazio se o usuário não tem.
     */
    @Override
    public Mono<ProfilePhoto> find(Long userId) {
        return sql.query("SELECT content, content_type FROM user_photos WHERE user_id = :user_id",
                Params.of().add("user_id", userId)).next()
                .map(r -> new ProfilePhoto(r.bytes("content"), r.string("content_type")));
    }

    /**
     * Diz se o usuário tem foto, sem carregar a imagem.
     */
    @Override
    public Mono<Boolean> exists(Long userId) {
        return sql.query("SELECT 1 AS found FROM user_photos WHERE user_id = :user_id",
                Params.of().add("user_id", userId)).hasElements();
    }

    /**
     * Remove a foto do usuário (não faz nada se não tem).
     */
    @Override
    public Mono<Void> delete(Long userId) {
        return sql.update("DELETE FROM user_photos WHERE user_id = :user_id", Params.of().add("user_id", userId)).then();
    }
}
