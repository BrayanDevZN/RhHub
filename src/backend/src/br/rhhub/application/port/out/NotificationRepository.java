package br.rhhub.application.port.out;

import br.rhhub.domain.model.Notification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface NotificationRepository {

    Mono<Notification> create(Notification notification);

    /** As mais recentes primeiro. */
    Flux<Notification> findByUser(Long userId, int limit);

    Mono<Long> countUnread(Long userId);

    /** Marca como lida só se for do usuário. Devolve false se não existir ou for de outro. */
    Mono<Boolean> markRead(Long id, Long userId);

    Mono<Void> markAllRead(Long userId);
}
