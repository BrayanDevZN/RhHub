package br.rhhub.application.web;

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

    private final AuthService auth;
    private final WebSupport web;
    private final WebProperties props;

    public GoogleController(AuthService auth, WebSupport web, WebProperties props) {
        this.auth = auth;
        this.web = web;
        this.props = props;
    }

    @GetMapping("/oauth2/authorization/google")
    Mono<Void> start(ServerWebExchange exchange) {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String url = auth.googleAuthorizationUrl(state); // 404 se estiver desligado
        exchange.getResponse().addCookie(web.stateCookie(state, false));
        return redirect(exchange, url);
    }

    @GetMapping("/login/oauth2/code/google")
    Mono<Void> callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
            ServerWebExchange exchange) {
        if (!auth.googleEnabled()) {
            return Mono.error(new br.rhhub.domain.exception.DomainException.NotFound("Login com Google desativado."));
        }
        String expected = web.cookieValue(exchange, "X-oauth_state");
        exchange.getResponse().addCookie(web.stateCookie("", true));
        if (expected == null || state == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Login com Google recusado: o state não confere (cookie {}, parâmetro {})",
                    expected == null ? "ausente" : "presente", state == null ? "ausente" : "presente");
            return redirect(exchange, props.homeUrl() + "login?error=google");
        }
        if (code == null || code.isBlank()) {
            log.warn("Login com Google recusado: o Google voltou sem code (o usuário cancelou ou o Google recusou)");
            return redirect(exchange, props.homeUrl() + "login?error=google");
        }
        return auth.loginWithGoogle(code)
                .flatMap(session -> {
                    AuthController.openSession0(web, exchange, session);
                    return redirect(exchange, props.homeUrl());
                })
                .onErrorResume(error -> {
                    log.warn("Login com Google recusado: {} ({})", error.getMessage(),
                            error.getCause() == null ? error.getClass().getSimpleName() : error.getCause().getClass().getSimpleName());
                    return redirect(exchange, props.homeUrl() + "login?error=google");
                });
    }

    private static Mono<Void> redirect(ServerWebExchange exchange, String url) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(url));
        exchange.getResponse().getHeaders().remove(HttpHeaders.CONTENT_TYPE);
        return exchange.getResponse().setComplete();
    }
}
