package br.rhhub.application.web;

import br.rhhub.application.web.dto.Views;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.service.NotificationService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** O sino do app. Cada usuário só enxerga e marca os próprios avisos. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;
    private final WebSupport web;

    public NotificationController(NotificationService notifications, WebSupport web) {
        this.notifications = notifications;
        this.web = web;
    }

    @GetMapping
    Mono<Views.InboxView> inbox(ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(notifications::inbox).map(Views::inbox);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> readAll(ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(notifications::markAllRead);
    }

    @PostMapping("/{id}/read")
    Mono<Map<String, Object>> read(@PathVariable String id, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> notifications.markRead(actor, WebSupport.parseId(id, "Id")))
                .flatMap(found -> found ? Mono.just(Map.<String, Object>of("status", true))
                        : Mono.error(new DomainException.NotFound("Aviso não encontrado.")));
    }
}
