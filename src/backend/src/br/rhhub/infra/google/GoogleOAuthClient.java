package br.rhhub.infra.google;

import br.rhhub.application.port.out.GoogleIdentityProvider;
import br.rhhub.domain.exception.DomainException;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * OAuth2 do Google, fluxo de código: monta a URL de autorização, troca o code pelo token e lê o perfil. O token vem
 * direto do Google por TLS, então o perfil é lido do endpoint userinfo, sem validar o ID token à mão.
 */
public class GoogleOAuthClient implements GoogleIdentityProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthClient.class);

    static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    static final String USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String authUrl;
    private final String tokenUrl;
    private final String userinfoUrl;
    private final WebClient client;

    public GoogleOAuthClient(String clientId, String clientSecret, String redirectUri) {
        this(clientId, clientSecret, redirectUri, AUTH_URL, TOKEN_URL, USERINFO_URL);
    }

    GoogleOAuthClient(String clientId, String clientSecret, String redirectUri, String authUrl, String tokenUrl,
            String userinfoUrl) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.authUrl = authUrl;
        this.tokenUrl = tokenUrl;
        this.userinfoUrl = userinfoUrl;
        this.client = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().proxyWithSystemProperties().responseTimeout(Duration.ofSeconds(10))))
                .build();
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String authorizationUrl(String state) {
        return authUrl + "?client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUri)
                + "&response_type=code"
                + "&scope=" + enc("openid email profile")
                + "&state=" + enc(state)
                + "&prompt=select_account";
    }

    @Override
    public Mono<Profile> fetchProfile(String code) {
        if (code == null || code.isBlank()) {
            return Mono.error(new DomainException.Unauthorized("Login com Google sem código."));
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");
        return client.post().uri(tokenUrl).body(BodyInserters.fromFormData(form))
                .retrieve().bodyToMono(JsonNode.class)
                .map(json -> json.path("access_token").asText(""))
                .filter(token -> !token.isBlank())
                .flatMap(token -> client.get().uri(userinfoUrl)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve().bodyToMono(JsonNode.class))
                .map(info -> new Profile(
                        info.path("sub").asText(""),
                        info.path("email").asText("").toLowerCase(),
                        info.path("email_verified").asBoolean(false),
                        info.path("name").asText("")))
                .filter(profile -> !profile.sub().isBlank() && !profile.email().isBlank())
                .switchIfEmpty(Mono.error(new DomainException.Unauthorized("O Google não confirmou o login.")))
                .doOnError(e -> {
                    // A resposta de erro do Google (invalid_client, invalid_grant, redirect_uri_mismatch...) não tem segredo
                    // e é o que diz por que o login falhou.
                    if (e instanceof WebClientResponseException http) {
                        log.warn("Google recusou ({} em {}): {}", http.getStatusCode().value(),
                                http.getRequest() == null ? "?" : http.getRequest().getURI().getHost(),
                                http.getResponseBodyAsString());
                    } else if (!(e instanceof DomainException)) {
                        log.warn("Falha ao falar com o Google: {}", e.toString());
                    }
                })
                .onErrorMap(e -> !(e instanceof DomainException), e -> new LoginFailure(reasonOf(e), e));
    }

    /** O campo "error" da resposta do Google (invalid_client, invalid_grant...); sem ele, o status HTTP. */
    static String reasonOf(Throwable error) {
        if (error instanceof WebClientResponseException http) {
            try {
                String code = new com.fasterxml.jackson.databind.ObjectMapper().readTree(http.getResponseBodyAsString())
                        .path("error").asText("");
                if (code.matches("[a-z_]{3,40}")) {
                    return code;
                }
            } catch (Exception ignored) {
                // corpo que não é JSON
            }
            return "google_http_" + http.getStatusCode().value();
        }
        return "google_unreachable";
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
