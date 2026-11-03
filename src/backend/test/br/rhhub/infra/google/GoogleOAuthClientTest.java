package br.rhhub.infra.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.domain.exception.DomainException;
import br.rhhub.infra.config.AuthProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GoogleOAuthClientTest {

    private HttpServer server;
    private final AtomicReference<String> tokenForm = new AtomicReference<>();
    private final AtomicReference<String> userinfoAuth = new AtomicReference<>();
    private volatile int tokenStatus = 200;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            tokenForm.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, tokenStatus, tokenStatus == 200 ? "{\"access_token\":\"tok123\"}" : "{\"error\":\"invalid_grant\"}");
        });
        server.createContext("/userinfo", exchange -> {
            userinfoAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            reply(exchange, 200, "{\"sub\":\"g-1\",\"email\":\"Ana@Gmail.com\",\"email_verified\":true,\"name\":\"Ana\"}");
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private GoogleOAuthClient client() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new GoogleOAuthClient("cid", "secret", "https://app.exemplo.com/cb", "https://accounts.example/auth",
                base + "/token", base + "/userinfo");
    }

    @Test
    void authorizationUrlCarriesClientRedirectScopeAndState() {
        String url = client().authorizationUrl("abc 123");
        assertThat(url).startsWith("https://accounts.example/auth?client_id=cid")
                .contains("redirect_uri=https%3A%2F%2Fapp.exemplo.com%2Fcb")
                .contains("response_type=code").contains("scope=openid+email+profile").contains("state=abc+123");
    }

    @Test
    void exchangesTheCodeForTheProfile() {
        var profile = client().fetchProfile("the-code").block();
        assertThat(profile.sub()).isEqualTo("g-1");
        assertThat(profile.email()).isEqualTo("ana@gmail.com");
        assertThat(profile.emailVerified()).isTrue();
        assertThat(profile.name()).isEqualTo("Ana");
        assertThat(tokenForm.get()).contains("code=the-code").contains("client_secret=secret").contains("grant_type=authorization_code");
        assertThat(userinfoAuth.get()).isEqualTo("Bearer tok123");
    }

    @Test
    void googleRefusalBecomesUnauthorized() {
        tokenStatus = 400;
        assertThatThrownBy(() -> client().fetchProfile("bad").block())
                .isInstanceOfSatisfying(br.rhhub.application.port.out.GoogleIdentityProvider.LoginFailure.class,
                        f -> assertThat(f.reason()).isEqualTo("invalid_grant"));
        assertThatThrownBy(() -> client().fetchProfile(" ").block()).isInstanceOf(DomainException.Unauthorized.class);
    }

    @Test
    void reasonIsTheGoogleErrorCodeOrAGenericOne() {
        assertThat(GoogleOAuthClient.reasonOf(new RuntimeException("rede"))).isEqualTo("google_unreachable");
        var noJson = org.springframework.web.reactive.function.client.WebClientResponseException.create(
                502, "Bad Gateway", org.springframework.http.HttpHeaders.EMPTY, "<html>".getBytes(), StandardCharsets.UTF_8);
        assertThat(GoogleOAuthClient.reasonOf(noJson)).isEqualTo("google_http_502");
        var weird = org.springframework.web.reactive.function.client.WebClientResponseException.create(
                400, "Bad Request", org.springframework.http.HttpHeaders.EMPTY,
                "{\"error\":\"<script>alert(1)</script>\"}".getBytes(), StandardCharsets.UTF_8);
        assertThat(GoogleOAuthClient.reasonOf(weird)).isEqualTo("google_http_400"); // nunca devolve texto livre na URL
        var invalidClient = org.springframework.web.reactive.function.client.WebClientResponseException.create(
                401, "Unauthorized", org.springframework.http.HttpHeaders.EMPTY,
                "{\"error\":\"invalid_client\",\"error_description\":\"Unauthorized\"}".getBytes(), StandardCharsets.UTF_8);
        assertThat(GoogleOAuthClient.reasonOf(invalidClient)).isEqualTo("invalid_client");
    }

    @Test
    void disabledByDefaultAndEnablingNeedsAllThreeVariables() {
        var config = new GoogleConfig();
        var off = config.googleIdentityProvider(new AuthProperties(null, null, null, 60, 1000, null));
        assertThat(off.enabled()).isFalse();
        assertThatThrownBy(() -> off.authorizationUrl("s")).isInstanceOf(DomainException.NotFound.class);
        assertThatThrownBy(() -> off.fetchProfile("c").block()).isInstanceOf(DomainException.NotFound.class);

        var partial = new AuthProperties(null, null, null, 60, 1000, new AuthProperties.Google(true, "id", "", null));
        assertThatThrownBy(() -> config.googleIdentityProvider(partial)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_CLIENT_SECRET").hasMessageContaining("GOOGLE_REDIRECT_URI")
                .hasMessageNotContaining("GOOGLE_CLIENT_ID");

        var full = new AuthProperties(null, null, null, 60, 1000, new AuthProperties.Google(true, "id", "sec", "http://x/cb"));
        assertThat(config.googleIdentityProvider(full).enabled()).isTrue();
    }
}
