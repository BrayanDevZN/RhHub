package br.rhhub.infra.database.adapter;

import br.rhhub.application.port.out.NotificationRepository;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.Notification;
import br.rhhub.infra.database.Params;
import br.rhhub.infra.database.Row;
import br.rhhub.infra.database.SqlExecutor;
import java.time.Clock;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Adapter: implementa NotificationRepository com SQL (PostgreSQL ou SQLite). */
@Repository
@DependsOn("tableCreator")
public class NotificationRepositoryAdapter implements NotificationRepository {

    private static final String COLUMNS = "id, user_id, title, message, link, is_read, created_at";

    private final SqlExecutor sql;
    private final Clock clock;

    /**
     * Cria o adapter com o executor de SQL e o relógio.
     */
    public NotificationRepositoryAdapter(SqlExecutor sql, Clock clock) {
        this.sql = sql;
        this.clock = clock;
    }

    /**
     * Grava um aviso novo (começa como não lido).
     *
     * @throws DomainException.NotFound se o usuário do aviso não existe.
     */
    @Override
    public Mono<Notification> create(Notification n) {
        var params = Params.of()
                .add("user_id", n.userId())
                .add("title", n.title())
                .add("message", n.message())
                .add("link", n.link(), String.class)
                .add("created_at", n.createdAt() != null ? n.createdAt() : clock.instant());
        return sql.query("""
                INSERT INTO notifications (user_id, title, message, link, created_at)
                VALUES (:user_id, :title, :message, :link, :created_at)
                RETURNING\s""" + COLUMNS, params)
                .next()
                .map(NotificationRepositoryAdapter::toDomain)
                .onErrorMap(SqlExecutor.ConstraintViolation.class,
                        e -> new DomainException.NotFound("Usuário não encontrado"));
    }

    /**
     * Lista os avisos mais recentes do usuário, no máximo {@code limit}.
     */
    @Override
    public Flux<Notification> findByUser(Long userId, int limit) {
        return sql.query("SELECT " + COLUMNS + " FROM notifications WHERE user_id = :user_id "
                + "ORDER BY created_at DESC, id DESC LIMIT :limit",
                Params.of().add("user_id", userId).add("limit", limit)).map(NotificationRepositoryAdapter::toDomain);
    }

    /**
     * Conta os avisos ainda não lidos do usuário (o número do sino). Sem avisos devolve 0.
     */
    @Override
    public Mono<Long> countUnread(Long userId) {
        return sql.query("SELECT COUNT(*) AS total FROM notifications WHERE user_id = :user_id AND is_read = :unread",
                Params.of().add("user_id", userId).add("unread", false)).next().map(row -> row.longValue("total"))
                .defaultIfEmpty(0L);
    }

    /**
     * Marca um aviso como lido, só se ele for do usuário (a condição user_id impede mexer no aviso de outra pessoa).
     *
     * @return true se atualizou; false se o aviso não existe ou é de outro usuário.
     */
    @Override
    public Mono<Boolean> markRead(Long id, Long userId) {
        return sql.query("UPDATE notifications SET is_read = :read WHERE id = :id AND user_id = :user_id RETURNING id",
                Params.of().add("read", true).add("id", id).add("user_id", userId)).hasElements();
    }

    /**
     * Marca todos os avisos não lidos do usuário como lidos.
     */
    @Override
    public Mono<Void> markAllRead(Long userId) {
        return sql.update("UPDATE notifications SET is_read = :read WHERE user_id = :user_id AND is_read = :unread",
                Params.of().add("read", true).add("user_id", userId).add("unread", false)).then();
    }

    /**
     * Converte uma linha em {@link Notification}.
     */
    static Notification toDomain(Row r) {
        return new Notification(r.longValue("id"), r.longValue("user_id"), r.string("title"), r.string("message"),
                r.string("link"), r.bool("is_read"), r.instant("created_at"));
    }
}
