package br.rhhub.application.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.rhhub.application.port.out.RateLimiter;
import br.rhhub.domain.auth.AccessPolicy;
import br.rhhub.domain.auth.CodePurpose;
import br.rhhub.domain.auth.JwtService;
import br.rhhub.domain.auth.PasswordHasher;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.infra.TestSupport;
import br.rhhub.infra.config.AppProperties;
import br.rhhub.infra.config.AuthProperties;
import br.rhhub.infra.config.WebProperties;
import br.rhhub.infra.document.PdfDocxTextExtractor;
import br.rhhub.infra.email.HtmlEmailComposer;
import br.rhhub.service.AdminService;
import br.rhhub.service.AuthService;
import br.rhhub.service.Fakes;
import br.rhhub.service.ProfilePhotoService;
import br.rhhub.service.ResumeService;
import br.rhhub.service.UserService;
import br.rhhub.service.VacancyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

/** As rotas de ponta a ponta (filtros, cookies, erros, CORS), com os adapters trocados por versões em memória. */
class WebApiTest {

    private static final byte[] PDF = "%PDF-1.7 meu curriculo".getBytes();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    static final Fakes.Users users = new Fakes.Users();
    static final Fakes.Codes codes = new Fakes.Codes();
    static final Fakes.Mailbox mailbox = new Fakes.Mailbox();
    static final Fakes.Analysis analysis = new Fakes.Analysis();
    static final Map<String, Integer> hits = new HashMap<>();
    static volatile int rateLimit = 1000;

    @Configuration
    @EnableWebFlux
    @Import({AuthFilter.class, RateLimitFilter.class, SecurityHeadersFilter.class, CorsConfig.class,
            ApiExceptionHandler.class, WebSupport.class, AuthController.class, UserController.class,
            VacancyController.class, ResumeController.class, AdminController.class, GoogleController.class,
            HealthController.class})
    static class Context {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean AppProperties appProperties() { return TestSupport.props(); }
        @Bean AuthProperties authProperties() { return new AuthProperties(null, "root@gmail.com", null, rateLimit, 100000, null); }
        @Bean WebProperties webProperties() { return new WebProperties("http://front.test/", false, "Strict"); }
        @Bean JwtService jwt(ObjectMapper m) { return new JwtService("segredo-de-teste-com-mais-de-32-caracteres", m); }
        @Bean AccessPolicy policy() { return new AccessPolicy("root@gmail.com"); }
        @Bean PasswordHasher hasher() {
            return new PasswordHasher() {
                public String hash(String p) { return "h:" + p; }
                public boolean matches(String p, String h) { return h.equals("h:" + p); }
            };
        }
        @Bean RateLimiter limiter() {
            return (key, limit) -> {
                int total = hits.merge(key, 1, Integer::sum);
                return total > limit ? Mono.error(new DomainException.TooManyRequests("Muitas requisições.")) : Mono.empty();
            };
        }
        @Bean Fakes.Refresh refresh() { return new Fakes.Refresh(); }
        @Bean Fakes.Vacancies vacancies() { return new Fakes.Vacancies(); }
        @Bean Fakes.Resumes resumes() { return new Fakes.Resumes(); }
        @Bean Fakes.Photos photos() { return new Fakes.Photos(); }
        @Bean AuthService authService(JwtService jwt, PasswordHasher hasher, Fakes.Refresh refresh, Clock clock) {
            return new AuthService(users, hasher, jwt, refresh, codes, new HtmlEmailComposer(), mailbox, new Fakes.Google() {
                @Override public boolean enabled() { return false; }
                @Override public String authorizationUrl(String state) { throw new DomainException.NotFound("Login com Google desativado."); }
            }, clock, true);
        }
        @Bean UserService userService(PasswordHasher hasher, Fakes.Refresh refresh, AccessPolicy policy) {
            return new UserService(users, hasher, refresh, policy);
        }
        @Bean VacancyService vacancyService(Fakes.Vacancies v, AccessPolicy policy) { return new VacancyService(v, policy); }
        @Bean ResumeService resumeService(Fakes.Resumes r, Fakes.Vacancies v) {
            return new ResumeService(r, v, new PdfDocxTextExtractor(), analysis);
        }
        @Bean AdminService adminService(Fakes.Refresh refresh, AccessPolicy policy) { return new AdminService(users, refresh, policy); }
        @Bean ProfilePhotoService photoService(Fakes.Photos p) { return new ProfilePhotoService(p, users); }
    }

