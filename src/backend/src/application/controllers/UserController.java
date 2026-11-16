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

/** Rotas /api/users e /api/sender: cadastro com código por e-mail, perfil (/me), foto e exclusão da própria conta. */
@RestController
@RequestMapping("/api")
public class UserController {

    /**
     * Corpo do pedido do código de cadastro: nome e e-mail.
     */
    public record SignupCodeRequest(String name, String email) {
    }

    /**
     * Corpo do cadastro: nome, e-mail, CPF, idade, gênero, senha e o código recebido por e-mail.
     */
    public record SignupRequest(String name, String email, String cpf, Integer age, String gender, String password,
            String code) {
    }

    /**
     * Corpo da edição de perfil: set (qual campo: age, gender, cpf ou two_factor) e value (o novo valor).
     */
    public record PatchRequest(String set, JsonNode value) {
    }

    /**
     * Corpo da exclusão de conta: a senha, para confirmar (contas do Google não precisam).
     */
    public record DeleteRequest(String password) {
    }

    /**
     * Corpo de completar o cadastro: CPF, idade e gênero.
     */
    public record ProfileRequest(String cpf, Integer age, String gender) {
    }

    private final AuthService auth;
    private final UserService users;
    private final ProfilePhotoService photos;
    private final WebSupport web;

    /**
     * Recebe os serviços de que as rotas precisam; o Spring os injeta.
     */
    public UserController(AuthService auth, UserService users, ProfilePhotoService photos, WebSupport web) {
        this.auth = auth;
        this.users = users;
        this.photos = photos;
        this.web = web;
    }

    /**
     * POST /api/sender/signup-code. Primeiro passo do cadastro: envia o código de confirmação para o e-mail informado.
     */
    @PostMapping("/sender/signup-code")
    Mono<Map<String, Object>> signupCode(@RequestBody SignupCodeRequest body) {
        return auth.requestSignupCode(body.name(), body.email()).map(AuthController::codeBody);
    }

    /**
     * POST /api/users. Segundo passo do cadastro: cria a conta com o código recebido, abre a sessão (cookies) e devolve o usuário (201).
     */
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    Mono<Views.UserView> signup(@RequestBody SignupRequest body, ServerWebExchange exchange) {
        return auth.register(body.name(), body.email(), body.cpf(), body.age(), body.gender(), body.password(), body.code())
                .map(session -> {
                    AuthController.openSession0(web, exchange, session);
                    return Views.user(session.user(), false);
                });
    }

    /**
     * GET /api/users/me. Devolve quem está logado, incluindo se tem foto. É a primeira chamada do front para saber se há sessão.
     */
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

    /** Completa o cadastro (CPF, idade e gênero juntos): o passo que a conta criada com o Google precisa fazer. */
    @PutMapping("/users/me/profile")
    Mono<Views.UserView> completeProfile(@RequestBody ProfileRequest body, ServerWebExchange exchange) {
        return web.actor(exchange)
                .flatMap(actor -> users.completeProfile(actor.publicId(), body.cpf(), body.age(), body.gender()))
                .flatMap(user -> photos.has(user).map(has -> Views.user(user, has)));
    }

    /**
     * DELETE /api/users/me. Exclui a própria conta e apaga os cookies de sessão (204).
     */
    @DeleteMapping("/users/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@RequestBody(required = false) DeleteRequest body, ServerWebExchange exchange) {
        return web.actor(exchange)
                .flatMap(actor -> users.deleteOwn(actor.publicId(), body == null ? null : body.password()))
                .then(Mono.fromRunnable(() -> web.clearSession(exchange)));
    }

    // ---- foto de perfil ----

    /**
     * PUT /api/users/me/photo. Envia ou troca a foto de perfil (JPEG, PNG ou WebP, até 2 MB).
     */
    @PutMapping(value = "/users/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Mono<Map<String, Object>> uploadPhoto(@RequestPart("file") FilePart file, ServerWebExchange exchange) {
        return web.actor(exchange)
                .flatMap(actor -> web.read(file, ProfilePhoto.MAX_BYTES, "A foto pode ter no máximo 2 MB.")
                        .flatMap(bytes -> photos.upload(actor, bytes)))
                .then(Mono.fromSupplier(() -> Map.<String, Object>of("status", true)));
    }

    /**
     * GET /api/users/me/photo. Devolve a própria foto de perfil.
     */
    @GetMapping("/users/me/photo")
    Mono<ResponseEntity<byte[]>> photo(ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(actor -> photos.get(actor, null)).map(UserController::image);
    }

    /**
     * DELETE /api/users/me/photo. Remove a própria foto (204).
     */
    @DeleteMapping("/users/me/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> deletePhoto(ServerWebExchange exchange) {
        return web.actor(exchange).flatMap(photos::remove);
    }

    /**
     * Monta a resposta HTTP de uma imagem, com o content-type certo e cache privado que sempre revalida (a foto muda quando a pessoa troca). Também é usada pelo AdminController.
     */
    static ResponseEntity<byte[]> image(ProfilePhoto photo) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                .header("Cache-Control", "private, no-cache").body(photo.content());
    }

    /**
     * Converte o corpo {set, value} em um objeto de atualização, conferindo o tipo do valor: age precisa ser número inteiro, two_factor precisa ser verdadeiro ou falso, gender e cpf precisam ser texto.
     *
     * @throws DomainException.Invalid se faltar campo ou valor, se o tipo estiver errado ou se o campo não puder ser editado.
     */
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

    /**
     * Lê o valor como texto, ou falha se ele não for um texto.
     */
    private static String text(JsonNode value) {
        if (!value.isTextual()) {
            throw new DomainException.Invalid("O valor precisa ser um texto.");
        }
        return value.asText();
    }
}
