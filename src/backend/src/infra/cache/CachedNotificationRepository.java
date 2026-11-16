package br.rhhub.infra.cache;

import br.rhhub.application.port.out.NotificationRepository;
import br.rhhub.domain.model.Notification;
import br.rhhub.infra.database.adapter.NotificationRepositoryAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * O sino consulta a lista e o contador de não lidos de cada usuário a cada 30 s, quase sempre sem novidade. Os dois
 * ficam em cache por usuário (60 s); criar um aviso ou marcar como lido apaga as entradas daquele usuário.
 */
@Repository
@Primary
@DependsOn("tableCreator")
@ConditionalOnProperty(name = "cache.enabled", havingValue = "true", matchIfMissing = true)
public class CachedNotificationRepository implements NotificationRepository {

    private final NotificationRepository db;
    private final QueryCache cache;

    @Autowired
    public CachedNotificationRepository(NotificationRepositoryAdapter db, QueryCache cache) {
        this((NotificationRepository) db, cache);
    }

    /** Para testes: qualquer implementação da porta serve de origem. */
    public CachedNotificationRepository(NotificationRepository db, QueryCache cache) {
        this.db = db;
        this.cache = cache;
    }

    @Override
    public Flux<Notification> findByUser(Long userId, int limit) {
        return cache.list(CacheKeys.notificationList(userId, limit), CacheKeys.SHORT, Notification.class,
                () -> db.findByUser(userId, limit));
    }

    @Override
    public Mono<Long> countUnread(Long userId) {
        return cache.one(CacheKeys.notificationUnread(userId), CacheKeys.SHORT, Long.class, () -> db.countUnread(userId));
    }

    @Override
    public Mono<Notification> create(Notification notification) {
        return db.create(notification)
                .flatMap(saved -> cache.evictPrefix(CacheKeys.notificationPrefix(saved.userId())).thenReturn(saved));
    }

    @Override
    public Mono<Boolean> markRead(Long id, Long userId) {
        return db.markRead(id, userId)
                .flatMap(found -> cache.evictPrefix(CacheKeys.notificationPrefix(userId)).thenReturn(found));
    }

    @Override
    public Mono<Void> markAllRead(Long userId) {
        return db.markAllRead(userId).then(cache.evictPrefix(CacheKeys.notificationPrefix(userId)));
    }
}
