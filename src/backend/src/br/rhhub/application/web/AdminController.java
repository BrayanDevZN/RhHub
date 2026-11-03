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

@RestController
@RequestMapping("/api/admin/users")
public class AdminController {

    public record RoleRequest(String role) {
    }

    private final AdminService admin;
    private final ProfilePhotoService photos;
    private final WebSupport web;

    public AdminController(AdminService admin, ProfilePhotoService photos, WebSupport web) {
        this.admin = admin;
        this.photos = photos;
        this.web = web;
    }

    @GetMapping
    Mono<List<Views.UserView>> list(ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> admin.listUsers(actor).map(u -> Views.user(u, null)).collectList());
    }

    @GetMapping("/{publicId}")
    Mono<Views.UserView> get(@PathVariable String publicId, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> admin.getUser(actor, WebSupport.parseUuid(publicId)))
                .map(u -> Views.user(u, null));
    }

    @PatchMapping("/{publicId}/role")
    Mono<Views.UserView> setRole(@PathVariable String publicId, @RequestBody RoleRequest body, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> admin.setRole(actor, WebSupport.parseUuid(publicId), body.role()))
                .map(u -> Views.user(u, null));
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@PathVariable String publicId, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> admin.deleteUser(actor, WebSupport.parseUuid(publicId)));
    }

    @GetMapping("/{publicId}/photo")
    Mono<ResponseEntity<byte[]>> photo(@PathVariable String publicId, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> photos.get(actor, WebSupport.parseUuid(publicId)))
                .map(UserController::image);
    }
}
