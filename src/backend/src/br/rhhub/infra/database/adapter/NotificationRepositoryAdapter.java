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

@Repository
@DependsOn("tableCreator")
public class NotificationRepositoryAdapter implements NotificationRepository {

    private static final String COLUMNS = "id, user_id, title, message, link, is_read, created_at";

    private final SqlExecutor sql;
    private final Clock clock;

    public NotificationRepositoryAdapter(SqlExecutor sql, Clock clock) {
        this.sql = sql;
        this.clock = clock;
    }

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

    @Override
    public Flux<Notification> findByUser(Long userId, int limit) {
        return sql.query("SELECT " + COLUMNS + " FROM notifications WHERE user_id = :user_id "
                + "ORDER BY created_at DESC, id DESC LIMIT :limit",
                Params.of().add("user_id", userId).add("limit", limit)).map(NotificationRepositoryAdapter::toDomain);
    }

    @Override
    public Mono<Long> countUnread(Long userId) {
        return sql.query("SELECT COUNT(*) AS total FROM notifications WHERE user_id = :user_id AND is_read = :unread",
                Params.of().add("user_id", userId).add("unread", false)).next().map(row -> row.longValue("total"))
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Boolean> markRead(Long id, Long userId) {
        return sql.query("UPDATE notifications SET is_read = :read WHERE id = :id AND user_id = :user_id RETURNING id",
                Params.of().add("read", true).add("id", id).add("user_id", userId)).hasElements();
    }

    @Override
    public Mono<Void> markAllRead(Long userId) {
        return sql.update("UPDATE notifications SET is_read = :read WHERE user_id = :user_id AND is_read = :unread",
                Params.of().add("read", true).add("user_id", userId).add("unread", false)).then();
    }

    static Notification toDomain(Row r) {
        return new Notification(r.longValue("id"), r.longValue("user_id"), r.string("title"), r.string("message"),
                r.string("link"), r.bool("is_read"), r.instant("created_at"));
    }
}
