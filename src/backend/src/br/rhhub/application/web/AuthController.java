package br.rhhub.application.web;

import br.rhhub.application.web.dto.Views;
import br.rhhub.domain.auth.JwtService;
import br.rhhub.domain.auth.TokenType;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.service.AuthService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(String email, String password) {
    }

    public record CodeRequest(String code) {
    }

    public record EmailRequest(String email) {
    }

    public record PasswordChange(String password, @JsonProperty("newPassword") String newPassword) {
    }

    public record PasswordReset(String email, String code, @JsonProperty("newPassword") String newPassword) {
    }

    private final AuthService auth;
    private final WebSupport web;
    private final JwtService jwt;
    private final Clock clock;

    public AuthController(AuthService auth, WebSupport web, JwtService jwt, Clock clock) {
        this.auth = auth;
        this.web = web;
        this.jwt = jwt;
        this.clock = clock;
    }

    /** Sem 2FA: abre a sessão ({auth: false}). Com 2FA: só o cookie temporário ({auth: "required"}). */
    @PostMapping("/login")
    Mono<Map<String, Object>> login(@RequestBody LoginRequest body, ServerWebExchange exchange) {
        return auth.login(body.email(), body.password()).map(result -> {
            if (result.twoFactorRequired()) {
                exchange.getResponse().addCookie(web.cookie(TokenType.TWO_FACTOR, result.twoFactorToken()));
                return Map.<String, Object>of("auth", "required");
            }
            openSession(exchange, result.session());
            return Map.<String, Object>of("auth", false);
        });
    }

    @PostMapping("/2fa/send")
    Mono<Map<String, Object>> sendTwoFactor(ServerWebExchange exchange) {
        return auth.sendTwoFactorCode(web.cookieValue(exchange, TokenType.TWO_FACTOR.cookie()))
                .map(AuthController::codeBody);
    }

    @PostMapping("/2fa/verify")
    Mono<Views.UserView> verifyTwoFactor(@RequestBody CodeRequest body, ServerWebExchange exchange) {
        return auth.verifyTwoFactor(web.cookieValue(exchange, TokenType.TWO_FACTOR.cookie()), body.code())
                .map(session -> {
                    exchange.getResponse().addCookie(web.expired(TokenType.TWO_FACTOR));
                    openSession(exchange, session);
                    return Views.user(session.user(), null);
                });
    }

    @PostMapping("/refresh")
    Mono<Map<String, Object>> refresh(ServerWebExchange exchange) {
        return auth.refresh(web.cookieValue(exchange, TokenType.REFRESH.cookie())).map(access -> {
            exchange.getResponse().addCookie(web.cookie(TokenType.ACCESS, access));
            return Map.<String, Object>of("status", true);
        });
    }

    /**
     * Sempre apaga os cookies, mesmo com o token de acesso vencido (aí identifica a pessoa pelo refresh token).
     * Os cookies precisam ser apagados ANTES de a resposta ser enviada: depois disso o cabeçalho já saiu.
     */
    @PostMapping("/logout")
    Mono<Void> logout(ServerWebExchange exchange) {
        UUID who = sessionOwner(exchange);
        web.clearSession(exchange);
        return who == null ? Mono.empty() : auth.logout(who);
    }

    private UUID sessionOwner(ServerWebExchange exchange) {
        for (TokenType type : new TokenType[] {TokenType.ACCESS, TokenType.REFRESH}) {
            try {
                return jwt.verify(web.cookieValue(exchange, type.cookie()), type, clock.instant()).subject();
            } catch (DomainException ignored) {
                // tenta o próximo
            }
        }
        return null;
    }

    @PatchMapping("/password")
    Mono<Map<String, Object>> changePassword(@RequestBody PasswordChange body, ServerWebExchange exchange) {
        return web.actor(exchange)
                .flatMap(user -> auth.changePassword(user.publicId(), body.password(), body.newPassword()))
                .then(Mono.fromSupplier(() -> {
                    web.clearSession(exchange); // a troca encerra as sessões: entra de novo com a senha nova
                    return Map.<String, Object>of("status", true);
                }));
    }

    @PostMapping("/password/reset-code")
    Mono<Map<String, Object>> resetCode(@RequestBody EmailRequest body) {
        return auth.requestPasswordReset(body.email()).map(AuthController::codeBody);
    }

    @PostMapping("/password/reset")
    Mono<Map<String, Object>> reset(@RequestBody PasswordReset body) {
        return auth.resetPassword(body.email(), body.code(), body.newPassword())
                .then(Mono.fromSupplier(() -> Map.<String, Object>of("status", true)));
    }

    @GetMapping("/providers")
    Map<String, Object> providers() {
        return Map.of("google", auth.googleEnabled());
    }

    static void openSession0(WebSupport web, ServerWebExchange exchange, AuthService.Session session) {
        exchange.getResponse().addCookie(web.cookie(TokenType.ACCESS, session.accessToken()));
        exchange.getResponse().addCookie(web.cookie(TokenType.REFRESH, session.refreshToken()));
    }

    /** Sessão aberta na volta do Google: os cookies precisam ser Lax (ver WebSupport.landingCookie). */
    static void openGoogleSession(WebSupport web, ServerWebExchange exchange, AuthService.Session session) {
        exchange.getResponse().addCookie(web.landingCookie(TokenType.ACCESS, session.accessToken()));
        exchange.getResponse().addCookie(web.landingCookie(TokenType.REFRESH, session.refreshToken()));
    }

    private void openSession(ServerWebExchange exchange, AuthService.Session session) {
        openSession0(web, exchange, session);
    }

    /** Em ENVIRONMENT=test o código vem na resposta; em produção só chega por e-mail. */
    static Map<String, Object> codeBody(AuthService.CodeSent sent) {
        var body = new LinkedHashMap<String, Object>();
        body.put("status", true);
        if (sent.code() != null) {
            body.put("code", sent.code());
        }
        return body;
    }
}