    private AnnotationConfigApplicationContext ctx;
    private WebTestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void start() {
        users.byId.clear();
        codes.issued.clear();
        mailbox.sent.clear();
        analysis.queued.clear();
        hits.clear();
        rateLimit = 1000;
        ctx = new AnnotationConfigApplicationContext(Context.class);
        client = WebTestClient.bindToApplicationContext(ctx).configureClient().baseUrl("http://localhost").build();
        ctx.getBean(AuthService.class).ensureRootAdmin("root@gmail.com", "Admin12345").block();
        ctx.getBean(Fakes.Vacancies.class).byId.clear();
        ctx.getBean(Fakes.Resumes.class).byId.clear();
        ctx.getBean(Fakes.Photos.class).byUser.clear();
    }

    @AfterEach
    void stop() {
        ctx.close();
    }

    // ---- ajudantes ----

    /** Cookies de uma sessão (os valores crus que o navegador mandaria de volta). */
    private record Cookies(Map<String, String> values) {
        void apply(org.springframework.util.MultiValueMap<String, String> target) {
            values.forEach(target::add);
        }
        String get(String name) { return values.get(name); }
    }

    private static Cookies cookiesOf(WebTestClient.ResponseSpec spec) {
        var map = new HashMap<String, String>();
        var result = spec.returnResult(String.class);
        result.getResponseCookies().forEach((name, list) -> {
            if (!list.get(0).getValue().isEmpty()) {
                map.put(name, list.get(0).getValue());
            }
        });
        return new Cookies(map);
    }

    private JsonNode json(WebTestClient.ResponseSpec spec) throws Exception {
        return mapper.readTree(spec.returnResult(String.class).getResponseBodyContent());
    }

