package br.rhhub.application.web;

import br.rhhub.application.web.dto.Views;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.ProfilePhoto;
import br.rhhub.service.AuthService;
import br.rhhub.service.ProfilePhotoService;
import br.rhhub.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class UserController {

    public record SignupCodeRequest(String name, String email) {
    }

    public record SignupRequest(String name, String email, String cpf, Integer age, String gender, String password,
            String code) {
    }

    public record PatchRequest(String set, JsonNode value) {
    }

    public record DeleteRequest(String password) {
    }

    private final AuthService auth;
    private final UserService users;
    private final ProfilePhotoService photos;
    private final WebSupport web;

    public UserController(AuthService auth, UserService users, ProfilePhotoService photos, WebSupport web) {
        this.auth = auth;
        this.users = users;
        this.photos = photos;
        this.web = web;
    }

    @PostMapping("/sender/signup-code")
    Mono<Map<String, Object>> signupCode(@RequestBody SignupCodeRequest body) {
        return auth.requestSignupCode(body.name(), body.email()).map(AuthController::codeBody);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    Mono<Views.UserView> signup(@RequestBody SignupRequest body, ServerWebExchange exchange) {
        return auth.register(body.name(), body.email(), body.cpf(), body.age(), body.gender(), body.password(), body.code())
                .map(session -> {
                    AuthController.openSession0(web, exchange, session);
                    return Views.user(session.user(), false);
                });
    }

    @GetMapping("/users/me")
    Mono<Views.UserView> me(ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(user -> photos.has(user).map(has -> Views.user(user, has)));
    }

    /** {set: "age" | "gender" | "two_factor" | "cpf", value}. */
    @PatchMapping("/users/me")
    Mono<Views.UserView> update(@RequestBody PatchRequest body, ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> users.update(actor.publicId(), change(body)))
                .flatMap(user -> photos.has(user).map(has -> Views.user(user, has)));
    }

    @DeleteMapping("/users/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@RequestBody(required = false) DeleteRequest body, ServerWebExchange exchange) {
        return web.actor(exchange)
                .flatMap(actor -> users.deleteOwn(actor.publicId(), body == null ? null : body.password()))
                .then(Mono.fromRunnable(() -> web.clearSession(exchange)));
    }

    // ---- foto de perfil ----

    @PutMapping(value = "/users/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Mono<Map<String, Object>> uploadPhoto(@RequestPart("file") FilePart file, ServerWebExchange exchange) {
        return web.actor(exchange)
                .flatMap(actor -> web.read(file, ProfilePhoto.MAX_BYTES, "A foto pode ter no máximo 2 MB.")
                        .flatMap(bytes -> photos.upload(actor, bytes)))
                .then(Mono.fromSupplier(() -> Map.<String, Object>of("status", true)));
    }

    @GetMapping("/users/me/photo")
    Mono<ResponseEntity<byte[]>> photo(ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> photos.get(actor, null)).map(UserController::image);
    }

    @DeleteMapping("/users/me/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> deletePhoto(ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(photos::remove);
    }

    static ResponseEntity<byte[]> image(ProfilePhoto photo) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                .header("Cache-Control", "private, no-cache").body(photo.content());
    }

    private static UserService.Update change(PatchRequest body) {
        if (body == null || body.set() == null || body.value() == null || body.value().isNull()) {
            throw new DomainException.Invalid("Informe o campo (set) e o valor (value).");
        }
        JsonNode value = body.value();
        return switch (body.set()) {
            case "age" -> {
                if (!value.isIntegralNumber()) {
                    throw new DomainException.Invalid("A idade precisa ser um número.");
                }
                yield new UserService.Update(value.asInt(), null, null, null);
            }
            case "gender" -> new UserService.Update(null, text(value), null, null);
            case "cpf" -> new UserService.Update(null, null, null, text(value));
            case "two_factor" -> {
                if (!value.isBoolean()) {
                    throw new DomainException.Invalid("two_factor precisa ser verdadeiro ou falso.");
                }
                yield new UserService.Update(null, null, value.asBoolean(), null);
            }
            default -> throw new DomainException.Invalid("Campo não editável: " + body.set());
        };
    }

    private static String text(JsonNode value) {
        if (!value.isTextual()) {
            throw new DomainException.Invalid("O valor precisa ser um texto.");
        }
        return value.asText();
    }
}
