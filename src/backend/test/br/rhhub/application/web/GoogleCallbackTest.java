package br.rhhub.application.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.rhhub.application.port.out.GoogleIdentityProvider;
import br.rhhub.domain.auth.AccessPolicy;
import br.rhhub.domain.auth.JwtService;
import br.rhhub.domain.auth.PasswordHasher;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.infra.TestSupport;
import br.rhhub.infra.config.AppProperties;
import br.rhhub.infra.config.WebProperties;
import br.rhhub.infra.email.HtmlEmailComposer;
import br.rhhub.service.AuthService;
import br.rhhub.service.Fakes;
import br.rhhub.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import reactor.core.publisher.Mono;

/** O vai e volta do login com Google: o que o navegador recebe em cada resultado (sucesso e cada motivo de recusa). */
class GoogleCallbackTest {

    static final Fakes.Users users = new Fakes.Users();
    static volatile GoogleIdentityProvider.Profile profile;
    static volatile RuntimeException failure;
    static volatile String frontend = "https://front.exemplo.com";

    @Configuration
    @EnableWebFlux
    @Import({ApiExceptionHandler.class, WebSupport.class, GoogleController.class})
    static class Context {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean AppProperties appProperties() { return TestSupport.props(); }
        @Bean WebProperties webProperties() { return new WebProperties(frontend, true, "Strict"); }
        @Bean JwtService jwt(ObjectMapper m) { return new JwtService("segredo-de-teste-com-mais-de-32-caracteres", m); }
        @Bean PasswordHasher hasher() {
            return new PasswordHasher() {
                public String hash(String p) { return "h:" + p; }
                public boolean matches(String p, String h) { return h.equals("h:" + p); }
            };
        }
        @Bean Fakes.Refresh refresh() { return new Fakes.Refresh(); }
        @Bean UserService userService(PasswordHasher hasher, Fakes.Refresh refresh) {
            return new UserService(users, hasher, refresh, new AccessPolicy("root@gmail.com"));
        }
        @Bean AuthService authService(JwtService jwt, PasswordHasher hasher, Fakes.Refresh refresh, Clock clock) {
            var google = new GoogleIdentityProvider() {
                public boolean enabled() { return true; }
                public String authorizationUrl(String state) { return "https://accounts.google.com/auth?state=" + state; }
                public Mono<Profile> fetchProfile(String code) {
                    return failure != null ? Mono.error(failure) : Mono.just(profile);
                }
            };
            return new AuthService(users, hasher, jwt, refresh, new Fakes.Codes(), new HtmlEmailComposer(),
                    new Fakes.Mailbox(), google, clock, true);
        }
    }

    private AnnotationConfigApplicationContext ctx;
    private WebTestClient client;

    @BeforeEach
    void start() {
        users.byId.clear();
        profile = new GoogleIdentityProvider.Profile("g-1", "ana@gmail.com", true, "Ana");
        failure = null;
        frontend = "https://front.exemplo.com";
        ctx = new AnnotationConfigApplicationContext(Context.class);
        client = WebTestClient.bindToApplicationContext(ctx).configureClient().baseUrl("http://localhost").build();
    }

    private String begin() {
        return begin("signup");
    }

    private String begin(String mode) {
        var res = client.get().uri("/oauth2/authorization/google?mode=" + mode).exchange().expectStatus().isFound().returnResult(String.class);
        var cookie = res.getResponseCookies().getFirst("X-oauth_state");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax"); // precisa voltar numa navegação vinda do Google
        assertThat(cookie.getPath()).isEqualTo("/login/oauth2");
        assertThat(res.getResponseHeaders().getLocation().toString()).contains("state=" + cookie.getValue());
        return cookie.getValue();
    }

    private String callbackLocation(String stateCookie, String query) {
        var spec = client.get().uri("/login/oauth2/code/google" + query).cookies(c -> {
            if (stateCookie != null) c.add("X-oauth_state", stateCookie);
        });
        return spec.exchange().expectStatus().isFound().returnResult(String.class).getResponseHeaders().getLocation().toString();
    }

    @Test
    void successOpensTheSessionAndGoesToTheFrontWithAbsoluteUrl() {
        String state = begin();
        var res = client.get().uri("/login/oauth2/code/google?code=abc&state=" + state).cookie("X-oauth_state", state)
                .exchange().expectStatus().isFound().returnResult(String.class);
        assertThat(res.getResponseHeaders().getLocation().toString()).isEqualTo("https://front.exemplo.com/");
        assertThat(res.getResponseCookies().getFirst("X-user_token").isHttpOnly()).isTrue();
        assertThat(res.getResponseCookies().getFirst("X-user_refresh_token")).isNotNull();
        // Strict seria descartado pelo navegador nesta volta vinda do Google, e o usuário caía de novo no /login.
        assertThat(res.getResponseCookies().getFirst("X-user_token").getSameSite()).isEqualTo("Lax");
        assertThat(res.getResponseCookies().getFirst("X-user_refresh_token").getSameSite()).isEqualTo("Lax");
        assertThat(res.getResponseCookies().getFirst("X-oauth_state").getMaxAge().isZero()).isTrue(); // state usado uma vez
        assertThat(users.byId.values()).extracting("email").containsExactly("ana@gmail.com");
    }

