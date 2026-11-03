package br.rhhub.service;

import br.rhhub.application.port.out.EmailComposer;
import br.rhhub.application.port.out.EmailSender;
import br.rhhub.application.port.out.GoogleIdentityProvider;
import br.rhhub.application.port.out.RefreshTokenStore;
import br.rhhub.application.port.out.UserRepository;
import br.rhhub.application.port.out.VerificationCodeStore;
import br.rhhub.domain.auth.CodePurpose;
import br.rhhub.domain.auth.JwtService;
import br.rhhub.domain.auth.PasswordHasher;
import br.rhhub.domain.auth.TokenClaims;
import br.rhhub.domain.auth.TokenType;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.AuthProvider;
import br.rhhub.domain.model.Role;
import br.rhhub.domain.model.User;
import br.rhhub.domain.rule.Rules;
import java.time.Clock;
import java.util.UUID;
import reactor.core.publisher.Mono;

/** Cadastro, login (com 2FA opcional), refresh, logout, troca e recuperação de senha e login com Google. */
public class AuthService {

    /** Tokens de uma sessão aberta. */
    public record Session(User user, String accessToken, String refreshToken) {
    }

    /** Resultado do login: ou a sessão aberta, ou o token temporário que leva ao passo do 2FA. */
    public record LoginResult(Session session, String twoFactorToken) {
        public boolean twoFactorRequired() {
            return session == null;
        }
    }

    /** code só vem preenchido em ENVIRONMENT=test, para os testes não dependerem de e-mail. */
    public record CodeSent(String code) {
    }

    private static final String BAD_CREDENTIALS = "E-mail ou senha incorretos.";

