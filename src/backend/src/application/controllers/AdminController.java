package br.rhhub.application.web;

import br.rhhub.application.web.dto.Views;
import br.rhhub.service.AdminService;
import br.rhhub.service.ProfilePhotoService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Rotas /api/admin/users: só administradores listam, consultam, mudam o papel e apagam usuários. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminController {

    /**
     * Corpo da requisição de mudar papel: {"role": "user" ou "admin"}.
     */
    public record RoleRequest(String role) {
    }

    private final AdminService admin;
    private final ProfilePhotoService photos;
    private final WebSupport web;

    /**
     * Recebe os serviços de que as rotas precisam; o Spring os injeta.
     */
    public AdminController(AdminService admin, ProfilePhotoService photos, WebSupport web) {
        this.admin = admin;
        this.photos = photos;
        this.web = web;
    }

    /**
     * GET /api/admin/users. Lista todos os usuários (só administradores). Devolve a versão pública de cada um, sem dados sensíveis.
     */
    @GetMapping
    Mono<List<Views.UserView>> list(ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> admin.listUsers(actor).map(u -> Views.user(u, null)).collectList());
    }

    /**
     * GET /api/admin/users/{publicId}. Mostra um usuário pelo identificador público (só administradores).
     */
    @GetMapping("/{publicId}")
    Mono<Views.UserView> get(@PathVariable String publicId, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> admin.getUser(actor, WebSupport.parseUuid(publicId)))
                .map(u -> Views.user(u, null));
    }

    /**
     * PATCH /api/admin/users/{publicId}/role. Muda o papel do usuário para user ou admin. As regras de quem pode promover ou rebaixar estão em AccessPolicy.
     */
    @PatchMapping("/{publicId}/role")
    Mono<Views.UserView> setRole(@PathVariable String publicId, @RequestBody RoleRequest body, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> admin.setRole(actor, WebSupport.parseUuid(publicId), body.role()))
                .map(u -> Views.user(u, null));
    }

    /**
     * DELETE /api/admin/users/{publicId}. Apaga a conta de um usuário (responde 204). O admin raiz não pode ser apagado.
     */
    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@PathVariable String publicId, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> admin.deleteUser(actor, WebSupport.parseUuid(publicId)));
    }

    /**
     * GET /api/admin/users/{publicId}/photo. Devolve a foto de perfil de qualquer usuário (só administradores).
     */
    @GetMapping("/{publicId}/photo")
    Mono<ResponseEntity<byte[]>> photo(@PathVariable String publicId, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> photos.get(actor, WebSupport.parseUuid(publicId)))
                .map(UserController::image);
    }
}
