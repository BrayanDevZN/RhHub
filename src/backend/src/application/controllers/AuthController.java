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

/** Rotas /api/auth: login, 2FA, renovar sessão (refresh), logout, trocar e recuperar senha. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Corpo do login: e-mail e senha.
     */
    public record LoginRequest(String email, String password) {
    }

    /**
     * Corpo com o código de 6 dígitos recebido por e-mail.
     */
    public record CodeRequest(String code) {
    }

    /**
     * Corpo com um e-mail (usado para pedir o código de recuperação de senha).
     */
    public record EmailRequest(String email) {
    }

    /**
     * Corpo da troca de senha: a senha atual e a nova (campo newPassword).
     */
    public record PasswordChange(String password, @JsonProperty("newPassword") String newPassword) {
    }

    /**
     * Corpo da recuperação de senha: e-mail, código recebido e a senha nova (campo newPassword).
     */
    public record PasswordReset(String email, String code, @JsonProperty("newPassword") String newPassword) {
    }

    private final AuthService auth;
    private final WebSupport web;
    private final JwtService jwt;
    private final Clock clock;

    /**
     * Recebe os serviços de que as rotas precisam; o Spring os injeta.
     */
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

    /**
     * POST /api/auth/2fa/send. Envia por e-mail o código da segunda etapa. Usa o cookie temporário de 2FA que o login criou.
     */
    @PostMapping("/2fa/send")
    Mono<Map<String, Object>> sendTwoFactor(ServerWebExchange exchange) {
        return auth.sendTwoFactorCode(web.cookieValue(exchange, TokenType.TWO_FACTOR.cookie()))
                .map(AuthController::codeBody);
    }

    /**
     * POST /api/auth/2fa/verify. Confere o código de 2FA. Se estiver certo, apaga o cookie temporário, abre a sessão (cookies de acesso e refresh) e devolve o usuário.
     */
    @PostMapping("/2fa/verify")
    Mono<Views.UserView> verifyTwoFactor(@RequestBody CodeRequest body, ServerWebExchange exchange) {
        return auth.verifyTwoFactor(web.cookieValue(exchange, TokenType.TWO_FACTOR.cookie()), body.code())
                .map(session -> {
                    exchange.getResponse().addCookie(web.expired(TokenType.TWO_FACTOR));
                    openSession(exchange, session);
                    return Views.user(session.user(), null);
                });
    }

    /**
     * POST /api/auth/refresh. Renova o cookie de acesso usando o cookie de refresh. O front chama sozinho quando o acesso vence.
     */
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

    /**
     * Descobre quem é o dono da sessão para o logout: tenta o token de acesso e depois o de refresh, ignorando token inválido ou vencido.
     *
     * @return o identificador do usuário, ou nulo se nenhum token vale.
     */
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

    /**
     * PATCH /api/auth/password. Troca a senha de quem está logado. Depois de trocar, apaga os cookies: todas as sessões encerram e a pessoa entra de novo com a senha nova.
     */
    @PatchMapping("/password")
    Mono<Map<String, Object>> changePassword(@RequestBody PasswordChange body, ServerWebExchange exchange) {
        return web.actor(exchange)
                .flatMap(user -> auth.changePassword(user.publicId(), body.password(), body.newPassword()))
                .then(Mono.fromSupplier(() -> {
                    web.clearSession(exchange); // a troca encerra as sessões: entra de novo com a senha nova
                    return Map.<String, Object>of("status", true);
                }));
    }

    /**
     * POST /api/auth/password/reset-code. Pede o código de recuperação de senha. Responde igual exista ou não a conta.
     */
    @PostMapping("/password/reset-code")
    Mono<Map<String, Object>> resetCode(@RequestBody EmailRequest body) {
        return auth.requestPasswordReset(body.email()).map(AuthController::codeBody);
    }

    /**
     * POST /api/auth/password/reset. Define a senha nova usando o código recebido por e-mail.
     */
    @PostMapping("/password/reset")
    Mono<Map<String, Object>> reset(@RequestBody PasswordReset body) {
        return auth.resetPassword(body.email(), body.code(), body.newPassword())
                .then(Mono.fromSupplier(() -> Map.<String, Object>of("status", true)));
    }

    /**
     * GET /api/auth/providers. Diz se o login com Google está ligado. O front usa para decidir o que fazer ao clicar no botão do Google.
     */
    @GetMapping("/providers")
    Map<String, Object> providers() {
        return Map.of("google", auth.googleEnabled());
    }

    /**
     * Coloca na resposta os cookies de sessão (acesso e refresh) no modo normal. Fica estático para o UserController usar também no cadastro.
     */
    static void openSession0(WebSupport web, ServerWebExchange exchange, AuthService.Session session) {
        exchange.getResponse().addCookie(web.cookie(TokenType.ACCESS, session.accessToken()));
        exchange.getResponse().addCookie(web.cookie(TokenType.REFRESH, session.refreshToken()));
    }

    /** Sessão aberta na volta do Google: os cookies precisam ser Lax (ver WebSupport.landingCookie). */
    static void openGoogleSession(WebSupport web, ServerWebExchange exchange, AuthService.Session session) {
        exchange.getResponse().addCookie(web.landingCookie(TokenType.ACCESS, session.accessToken()));
        exchange.getResponse().addCookie(web.landingCookie(TokenType.REFRESH, session.refreshToken()));
    }

    /**
     * Atalho de {@link #openSession0} usando o WebSupport deste controller.
     */
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