    private WebTestClient.ResponseSpec post(String uri, Object body, Cookies cookies) {
        return client.post().uri(uri).cookies(c -> { if (cookies != null) cookies.apply(c); })
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body == null ? Map.of() : body).exchange();
    }

    private WebTestClient.ResponseSpec get(String uri, Cookies cookies) {
        return client.get().uri(uri).cookies(c -> { if (cookies != null) cookies.apply(c); }).exchange();
    }

    private Cookies signup(String name, String email, String cpf) throws Exception {
        var sent = json(post("/api/sender/signup-code", Map.of("name", name, "email", email), null).expectStatus().isOk());
        String code = sent.get("code").asText();
        var created = post("/api/users", Map.of("name", name, "email", email, "cpf", cpf, "age", 30, "gender", "female",
                "password", "Senha123", "code", code), null).expectStatus().isCreated();
        return cookiesOf(created);
    }

    private Cookies rootLogin() {
        return cookiesOf(post("/api/auth/login", Map.of("email", "root@gmail.com", "password", "Admin12345"), null)
                .expectStatus().isOk());
    }

    private WebTestClient.ResponseSpec upload(String uri, String field, String fileName, byte[] bytes, String vacancyId, Cookies cookies) {
        var builder = new MultipartBodyBuilder();
        if (vacancyId != null) {
            builder.part("vacancyId", vacancyId);
        }
        builder.part(field, new org.springframework.core.io.ByteArrayResource(bytes) {
            @Override public String getFilename() { return fileName; }
        }).contentType(MediaType.APPLICATION_OCTET_STREAM);
        return client.method(uri.equals("/api/users/me/photo") ? org.springframework.http.HttpMethod.PUT : org.springframework.http.HttpMethod.POST)
                .uri(uri).cookies(c -> { if (cookies != null) cookies.apply(c); })
                .body(BodyInserters.fromMultipartData(builder.build())).exchange();
    }

    // ---- testes ----

    @Test
    void healthIsPublicAndSecurityHeadersAreSet() {
        get("/api/health", null).expectStatus().isOk().expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("X-Frame-Options", "DENY");
    }

    @Test
    void protectedRoutesNeedALoginAndErrorsUseTheStandardShape() throws Exception {
        var body = json(get("/api/users/me", null).expectStatus().isUnauthorized());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("error").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("path").asText()).isEqualTo("/api/users/me");
        assertThat(body.get("message").asText()).isNotBlank();

        get("/api/vacancies", null).expectStatus().isUnauthorized();
        get("/api/nada-aqui", new Cookies(Map.of("X-user_token", "lixo.lixo.lixo"))).expectStatus().isUnauthorized();
        get("/api/health", null).expectStatus().isOk();
    }

    @Test
    void signupOpensASessionWithHardenedCookiesAndNeverLeaksSecrets() throws Exception {
        var sent = post("/api/sender/signup-code", Map.of("name", "Ana", "email", "ana@gmail.com"), null).expectStatus().isOk();
        String code = json(sent).get("code").asText();
        var response = post("/api/users", Map.of("name", "Ana", "email", "ana@gmail.com", "cpf", "12345678901", "age", 30,
                "gender", "female", "password", "Senha123", "code", code), null).expectStatus().isCreated().returnResult(String.class);

        var access = response.getResponseCookies().getFirst("X-user_token");
        var refresh = response.getResponseCookies().getFirst("X-user_refresh_token");
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(access.getSameSite()).isEqualTo("Strict");
        assertThat(access.getPath()).isEqualTo("/");
        assertThat(access.getMaxAge().toMinutes()).isEqualTo(15);
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(refresh.getPath()).isEqualTo("/api/auth");
        assertThat(refresh.getMaxAge().toDays()).isEqualTo(7);

        String body = new String(response.getResponseBodyContent());
        assertThat(body).contains("\"public_id\"").contains("ana@gmail.com").doesNotContain("password").doesNotContain("Senha123")
                .doesNotContain("h:Senha123").doesNotContain("google_sub");
        assertThat(mailbox.sent).hasSize(1);
    }

    @Test
    void meReturnsTheUserAndPatchEditsAllowedFieldsOnly() throws Exception {
        var ana = signup("Ana", "ana@gmail.com", "12345678901");
        var me = json(get("/api/users/me", ana).expectStatus().isOk());
        assertThat(me.get("email").asText()).isEqualTo("ana@gmail.com");
        assertThat(me.get("role").asText()).isEqualTo("user");
        assertThat(me.get("has_photo").asBoolean()).isFalse();

        var edited = json(client.patch().uri("/api/users/me").cookies(ana::apply).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("set", "age", "value", 41)).exchange().expectStatus().isOk());
        assertThat(edited.get("age").asInt()).isEqualTo(41);

        for (Object bad : new Object[] {Map.of("set", "role", "value", "admin"), Map.of("set", "age", "value", "x"),
                Map.of("set", "age", "value", 10), Map.of("set", "two_factor", "value", "sim"), Map.of("value", 1)}) {
            client.patch().uri("/api/users/me").cookies(ana::apply).contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(bad).exchange().expectStatus().isBadRequest();
        }
    }

    @Test
    void validationDuplicatesAndBadCredentialsGetTheRightStatus() throws Exception {
        signup("Ana", "ana@gmail.com", "12345678901");
        post("/api/sender/signup-code", Map.of("name", "Ana", "email", "ana@gmail.com"), null).expectStatus().isEqualTo(HttpStatus.CONFLICT);
        post("/api/sender/signup-code", Map.of("name", "Bia", "email", "bia@outlook.com"), null).expectStatus().isBadRequest();
        post("/api/users", Map.of("name", "Bia"), null).expectStatus().isBadRequest();
        client.post().uri("/api/users").contentType(MediaType.APPLICATION_JSON).bodyValue("{quebrado").exchange().expectStatus().isBadRequest();
        post("/api/auth/login", Map.of("email", "ana@gmail.com", "password", "Errada123"), null).expectStatus().isUnauthorized();
        post("/api/auth/login", Map.of("email", "ninguem@gmail.com", "password", "Senha123"), null).expectStatus().isUnauthorized();
    }

    @Test
    void twoFactorLoginNeedsTheTemporaryCookieAndTheCode() throws Exception {
        var ana = signup("Ana", "ana@gmail.com", "12345678901");
        client.patch().uri("/api/users/me").cookies(ana::apply).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("set", "two_factor", "value", true)).exchange().expectStatus().isOk();

        var login = post("/api/auth/login", Map.of("email", "ana@gmail.com", "password", "Senha123"), null).expectStatus().isOk();
        assertThat(json(login).get("auth").asText()).isEqualTo("required");
        var temp = cookiesOf(login);
        assertThat(temp.values()).containsOnlyKeys("X-auth2_token"); // ainda sem sessão
        get("/api/users/me", temp).expectStatus().isUnauthorized();

        post("/api/auth/2fa/send", null, null).expectStatus().isUnauthorized(); // sem o cookie
        String code = json(post("/api/auth/2fa/send", null, temp).expectStatus().isOk()).get("code").asText();
        post("/api/auth/2fa/verify", Map.of("code", "000000"), temp).expectStatus().isUnauthorized();
        var verified = post("/api/auth/2fa/verify", Map.of("code", code), temp).expectStatus().isOk();
        var session = cookiesOf(verified);
        assertThat(session.values()).containsKeys("X-user_token", "X-user_refresh_token");
        get("/api/users/me", session).expectStatus().isOk();
    }

    @Test
    void refreshRenewsTheAccessTokenAndLogoutKillsTheSession() throws Exception {
        var ana = signup("Ana", "ana@gmail.com", "12345678901");
        var renewed = cookiesOf(post("/api/auth/refresh", null, new Cookies(Map.of("X-user_refresh_token", ana.get("X-user_refresh_token"))))
                .expectStatus().isOk());
        assertThat(renewed.get("X-user_token")).isNotBlank();
        post("/api/auth/refresh", null, null).expectStatus().isUnauthorized();
        post("/api/auth/refresh", null, new Cookies(Map.of("X-user_refresh_token", ana.get("X-user_token")))).expectStatus().isUnauthorized();

        var out = post("/api/auth/logout", null, ana).expectStatus().isOk().returnResult(String.class);
        // os três cookies saem expirados NA resposta do logout (senão o token de acesso continuaria valendo no navegador)
        for (String name : new String[] {"X-user_token", "X-user_refresh_token", "X-auth2_token"}) {
            var cleared = out.getResponseCookies().getFirst(name);
            assertThat(cleared).as(name).isNotNull();
            assertThat(cleared.getMaxAge().isZero()).as(name + " expira na hora").isTrue();
            assertThat(cleared.getValue()).isEmpty();
        }
        post("/api/auth/refresh", null, new Cookies(Map.of("X-user_refresh_token", ana.get("X-user_refresh_token"))))
                .expectStatus().isUnauthorized();
        // sair sem nenhuma sessão não é erro e também limpa os cookies
        var none = post("/api/auth/logout", null, null).expectStatus().isOk().returnResult(String.class);
        assertThat(none.getResponseCookies().getFirst("X-user_token").getMaxAge().isZero()).isTrue();
    }

    @Test
    void logoutWorksWithAnExpiredAccessTokenUsingTheRefreshCookieAndRevokesIt() throws Exception {
        var ana = signup("Ana", "ana@gmail.com", "12345678901");
        // só o refresh cookie (o de acesso já venceu): o logout identifica a pessoa por ele e revoga a sessão
        var onlyRefresh = new Cookies(Map.of("X-user_refresh_token", ana.get("X-user_refresh_token")));
        post("/api/auth/logout", null, onlyRefresh).expectStatus().isOk();
        post("/api/auth/refresh", null, onlyRefresh).expectStatus().isUnauthorized();
    }

    @Test
    void deleteAccountAlsoClearsTheCookies() throws Exception {
        var ana = signup("Ana", "ana@gmail.com", "12345678901");
        var res = client.method(org.springframework.http.HttpMethod.DELETE).uri("/api/users/me").cookies(ana::apply)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("password", "Senha123")).exchange()
                .expectStatus().isNoContent().returnResult(String.class);
        assertThat(res.getResponseCookies().getFirst("X-user_token").getMaxAge().isZero()).isTrue();
    }

    @Test
    void passwordChangeAndResetByCode() throws Exception {
        var ana = signup("Ana", "ana@gmail.com", "12345678901");
        client.patch().uri("/api/auth/password").cookies(ana::apply).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("password", "Errada123", "newPassword", "Nova12345")).exchange().expectStatus().isUnauthorized();
        client.patch().uri("/api/auth/password").cookies(ana::apply).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("password", "Senha123", "newPassword", "Nova12345")).exchange().expectStatus().isOk();
        post("/api/auth/login", Map.of("email", "ana@gmail.com", "password", "Nova12345"), null).expectStatus().isOk();

        String code = json(post("/api/auth/password/reset-code", Map.of("email", "ana@gmail.com"), null).expectStatus().isOk()).get("code").asText();
        post("/api/auth/password/reset", Map.of("email", "ana@gmail.com", "code", code, "newPassword", "Outra12345"), null).expectStatus().isOk();
        post("/api/auth/login", Map.of("email", "ana@gmail.com", "password", "Outra12345"), null).expectStatus().isOk();
        // e-mail desconhecido responde igual, sem revelar nada
        var ghost = json(post("/api/auth/password/reset-code", Map.of("email", "fantasma@gmail.com"), null).expectStatus().isOk());
        assertThat(ghost.has("code")).isFalse();
    }

    @Test
    void vacanciesAreReadByAnyoneButManagedByAdminsAndCreatedByIsHiddenFromCandidates() throws Exception {
        var ana = signup("Ana", "ana@gmail.com", "12345678901");
        var root = rootLogin();
        post("/api/vacancies", Map.of("name", "Dev", "description", "Backend"), ana).expectStatus().isForbidden();
        var created = json(post("/api/vacancies", Map.of("name", "Dev", "description", "Backend"), root).expectStatus().isCreated());
        long id = created.get("id").asLong();
        assertThat(created.has("created_by")).isTrue();
        post("/api/vacancies", Map.of("name", "Dev", "description", "x"), root).expectStatus().isEqualTo(HttpStatus.CONFLICT);

        var forCandidate = json(get("/api/vacancies", ana).expectStatus().isOk());
        assertThat(forCandidate).hasSize(1);
        assertThat(forCandidate.get(0).has("created_by")).isFalse();
        assertThat(json(get("/api/vacancies/" + id, root).expectStatus().isOk()).has("created_by")).isTrue();
        get("/api/vacancies/999", ana).expectStatus().isNotFound();
        get("/api/vacancies/abc", ana).expectStatus().isBadRequest();

        client.patch().uri("/api/vacancies/" + id).cookies(ana::apply).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("description", "x")).exchange().expectStatus().isForbidden();
        var edited = json(client.patch().uri("/api/vacancies/" + id).cookies(root::apply).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("description", "Sênior")).exchange().expectStatus().isOk());
        assertThat(edited.get("description").asText()).isEqualTo("Sênior");
        client.delete().uri("/api/vacancies/" + id).cookies(ana::apply).exchange().expectStatus().isForbidden();
        client.delete().uri("/api/vacancies/" + id).cookies(root::apply).exchange().expectStatus().isNoContent();
    }

    @Test
    void resumeUploadDownloadAndAccessRules() throws Exception {
        var ana = signup("Ana", "ana@gmail.com", "12345678901");
        var bia = signup("Bia", "bia@gmail.com", "10987654321");
        var root = rootLogin();
        long vacancy = json(post("/api/vacancies", Map.of("name", "Dev", "description", "Backend"), root).expectStatus().isCreated()).get("id").asLong();

        upload("/api/resumes", "file", "meu cv.pdf", "texto puro".getBytes(), "" + vacancy, ana).expectStatus().isBadRequest();
        upload("/api/resumes", "file", "cv.pdf", PDF, "999", ana).expectStatus().isNotFound();
        upload("/api/resumes", "file", "cv.pdf", PDF, "abc", ana).expectStatus().isBadRequest();
        upload("/api/resumes", "file", "cv.pdf", PDF, "" + vacancy, null).expectStatus().isUnauthorized();

        var created = json(upload("/api/resumes", "file", "meu cv.pdf", PDF, "" + vacancy, ana).expectStatus().isCreated());
        long id = created.get("id").asLong();
        assertThat(created.get("status").asText()).isEqualTo("pending");
        assertThat(created.get("content_type").asText()).isEqualTo("application/pdf");
        assertThat(created.has("file")).isFalse();
        assertThat(analysis.queued).containsExactly(id);
        upload("/api/resumes", "file", "cv.pdf", PDF, "" + vacancy, ana).expectStatus().isEqualTo(HttpStatus.CONFLICT);

        assertThat(json(get("/api/resumes/me", ana).expectStatus().isOk())).hasSize(1);
        assertThat(json(get("/api/resumes/me", bia).expectStatus().isOk())).hasSize(0);
        get("/api/resumes/" + id, ana).expectStatus().isOk();
        get("/api/resumes/" + id, root).expectStatus().isOk();
        get("/api/resumes/" + id, bia).expectStatus().isForbidden();
        get("/api/resumes/" + id + "/file", bia).expectStatus().isForbidden();
        get("/api/resumes", ana).expectStatus().isForbidden();
        assertThat(json(get("/api/resumes?status=pending&vacancyId=" + vacancy, root).expectStatus().isOk())).hasSize(1);
        get("/api/resumes?status=xyz", root).expectStatus().isBadRequest();

        var file = get("/api/resumes/" + id + "/file", ana).expectStatus().isOk()
                .expectHeader().contentType("application/pdf")
                .expectHeader().value(HttpHeaders.CONTENT_DISPOSITION, v -> assertThat(v).startsWith("attachment").contains("meu%20cv.pdf"))
                .returnResult(byte[].class);
        assertThat(file.getResponseBodyContent()).isEqualTo(PDF);

        client.delete().uri("/api/resumes/" + id).cookies(bia::apply).exchange().expectStatus().isForbidden();
        client.delete().uri("/api/resumes/" + id).cookies(ana::apply).exchange().expectStatus().isNoContent();
        get("/api/resumes/" + id, ana).expectStatus().isNotFound();
    }

    @Test
    void profilePhotoUploadServeAndRemove() throws Exception {
        var ana = signup("Ana", "ana@gmail.com", "12345678901");
        var bia = signup("Bia", "bia@gmail.com", "10987654321");
        var root = rootLogin();
        String publicId = json(get("/api/users/me", ana).expectStatus().isOk()).get("public_id").asText();

        get("/api/users/me/photo", ana).expectStatus().isNotFound();
        upload("/api/users/me/photo", "file", "eu.png", "<svg onload=alert(1)>".getBytes(), null, ana).expectStatus().isBadRequest();
        upload("/api/users/me/photo", "file", "eu.png", PNG, null, ana).expectStatus().isOk();

        assertThat(json(get("/api/users/me", ana).expectStatus().isOk()).get("has_photo").asBoolean()).isTrue();
        var served = get("/api/users/me/photo", ana).expectStatus().isOk().expectHeader().contentType("image/png")
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff").returnResult(byte[].class);
        assertThat(served.getResponseBodyContent()).isEqualTo(PNG);

        get("/api/admin/users/" + publicId + "/photo", root).expectStatus().isOk();
        get("/api/admin/users/" + publicId + "/photo", bia).expectStatus().isForbidden();

        client.delete().uri("/api/users/me/photo").cookies(ana::apply).exchange().expectStatus().isNoContent();
        get("/api/users/me/photo", ana).expectStatus().isNotFound();
    }

    @Test
    void adminRoutesFollowThePermissionTable() throws Exception {
        var ana = signup("Ana", "ana@gmail.com", "12345678901");
        signup("Bia", "bia@gmail.com", "10987654321");
        var root = rootLogin();
        get("/api/admin/users", ana).expectStatus().isForbidden();
        var list = json(get("/api/admin/users", root).expectStatus().isOk());
        assertThat(list).hasSize(3);
        assertThat(list.toString()).doesNotContain("password").doesNotContain("google_sub");

        String anaId = json(get("/api/users/me", ana).expectStatus().isOk()).get("public_id").asText();
        var promoted = json(client.patch().uri("/api/admin/users/" + anaId + "/role").cookies(root::apply).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("role", "admin")).exchange().expectStatus().isOk());
        assertThat(promoted.get("role").asText()).isEqualTo("admin");
        client.patch().uri("/api/admin/users/" + anaId + "/role").cookies(root::apply).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("role", "chefe")).exchange().expectStatus().isBadRequest();
        get("/api/admin/users/nao-e-uuid", root).expectStatus().isBadRequest();

        // agora a Ana é admin, mas só o raiz exclui e rebaixa
        String rootId = json(get("/api/users/me", root).expectStatus().isOk()).get("public_id").asText();
        String biaId = list.findValuesAsText("public_id").stream().filter(v -> !v.equals(anaId))
                .filter(v -> !v.equals(rootId)).findFirst().orElseThrow();
        client.delete().uri("/api/admin/users/" + biaId).cookies(ana::apply).exchange().expectStatus().isForbidden();
        client.delete().uri("/api/admin/users/" + biaId).cookies(root::apply).exchange().expectStatus().isNoContent();
        client.delete().uri("/api/admin/users/" + biaId).cookies(root::apply).exchange().expectStatus().isNotFound();
    }

    @Test
    void corsAllowsOnlyTheConfiguredOriginWithCredentials() {
        client.options().uri("/api/users/me").header("Origin", "http://front.test")
                .header("Access-Control-Request-Method", "PATCH").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://front.test")
                .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true");
        client.options().uri("/api/users/me").header("Origin", "http://malvado.test")
                .header("Access-Control-Request-Method", "PATCH").exchange().expectStatus().isForbidden();
    }

    @Test
    void googleIsOffByDefault() throws Exception {
        assertThat(json(get("/api/auth/providers", null).expectStatus().isOk()).get("google").asBoolean()).isFalse();
        get("/oauth2/authorization/google", null).expectStatus().isNotFound();
        get("/login/oauth2/code/google?code=x&state=y", null).expectStatus().isNotFound();
    }

    @Test
    void rateLimitReturns429AndSensitiveRoutesHaveATighterCeiling() throws Exception {
        signup("Ana", "ana@gmail.com", "12345678901");
        hits.clear();
        for (int i = 0; i < RateLimitFilter.SENSITIVE_LIMIT; i++) {
            post("/api/auth/login", Map.of("email", "ana@gmail.com", "password", "Errada123"), null).expectStatus().isUnauthorized();
        }
        var blocked = json(post("/api/auth/login", Map.of("email", "ana@gmail.com", "password", "Senha123"), null)
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(blocked.get("status").asInt()).isEqualTo(429);
        get("/api/health", null).expectStatus().isOk(); // health nunca é limitado
    }

    @Test
    void ipComesFromTheLastForwardedEntryWhichIsTheProxysOwn() {
        signupQuietly();
        hits.clear();
        for (int i = 0; i < RateLimitFilter.SENSITIVE_LIMIT; i++) {
            client.post().uri("/api/auth/login").header("X-Forwarded-For", "1.1.1." + i + ", 9.9.9.9")
                    .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("email", "a@gmail.com", "password", "x")).exchange();
        }
        // o cliente varia o primeiro IP, mas o do proxy (9.9.9.9) é o mesmo: o limite vale
        client.post().uri("/api/auth/login").header("X-Forwarded-For", "2.2.2.2, 9.9.9.9")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("email", "a@gmail.com", "password", "x")).exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private void signupQuietly() {
        try {
            signup("Ana", "ana@gmail.com", "12345678901");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void codesAreNotExposedOutsideTestEnvironmentByTheService() {
        // o serviço de produção (exposeCode=false) nunca devolve o código
        var prod = new AuthService(users, new PasswordHasher() {
            public String hash(String p) { return p; }
            public boolean matches(String p, String h) { return p.equals(h); }
        }, ctx.getBean(JwtService.class), ctx.getBean(Fakes.Refresh.class), codes, new HtmlEmailComposer(), mailbox,
                new Fakes.Google(), Clock.systemUTC(), false);
        assertThat(prod.requestSignupCode("Zé", "ze@gmail.com").block().code()).isNull();
        assertThat(codes.last(CodePurpose.SIGNUP, "ze@gmail.com")).matches("\\d{6}");
    }
}
