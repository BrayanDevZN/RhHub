package br.rhhub.application.web;

import br.rhhub.domain.auth.TokenType;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.User;
import br.rhhub.infra.config.AppProperties;
import br.rhhub.infra.config.WebProperties;
import br.rhhub.service.UserService;
import java.time.Duration;
import java.util.UUID;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Cookies de sessão, usuário logado e leitura de upload: o que os controllers têm em comum. */
@Component
public class WebSupport {

    /** Atributo do exchange com o public_id de quem tem um token de acesso válido. */
    public static final String USER_ATTRIBUTE = "rhhub.publicId";

    private final WebProperties web;
    private final boolean secure;
    private final UserService users;

    public WebSupport(WebProperties web, AppProperties app, UserService users) {
        this.web = web;
        this.secure = web.cookieSecure() != null ? web.cookieSecure() : !app.isTest();
        this.users = users;
    }

    // ---- cookies ----

    /** Os cookies de sessão levam HttpOnly (o JS não lê), SameSite e, em produção, Secure. */
    public ResponseCookie cookie(TokenType type, String token) {
        return base(type, token).maxAge(type.lifetime()).build();
    }

    /**
     * Cookie de sessão aberto na volta do Google. O navegador descarta Set-Cookie SameSite=Strict numa resposta que
     * vem de uma cadeia de navegação iniciada em outro site (accounts.google.com), e o login ficava sem sessão e
     * voltava para /login. Aqui o SameSite é Lax (o mesmo valor do state); o cookie volta a Strict ao renovar.
     */
    public ResponseCookie landingCookie(TokenType type, String token) {
        return base(type, token).sameSite("Lax").maxAge(type.lifetime()).build();
    }

    public ResponseCookie expired(TokenType type) {
        return base(type, "").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(TokenType type, String value) {
        return ResponseCookie.from(type.cookie(), value).httpOnly(true).secure(secure)
                .sameSite(web.cookieSameSite()).path(type == TokenType.ACCESS ? "/" : "/api/auth");
    }

    /** O state do OAuth precisa voltar numa navegação vinda do Google, então é Lax, e só vale 10 minutos. */
    public ResponseCookie stateCookie(String value, boolean clear) {
        return ResponseCookie.from("X-oauth_state", value).httpOnly(true).secure(secure).sameSite("Lax")
                .path("/login/oauth2").maxAge(clear ? Duration.ZERO : Duration.ofMinutes(10)).build();
    }

    public String cookieValue(ServerWebExchange exchange, String name) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(name);
        return cookie == null ? null : cookie.getValue();
    }

    public void clearSession(ServerWebExchange exchange) {
        for (TokenType type : TokenType.values()) {
            exchange.getResponse().addCookie(expired(type));
        }
    }

    // ---- usuário logado ----

    public Mono<User> actor(ServerWebExchange exchange) {
        UUID id = exchange.getAttribute(USER_ATTRIBUTE);
        return id == null ? Mono.error(new DomainException.Unauthorized("Faça login para continuar."))
                : users.identity(id);
    }

    // ---- upload ----

    /** Lê o arquivo na memória, recusando o que passar de maxBytes sem carregar o resto. */
    public Mono<byte[]> read(FilePart part, int maxBytes, String tooBigMessage) {
        return DataBufferUtils.join(part.content(), maxBytes + 1)
                .map(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return bytes;
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .onErrorMap(DataBufferLimitException.class, e -> new DomainException.Invalid(tooBigMessage))
                .defaultIfEmpty(new byte[0]);
    }

    public static Long parseId(String raw, String what) {
        try {
            return Long.valueOf(raw.strip());
        } catch (RuntimeException e) {
            throw new DomainException.Invalid(what + " inválido.");
        }
    }

    public static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (RuntimeException e) {
            throw new DomainException.Invalid("Identificador inválido.");
        }
    }

}
