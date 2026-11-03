package br.rhhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.domain.auth.AccessPolicy;
import br.rhhub.domain.auth.CodePurpose;
import br.rhhub.domain.auth.JwtService;
import br.rhhub.domain.auth.TokenType;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.AuthProvider;
import br.rhhub.domain.model.Role;
import br.rhhub.infra.email.HtmlEmailComposer;
import br.rhhub.infra.security.BcryptPasswordHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC);
    private static final String EMAIL = "ana@gmail.com";

    private Fakes.Users users;
    private Fakes.Refresh refresh;
    private Fakes.Codes codes;
    private Fakes.Mailbox mailbox;
    private Fakes.Google google;
    private JwtService jwt;
    private AuthService auth;
    private UserService account;

    @BeforeEach
    void setUp() {
        users = new Fakes.Users();
        refresh = new Fakes.Refresh();
        codes = new Fakes.Codes();
        mailbox = new Fakes.Mailbox();
        google = new Fakes.Google();
        jwt = new JwtService("segredo-de-teste-com-mais-de-32-caracteres", new ObjectMapper());
        var hasher = new BcryptPasswordHasher();
        auth = new AuthService(users, hasher, jwt, refresh, codes, new HtmlEmailComposer(), mailbox, google, CLOCK, true);
        account = new UserService(users, hasher, refresh, new AccessPolicy("root@gmail.com"));
    }

    private AuthService.Session signUp() {
        auth.requestSignupCode("Ana Souza", EMAIL).block();
        String code = codes.last(CodePurpose.SIGNUP, EMAIL);
        return auth.register("Ana Souza", EMAIL, "123.456.789-01", 30, "female", "Senha123", code).block();
    }

    @Test
    void signupSendsACodeByEmailAndRegisterOpensASession() {
        var sent = auth.requestSignupCode("Ana Souza", "ANA@gmail.com").block();
        assertThat(sent.code()).matches("\\d{6}");
        assertThat(mailbox.sent).hasSize(1);
        assertThat(mailbox.sent.get(0).to()).isEqualTo(EMAIL);
        assertThat(mailbox.sent.get(0).content()).contains(sent.code()).contains("Ana Souza");

        var session = auth.register("Ana Souza", EMAIL, "12345678901", 30, "female", "Senha123", sent.code()).block();
        assertThat(session.user().id()).isNotNull();
        assertThat(session.user().role()).isEqualTo(Role.USER);
        assertThat(session.user().passwordHash()).isNotEqualTo("Senha123").startsWith("$2");
        assertThat(jwt.verify(session.accessToken(), TokenType.ACCESS, CLOCK.instant()).subject()).isEqualTo(session.user().publicId());
        var refreshClaims = jwt.verify(session.refreshToken(), TokenType.REFRESH, CLOCK.instant());
        assertThat(refresh.tokens.get(session.user().publicId())).isEqualTo(refreshClaims.id());
    }

    @Test
    void signupRefusesDuplicatesBadCodesAndInvalidFields() {
        signUp();
        assertThatThrownBy(() -> auth.requestSignupCode("Ana", EMAIL).block()).isInstanceOf(DomainException.Conflict.class);
        assertThatThrownBy(() -> auth.requestSignupCode("Bia", "bia@outlook.com").block()).isInstanceOf(DomainException.Invalid.class);

        auth.requestSignupCode("Bia", "bia@gmail.com").block();
        assertThatThrownBy(() -> auth.register("Bia", "bia@gmail.com", "12345678901", 30, "female", "Senha123", "000000").block())
                .isInstanceOf(DomainException.Conflict.class); // CPF repetido, antes mesmo do código
        assertThatThrownBy(() -> auth.register("Bia", "bia@gmail.com", "98765432100", 30, "female", "Senha123", "999999").block())
                .isInstanceOf(DomainException.Unauthorized.class);
        assertThatThrownBy(() -> auth.register("Bia", "bia@gmail.com", "98765432100", 17, "female", "Senha123", "000002").block())
                .isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> auth.register("Bia", "bia@gmail.com", "98765432100", 30, "female", "fraca", "000002").block())
                .isInstanceOf(DomainException.Invalid.class);
    }

    @Test
    void loginWithoutTwoFactorOpensTheSession() {
        signUp();
        var result = auth.login("Ana@Gmail.com", "Senha123").block();
        assertThat(result.twoFactorRequired()).isFalse();
        assertThat(result.session().user().email()).isEqualTo(EMAIL);
    }

    @Test
    void wrongPasswordIsUnauthorizedAndAnUnknownAccountIsReportedAsNotFound() {
        signUp();
        var wrong = catchThrowable(() -> auth.login(EMAIL, "Errada123").block());
        var unknown = catchThrowable(() -> auth.login("ninguem@gmail.com", "Senha123").block());
        assertThat(wrong).isInstanceOf(DomainException.Unauthorized.class);
        assertThat(unknown).isInstanceOf(DomainException.NotFound.class).hasMessage(AuthService.NO_ACCOUNT);
    }

    @Test
    void googleLoginWithoutCreatingFailsWhenThereIsNoAccount() {
        var failure = catchThrowable(() -> auth.loginWithGoogle("ok", false).block());
        assertThat(failure).isInstanceOf(DomainException.NotFound.class);
        assertThat(auth.loginWithGoogle("ok", true).block().user()).isNotNull();
        assertThat(auth.loginWithGoogle("ok", false).block().user()).isNotNull();
    }

    private static Throwable catchThrowable(Runnable r) {
        try {
            r.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    @Test
    void twoFactorFlowNeedsTheTokenAndTheCode() {
        var session = signUp();
        account.update(session.user().publicId(), new UserService.Update(null, null, true, null)).block();

        var login = auth.login(EMAIL, "Senha123").block();
        assertThat(login.twoFactorRequired()).isTrue();
        assertThat(login.session()).isNull();
        String token = login.twoFactorToken();

        var sent = auth.sendTwoFactorCode(token).block();
        assertThat(mailbox.sent).hasSize(2); // cadastro + 2FA
        assertThatThrownBy(() -> auth.verifyTwoFactor(token, "999999").block()).isInstanceOf(DomainException.Unauthorized.class);
        var opened = auth.verifyTwoFactor(token, sent.code()).block();
        assertThat(opened.user().email()).isEqualTo(EMAIL);

        // o token de acesso não serve como token de 2FA
        assertThatThrownBy(() -> auth.sendTwoFactorCode(opened.accessToken()).block()).isInstanceOf(DomainException.Unauthorized.class);
    }

    @Test
    void refreshGivesANewAccessTokenUntilLogout() {
        var session = signUp();
        String access = auth.refresh(session.refreshToken()).block();
        assertThat(jwt.verify(access, TokenType.ACCESS, CLOCK.instant()).subject()).isEqualTo(session.user().publicId());

        auth.logout(session.user().publicId()).block();
        assertThatThrownBy(() -> auth.refresh(session.refreshToken()).block()).isInstanceOf(DomainException.Unauthorized.class);
        assertThatThrownBy(() -> auth.refresh(session.accessToken()).block()).isInstanceOf(DomainException.Unauthorized.class);
    }

    @Test
    void newLoginInvalidatesTheOlderRefreshToken() {
        var first = signUp();
        var second = auth.login(EMAIL, "Senha123").block().session();
        assertThatThrownBy(() -> auth.refresh(first.refreshToken()).block()).isInstanceOf(DomainException.Unauthorized.class);
        assertThat(auth.refresh(second.refreshToken()).block()).isNotBlank();
    }

    @Test
    void changePasswordChecksTheCurrentOneAndEndsTheSession() {
        var session = signUp();
        var id = session.user().publicId();
        assertThatThrownBy(() -> auth.changePassword(id, "Errada123", "Nova12345").block()).isInstanceOf(DomainException.Unauthorized.class);
        assertThatThrownBy(() -> auth.changePassword(id, "Senha123", "fraca").block()).isInstanceOf(DomainException.Invalid.class);
        auth.changePassword(id, "Senha123", "Nova12345").block();
        assertThatThrownBy(() -> auth.refresh(session.refreshToken()).block()).isInstanceOf(DomainException.Unauthorized.class);
        assertThat(auth.login(EMAIL, "Nova12345").block().twoFactorRequired()).isFalse();
        assertThatThrownBy(() -> auth.login(EMAIL, "Senha123").block()).isInstanceOf(DomainException.Unauthorized.class);
    }

    @Test
    void passwordResetByCode() {
        signUp();
        var sent = auth.requestPasswordReset(EMAIL).block();
        assertThat(sent.code()).matches("\\d{6}");
        assertThatThrownBy(() -> auth.resetPassword(EMAIL, "999999", "Nova12345").block()).isInstanceOf(DomainException.Unauthorized.class);
        auth.resetPassword(EMAIL, sent.code(), "Nova12345").block();
        assertThat(auth.login(EMAIL, "Nova12345").block().session()).isNotNull();
    }

    @Test
    void passwordResetDoesNotRevealWhoIsRegistered() {
        int before = mailbox.sent.size();
        var sent = auth.requestPasswordReset("fantasma@gmail.com").block();
        assertThat(sent.code()).isNull();
        assertThat(mailbox.sent).hasSize(before);
    }

    @Test
    void googleLoginCreatesLinksAndFiltersAccounts() {
        var created = auth.loginWithGoogle("ok").block();
        assertThat(created.user().provider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(created.user().cpf()).isNull();
        assertThat(created.user().passwordHash()).isNull();
        assertThat(auth.loginWithGoogle("ok").block().user().id()).isEqualTo(created.user().id());
        assertThatThrownBy(() -> auth.login("gente@gmail.com", "Qualquer123").block())
                .isInstanceOf(DomainException.Unauthorized.class).hasMessageContaining("Google");

        google.profile = new br.rhhub.application.port.out.GoogleIdentityProvider.Profile("g-2", EMAIL, true, "Ana");
        var local = signUp();
        var linked = auth.loginWithGoogle("ok").block();
        assertThat(linked.user().id()).isEqualTo(local.user().id());
        assertThat(linked.user().googleSub()).isEqualTo("g-2");
        assertThat(linked.user().provider()).isEqualTo(AuthProvider.LOCAL);

        google.profile = new br.rhhub.application.port.out.GoogleIdentityProvider.Profile("g-3", "x@outlook.com", true, "X");
        assertThatThrownBy(() -> auth.loginWithGoogle("ok").block()).isInstanceOf(DomainException.Forbidden.class);
        google.profile = new br.rhhub.application.port.out.GoogleIdentityProvider.Profile("g-4", "y@gmail.com", false, "Y");
        assertThatThrownBy(() -> auth.loginWithGoogle("ok").block()).isInstanceOf(DomainException.Unauthorized.class);
        assertThatThrownBy(() -> auth.loginWithGoogle("recusado").block()).isInstanceOf(DomainException.Unauthorized.class);
    }

    @Test
    void rootAdminIsCreatedOnceAndPromotedIfItAlreadyExists() {
        var admin = auth.ensureRootAdmin("Root@Gmail.com", "Admin12345").block();
        assertThat(admin.role()).isEqualTo(Role.ADMIN);
        assertThat(admin.email()).isEqualTo("root@gmail.com");
        assertThat(auth.ensureRootAdmin("root@gmail.com", "Admin12345").block().id()).isEqualTo(admin.id());
        assertThat(users.byId).hasSize(1);
        assertThat(auth.login("root@gmail.com", "Admin12345").block().session().user().isAdmin()).isTrue();

        signUp();
        var promoted = auth.ensureRootAdmin(EMAIL, "Admin12345").block();
        assertThat(promoted.role()).isEqualTo(Role.ADMIN);
        assertThat(promoted.passwordHash()).isEqualTo(users.findByEmail(EMAIL).block().passwordHash());
        assertThatThrownBy(() -> auth.ensureRootAdmin("novo@gmail.com", "fraca").block()).isInstanceOf(DomainException.Invalid.class);
    }

    @Test
    void userCanCompleteProfileOnceAndDeleteTheAccount() {
        var google = auth.loginWithGoogle("ok").block().user();
        var done = account.update(google.publicId(), new UserService.Update(25, "other", null, "11122233344")).block();
        assertThat(done.cpf()).isEqualTo("11122233344");
        assertThat(done.age()).isEqualTo(25);
        assertThatThrownBy(() -> account.update(google.publicId(), new UserService.Update(null, null, null, "55566677788")).block())
                .isInstanceOf(DomainException.Unprocessable.class);
        assertThatThrownBy(() -> account.update(google.publicId(), new UserService.Update(null, null, true, null)).block())
                .isInstanceOf(DomainException.Unprocessable.class);
        assertThatThrownBy(() -> account.update(google.publicId(), new UserService.Update(10, null, null, null)).block())
                .isInstanceOf(DomainException.Invalid.class);

        var local = signUp().user();
        assertThatThrownBy(() -> account.update(local.publicId(), new UserService.Update(null, null, null, "11122233344")).block())
                .isInstanceOf(DomainException.Unprocessable.class); // já tem CPF
        assertThatThrownBy(() -> account.deleteOwn(local.publicId(), "Errada123").block()).isInstanceOf(DomainException.Unauthorized.class);
        account.deleteOwn(local.publicId(), "Senha123").block();
        assertThat(users.findByEmail(EMAIL).block()).isNull();
        assertThat(refresh.tokens).doesNotContainKey(local.publicId());
    }

    @Test
    void rootAdminCannotDeleteTheOwnAccount() {
        var admin = auth.ensureRootAdmin("root@gmail.com", "Admin12345").block();
        assertThatThrownBy(() -> account.deleteOwn(admin.publicId(), "Admin12345").block()).isInstanceOf(DomainException.Forbidden.class);
    }
}
