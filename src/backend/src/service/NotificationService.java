package br.rhhub.service;

import br.rhhub.application.port.out.NotificationRepository;
import br.rhhub.application.port.out.Notifier;
import br.rhhub.application.port.out.UserRepository;
import br.rhhub.domain.model.Notification;
import br.rhhub.domain.model.User;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** O sino do app: cada usuário lê e marca como lidos só os próprios avisos. */
public class NotificationService implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Quantos avisos a lista devolve. */
    public static final int LIST_LIMIT = 30;

    /**
     * Caixa de avisos do sino: os avisos mais recentes e quantos ainda não foram lidos.
     */
    public record Inbox(List<Notification> items, long unread) {
    }

    private final NotificationRepository notifications;
    private final UserRepository users;

    /**
     * Cria o serviço de avisos (sino).
     */
    public NotificationService(NotificationRepository notifications, UserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    /**
     * Devolve a caixa de avisos de quem está logado: os {@value #LIST_LIMIT} mais recentes e o total de não lidos, buscados ao mesmo tempo.
     */
    public Mono<Inbox> inbox(User actor) {
        return Mono.zip(notifications.findByUser(actor.id(), LIST_LIMIT).collectList(),
                notifications.countUnread(actor.id()), Inbox::new);
    }

    /** Falso se o aviso não existe ou é de outro usuário (o controller responde 404 nos dois casos). */
    public Mono<Boolean> markRead(User actor, Long id) {
        return notifications.markRead(id, actor.id());
    }

    /**
     * Marca todos os avisos do usuário como lidos.
     */
    public Mono<Void> markAllRead(User actor) {
        return notifications.markAllRead(actor.id());
    }

    /**
     * Cria um aviso para um usuário. Falha ao gravar o aviso nunca atrapalha a ação principal: o erro é só registrado no log e ignorado.
     *
     * @param link rota do app para onde o clique leva (pode ser nulo).
     */
    @Override
    public Mono<Void> user(Long userId, String title, String message, String link) {
        return Mono.defer(() -> notifications.create(new Notification(null, userId, title, message, link, false, null)))
                .then()
                .onErrorResume(error -> {
                    log.warn("Aviso para o usuário {} não gravado: {}", userId, error.toString());
                    return Mono.empty();
                });
    }

    /**
     * Cria o mesmo aviso para todos os administradores (por exemplo, "Nova candidatura"). Também ignora falhas, só registrando no log.
     */
    @Override
    public Mono<Void> admins(String title, String message, String link) {
        return Flux.defer(users::findAll)
                .filter(User::isAdmin)
                .concatMap(admin -> user(admin.id(), title, message, link))
                .then()
                .onErrorResume(error -> {
                    log.warn("Aviso para os administradores não gravado: {}", error.toString());
                    return Mono.empty();
                });
    }
}
