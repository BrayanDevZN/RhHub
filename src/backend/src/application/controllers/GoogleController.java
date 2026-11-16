package br.rhhub.application.web;

import br.rhhub.application.port.out.GoogleIdentityProvider;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.infra.config.WebProperties;
import br.rhhub.service.AuthService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Login com Google. Com GOOGLE_OAUTH_ENABLED=false, as duas rotas respondem 404. */
@RestController
public class GoogleController {

    private static final Logger log = LoggerFactory.getLogger(GoogleController.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SIGNUP = "signup";
    private static final String LOGIN = "login";

    private final AuthService auth;
    private final WebSupport web;
    private final WebProperties props;

    public GoogleController(AuthService auth, WebSupport web, WebProperties props) {
        this.auth = auth;
        this.web = web;
        this.props = props;
    }

    /**
     * ?mode=signup cria a conta se ela não existir (o botão "Cadastrar com Google"); sem isso, ou com mode=login, só
     * entra quem já tem conta. O modo viaja no state (signup.xxxx / login.xxxx), que o cookie confere na volta.
     */
    @GetMapping("/oauth2/authorization/google")
    Mono<Void> start(@RequestParam(required = false) String mode, ServerWebExchange exchange) {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String state = ("signup".equals(mode) ? SIGNUP : LOGIN) + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String url = auth.googleAuthorizationUrl(state); // 404 se estiver desligado
        exchange.getResponse().addCookie(web.stateCookie(state, false));
        return redirect(exchange, url);
    }

    @GetMapping("/login/oauth2/code/google")
    Mono<Void> callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
            @RequestParam(required = false) String error, ServerWebExchange exchange) {
        if (!auth.googleEnabled()) {
            return Mono.error(new br.rhhub.domain.exception.DomainException.NotFound("Login com Google desativado."));
        }
        String expected = web.cookieValue(exchange, "X-oauth_state");
        exchange.getResponse().addCookie(web.stateCookie("", true));
        if (expected == null || state == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Login com Google recusado: o state não confere (cookie {}, parâmetro {})",
                    expected == null ? "ausente" : "presente", state == null ? "ausente" : "presente");
            return failed(exchange, "state");
        }
        if (error != null && !error.isBlank()) { // o usuário cancelou ou o Google recusou antes de devolver o code
            log.warn("Login com Google recusado pelo Google: {}", error);
            return failed(exchange, "google_" + error.replaceAll("[^a-z_]", "").toLowerCase());
        }
        if (code == null || code.isBlank()) {
            log.warn("Login com Google recusado: o Google voltou sem code");
            return failed(exchange, "no_code");
        }
        return auth.loginWithGoogle(code, state.startsWith(SIGNUP + "."))
                .flatMap(session -> {
                    AuthController.openGoogleSession(web, exchange, session);
                    return redirect(exchange, props.homeUrl());
                })
                .onErrorResume(failure -> {
                    String reason = reasonOf(failure);
                    log.warn("Login com Google recusado ({}): {}", reason, failure.getMessage());
                    return failed(exchange, reason);
                });
    }

    /**
     * Código curto do motivo, para a tela do login mostrar (sem dado sensível): o erro do Google, não_gmail, e-mail não
     * verificado, ou server_error:NomeDaExceção quando foi o nosso lado (banco, Redis...) que falhou.
     */
    static String reasonOf(Throwable failure) {
        if (failure instanceof GoogleIdentityProvider.LoginFailure google) {
            return google.reason();
        }
        if (failure instanceof DomainException.NotFound) {
            return "no_account";
        }
        if (failure instanceof DomainException.Forbidden) {
            return "not_gmail";
        }
        if (failure instanceof DomainException.Unauthorized) {
            return "email_not_verified";
        }
        return "server_error:" + failure.getClass().getSimpleName();
    }

    private Mono<Void> failed(ServerWebExchange exchange, String reason) {
        return redirect(exchange, props.homeUrl() + "login?error=google&reason="
                + java.net.URLEncoder.encode(reason, StandardCharsets.UTF_8));
    }

    private static Mono<Void> redirect(ServerWebExchange exchange, String url) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(url));
        exchange.getResponse().getHeaders().remove(HttpHeaders.CONTENT_TYPE);
        return exchange.getResponse().setComplete();
    }
}