    @Test
    void loginDoesNotCreateAnAccountAndTellsThereIsNone() {
        String state = begin("login");
        assertThat(state).startsWith("login.");
        assertThat(callbackLocation(state, "?code=abc&state=" + state))
                .isEqualTo("https://front.exemplo.com/login?error=google&reason=no_account");
        assertThat(users.byId).isEmpty();
    }

    @Test
    void loginWithoutAModeNeverCreatesAnAccountEither() {
        var res = client.get().uri("/oauth2/authorization/google").exchange().expectStatus().isFound().returnResult(String.class);
        String state = res.getResponseCookies().getFirst("X-oauth_state").getValue();
        assertThat(state).startsWith("login.");
        assertThat(callbackLocation(state, "?code=abc&state=" + state)).contains("reason=no_account");
        assertThat(users.byId).isEmpty();
    }

    @Test
    void loginEntersAnExistingAccountAndSignupCreatesOnlyOnce() {
        String signup = begin("signup");
        assertThat(callbackLocation(signup, "?code=abc&state=" + signup)).isEqualTo("https://front.exemplo.com/");
        assertThat(users.byId).hasSize(1);
        String login = begin("login");
        assertThat(callbackLocation(login, "?code=abc&state=" + login)).isEqualTo("https://front.exemplo.com/");
        String again = begin("signup");
        assertThat(callbackLocation(again, "?code=abc&state=" + again)).isEqualTo("https://front.exemplo.com/");
        assertThat(users.byId).hasSize(1);
    }

    @Test
    void aFrontendUrlWithoutSchemeStillRedirectsToAnAbsoluteAddress() {
        frontend = "rhhub-production.up.railway.app"; // o erro real de configuração que apareceu em produção
        ctx.close();
        ctx = new AnnotationConfigApplicationContext(Context.class);
        client = WebTestClient.bindToApplicationContext(ctx).configureClient().baseUrl("http://localhost").build();
        String state = begin();
        assertThat(callbackLocation(state, "?code=abc&state=" + state)).isEqualTo("https://rhhub-production.up.railway.app/");
        assertThat(callbackLocation(null, "?code=abc&state=x"))
                .isEqualTo("https://rhhub-production.up.railway.app/login?error=google&reason=state");
    }

    @Test
    void eachFailureComesBackWithItsReasonCode() {
        String state = begin();
        String base = "https://front.exemplo.com/login?error=google&reason=";

        assertThat(callbackLocation(null, "?code=abc&state=" + state)).isEqualTo(base + "state"); // cookie perdido
        assertThat(callbackLocation("outro", "?code=abc&state=" + state)).isEqualTo(base + "state");
        assertThat(callbackLocation(state, "?state=" + state)).isEqualTo(base + "no_code");
        assertThat(callbackLocation(state, "?error=access_denied&state=" + state)).isEqualTo(base + "google_access_denied");

        failure = new GoogleIdentityProvider.LoginFailure("invalid_client", null);
        assertThat(callbackLocation(state, "?code=abc&state=" + state)).isEqualTo(base + "invalid_client");
        failure = new GoogleIdentityProvider.LoginFailure("invalid_grant", null);
        assertThat(callbackLocation(state, "?code=abc&state=" + state)).isEqualTo(base + "invalid_grant");

        failure = null;
        profile = new GoogleIdentityProvider.Profile("g-2", "x@outlook.com", true, "X");
        assertThat(callbackLocation(state, "?code=abc&state=" + state)).isEqualTo(base + "not_gmail");
        profile = new GoogleIdentityProvider.Profile("g-3", "y@gmail.com", false, "Y");
        assertThat(callbackLocation(state, "?code=abc&state=" + state)).isEqualTo(base + "email_not_verified");

        // falha do nosso lado (banco, Redis...): vem o nome da exceção, nunca a mensagem
        failure = new IllegalStateException("senha=segredo no texto");
        String ours = callbackLocation(state, "?code=abc&state=" + state);
        assertThat(ours).isEqualTo(base + "server_error%3AIllegalStateException").doesNotContain("segredo");
    }
}