    private final UserRepository users;
    private final Passwords passwords;
    private final JwtService jwt;
    private final RefreshTokenStore refreshTokens;
    private final VerificationCodeStore codes;
    private final EmailComposer composer;
    private final EmailSender email;
    private final GoogleIdentityProvider google;
    private final Clock clock;
    private final boolean exposeCode;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordHasher hasher, JwtService jwt, RefreshTokenStore refreshTokens,
            VerificationCodeStore codes, EmailComposer composer, EmailSender email, GoogleIdentityProvider google,
            Clock clock, boolean exposeCode) {
        this.users = users;
        this.passwords = new Passwords(hasher);
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.codes = codes;
        this.composer = composer;
        this.email = email;
        this.google = google;
        this.clock = clock;
        this.exposeCode = exposeCode;
        this.dummyHash = passwords.hashNow("senha-que-ninguem-usa");
    }

    // ---- cadastro ----

    public Mono<CodeSent> requestSignupCode(String rawName, String rawEmail) {
        return Mono.defer(() -> {
            String name = Rules.name(rawName);
            String address = Rules.email(rawEmail);
            return users.existsByEmail(address).flatMap(exists -> exists
                    ? Mono.<CodeSent>error(new DomainException.Conflict("Esse e-mail já está cadastrado."))
                    : sendCode(CodePurpose.SIGNUP, address, name));
        });
    }

    public Mono<Session> register(String rawName, String rawEmail, String rawCpf, Integer rawAge, String rawGender,
            String rawPassword, String rawCode) {
        return Mono.defer(() -> {
            String name = Rules.name(rawName);
            String address = Rules.email(rawEmail);
            String cpf = Rules.cpf(rawCpf);
            int age = Rules.age(rawAge);
            var gender = Rules.gender(rawGender);
            String password = Rules.password(rawPassword);
            String code = Rules.code(rawCode);
            return users.existsByEmail(address)
                    .flatMap(exists -> exists
                            ? Mono.<Boolean>error(new DomainException.Conflict("Esse e-mail já está cadastrado."))
                            : users.existsByCpf(cpf))
                    .flatMap(exists -> exists
                            ? Mono.<Void>error(new DomainException.Conflict("Esse CPF já está cadastrado."))
                            : codes.verify(CodePurpose.SIGNUP, address, code))
                    .then(Mono.defer(() -> passwords.hash(password)))
                    .flatMap(hash -> users.save(new User(null, UUID.randomUUID(), name, address, cpf, age, gender,
                            hash, false, Role.USER, AuthProvider.LOCAL, null, null)))
                    .flatMap(this::openSession);
        });
    }

    // ---- login ----

    public Mono<LoginResult> login(String rawEmail, String password) {
        return Mono.defer(() -> {
            String address = Rules.email(rawEmail);
            return users.findByEmail(address)
                    .switchIfEmpty(Mono.defer(() -> passwords.matches(password, dummyHash) // mesmo tempo de um usuário real
                            .then(Mono.<User>empty())))
                    .flatMap(user -> {
                        if (user.isGoogle() || user.passwordHash() == null) {
                            return Mono.<LoginResult>error(new DomainException.Unauthorized(
                                    "Essa conta usa o login com Google."));
                        }
                        return passwords.matches(password, user.passwordHash()).flatMap(ok -> {
                            if (!ok) {
                                return Mono.<LoginResult>error(new DomainException.Unauthorized(BAD_CREDENTIALS));
                            }
                            if (user.twoFactor()) {
                                return Mono.just(new LoginResult(null,
                                        jwt.issue(user.publicId(), TokenType.TWO_FACTOR, clock.instant())));
                            }
                            return openSession(user).map(session -> new LoginResult(session, null));
                        });
                    })
                    .switchIfEmpty(Mono.error(new DomainException.Unauthorized(BAD_CREDENTIALS)));
        });
    }

    public Mono<CodeSent> sendTwoFactorCode(String twoFactorToken) {
        return Mono.defer(() -> {
            TokenClaims claims = jwt.verify(twoFactorToken, TokenType.TWO_FACTOR, clock.instant());
            return userOrUnauthorized(claims.subject())
                    .flatMap(user -> sendCode(CodePurpose.TWO_FACTOR, user.email(), user.name()));
        });
    }

    public Mono<Session> verifyTwoFactor(String twoFactorToken, String rawCode) {
        return Mono.defer(() -> {
            TokenClaims claims = jwt.verify(twoFactorToken, TokenType.TWO_FACTOR, clock.instant());
            String code = Rules.code(rawCode);
            return userOrUnauthorized(claims.subject())
                    .flatMap(user -> codes.verify(CodePurpose.TWO_FACTOR, user.email(), code).thenReturn(user))
                    .flatMap(this::openSession);
        });
    }

    // ---- sessão ----

    /** Gera um access token novo. O refresh token continua o mesmo, até expirar ou ser revogado. */
    public Mono<String> refresh(String refreshToken) {
        return Mono.defer(() -> {
            TokenClaims claims = jwt.verify(refreshToken, TokenType.REFRESH, clock.instant());
            return refreshTokens.isCurrent(claims.subject(), claims.id()).flatMap(current -> current
                    ? userOrUnauthorized(claims.subject())
                            .map(user -> jwt.issue(user.publicId(), TokenType.ACCESS, clock.instant()))
                    : Mono.<String>error(new DomainException.Unauthorized("Sessão encerrada. Entre de novo.")));
        });
    }

    public Mono<Void> logout(UUID publicId) {
        return refreshTokens.revoke(publicId);
    }

    // ---- senha ----

    public Mono<Void> changePassword(UUID publicId, String current, String rawNewPassword) {
        return Mono.defer(() -> {
            String newPassword = Rules.password(rawNewPassword);
            return userOrUnauthorized(publicId).flatMap(user -> {
                if (user.isGoogle() || user.passwordHash() == null) {
                    return Mono.<Void>error(new DomainException.Unprocessable("Contas do Google não têm senha."));
                }
                return passwords.matches(current, user.passwordHash()).flatMap(ok -> ok
                        ? passwords.hash(newPassword)
                                .flatMap(hash -> users.save(user.withPasswordHash(hash)))
                                .then(Mono.defer(() -> refreshTokens.revoke(publicId)))
                        : Mono.<Void>error(new DomainException.Unauthorized("Senha atual incorreta.")));
            });
        });
    }

    /** Responde igual exista ou não a conta, para não revelar quem está cadastrado. */
    public Mono<CodeSent> requestPasswordReset(String rawEmail) {
        return Mono.defer(() -> {
            String address = Rules.email(rawEmail);
            return users.findByEmail(address)
                    .filter(user -> !user.isGoogle())
                    .flatMap(user -> sendCode(CodePurpose.PASSWORD_RESET, address, user.name()))
                    .defaultIfEmpty(new CodeSent(null));
        });
    }

    public Mono<Void> resetPassword(String rawEmail, String rawCode, String rawNewPassword) {
        return Mono.defer(() -> {
            String address = Rules.email(rawEmail);
            String code = Rules.code(rawCode);
            String newPassword = Rules.password(rawNewPassword);
            return codes.verify(CodePurpose.PASSWORD_RESET, address, code)
                    .then(Mono.defer(() -> users.findByEmail(address)))
                    .switchIfEmpty(Mono.error(new DomainException.Unauthorized("Código inválido ou expirado.")))
                    .flatMap(user -> passwords.hash(newPassword)
                            .flatMap(hash -> users.save(user.withPasswordHash(hash)))
                            .then(Mono.defer(() -> refreshTokens.revoke(user.publicId()))));
        });
    }

    // ---- Google ----

    public boolean googleEnabled() {
        return google.enabled();
    }

    public String googleAuthorizationUrl(String state) {
        return google.authorizationUrl(state);
    }

    /** Entra (ou cria a conta, ou vincula à existente pelo e-mail). Contas do Google não passam pelo 2FA. */
    public Mono<Session> loginWithGoogle(String code) {
        return google.fetchProfile(code).flatMap(profile -> {
            if (!profile.emailVerified()) {
                return Mono.<User>error(new DomainException.Unauthorized("O Google não verificou esse e-mail."));
            }
            String address;
            try {
                address = Rules.email(profile.email());
            } catch (DomainException.Invalid e) {
                return Mono.<User>error(new DomainException.Forbidden("Só contas @gmail.com podem entrar."));
            }
            return users.findByGoogleSub(profile.sub())
                    .switchIfEmpty(Mono.defer(() -> users.findByEmail(address)
                            .flatMap(existing -> users.save(existing.withGoogleSub(profile.sub())))))
                    .switchIfEmpty(Mono.defer(() -> users.save(new User(null, UUID.randomUUID(),
                            profile.name().isBlank() ? address.substring(0, address.indexOf('@')) : Rules.name(profile.name()),
                            address, null, null, null, null, false, Role.USER, AuthProvider.GOOGLE, profile.sub(), null))));
        }).flatMap(this::openSession);
    }

    // ---- admin raiz ----

    /** Cria o admin raiz se não existir e garante o papel de admin se já existir. Idempotente. */
    public Mono<User> ensureRootAdmin(String rawEmail, String rawPassword) {
        return Mono.defer(() -> {
            String address = Rules.email(rawEmail);
            String password = Rules.password(rawPassword);
            return users.findByEmail(address)
                    .flatMap(existing -> existing.isAdmin() ? Mono.just(existing)
                            : users.save(existing.withRole(Role.ADMIN)))
                    .switchIfEmpty(Mono.defer(() -> passwords.hash(password)
                            .flatMap(hash -> users.save(new User(null, UUID.randomUUID(), "Administrador", address,
                                    null, null, null, hash, false, Role.ADMIN, AuthProvider.LOCAL, null, null)))));
        });
    }

    // ---- internos ----

    private Mono<CodeSent> sendCode(CodePurpose purpose, String address, String name) {
        return codes.issue(purpose, address)
                .flatMap(code -> email.send(composer.verificationCode(purpose, address, name, code))
                        .thenReturn(new CodeSent(exposeCode ? code : null)));
    }

    private Mono<User> userOrUnauthorized(UUID publicId) {
        return users.findByPublicId(publicId)
                .switchIfEmpty(Mono.error(new DomainException.Unauthorized("Sessão inválida.")));
    }

    private Mono<Session> openSession(User user) {
        var now = clock.instant();
        String refreshId = UUID.randomUUID().toString();
        String refresh = jwt.issue(user.publicId(), TokenType.REFRESH, refreshId, now);
        String access = jwt.issue(user.publicId(), TokenType.ACCESS, now);
        return refreshTokens.save(user.publicId(), refreshId).thenReturn(new Session(user, access, refresh));
    }
}
