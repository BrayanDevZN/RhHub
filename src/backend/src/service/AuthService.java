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
        /**
         * Diz se o login parou na segunda etapa: sem sessão aberta, falta o código de 2FA.
         */
        public boolean twoFactorRequired() {
            return session == null;
        }
    }

    /** code só vem preenchido em ENVIRONMENT=test, para os testes não dependerem de e-mail. */
    public record CodeSent(String code) {
    }

    private static final String BAD_CREDENTIALS = "E-mail ou senha incorretos.";
    /** Mensagem de login com e-mail ou Google que não têm conta: o front mostra o aviso e leva ao cadastro. */
    public static final String NO_ACCOUNT = "Não encontramos uma conta com esse e-mail. Crie uma conta para entrar.";

    private final UserRepository users;
    private final AsyncPasswordHasher passwords;
    private final JwtService jwt;
    private final RefreshTokenStore refreshTokens;
    private final VerificationCodeStore codes;
    private final EmailComposer composer;
    private final EmailSender email;
    private final GoogleIdentityProvider google;
    private final Clock clock;
    private final boolean exposeCode;

    /**
     * Cria o serviço de autenticação.
     *
     * @param exposeCode se true, o código de verificação também volta na resposta (só no ambiente de teste); em produção ele chega apenas por e-mail.
     */
    public AuthService(UserRepository users, PasswordHasher hasher, JwtService jwt, RefreshTokenStore refreshTokens,
            VerificationCodeStore codes, EmailComposer composer, EmailSender email, GoogleIdentityProvider google,
            Clock clock, boolean exposeCode) {
        this.users = users;
        this.passwords = new AsyncPasswordHasher(hasher);
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.codes = codes;
        this.composer = composer;
        this.email = email;
        this.google = google;
        this.clock = clock;
        this.exposeCode = exposeCode;
    }

    // ---- cadastro ----

    /**
     * Primeiro passo do cadastro: valida nome e e-mail, confirma que o e-mail ainda não existe e envia um código de 6 dígitos para ele (vale 15 minutos).
     *
     * @throws DomainException.Conflict se o e-mail já está cadastrado.
     */
    public Mono<CodeSent> requestSignupCode(String rawName, String rawEmail) {
        return Mono.defer(() -> {
            String name = Rules.name(rawName);
            String address = Rules.email(rawEmail);
            return users.existsByEmail(address).flatMap(exists -> exists
                    ? Mono.<CodeSent>error(new DomainException.Conflict("Esse e-mail já está cadastrado."))
                    : sendCode(CodePurpose.SIGNUP, address, name));
        });
    }

    /**
     * Segundo passo do cadastro: cria a conta.
     *
     * Valida todos os campos, confere e-mail e CPF repetidos, confere o código recebido por e-mail (vale uma vez), gera o hash da senha, grava o usuário com papel USER e já abre a sessão (devolve os tokens).
     *
     * @throws DomainException.Conflict se e-mail ou CPF já existem.
     * @throws DomainException.Unauthorized se o código está errado ou expirou.
     */
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

    /**
     * Login com e-mail e senha.
     *
     * Se não existe conta, responde 404 (para o front oferecer criar uma). Contas do Google não têm senha e são recusadas. Senha errada dá 401 com a mensagem genérica "E-mail ou senha incorretos". Se o usuário ligou o 2FA, devolve só um token temporário de 2FA e a sessão só abre depois do código; senão abre a sessão direto.
     */
    public Mono<LoginResult> login(String rawEmail, String password) {
        return Mono.defer(() -> {
            String address = Rules.email(rawEmail);
            return users.findByEmail(address)
                    .switchIfEmpty(Mono.error(new DomainException.NotFound(NO_ACCOUNT)))
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

    /**
     * Envia por e-mail o código da segunda etapa. Exige o token temporário de 2FA devolvido pelo login.
     *
     * @throws DomainException.Unauthorized se o token for inválido ou vencido.
     */
    public Mono<CodeSent> sendTwoFactorCode(String twoFactorToken) {
        return Mono.defer(() -> {
            TokenClaims claims = jwt.verify(twoFactorToken, TokenType.TWO_FACTOR, clock.instant());
            return userOrUnauthorized(claims.subject())
                    .flatMap(user -> sendCode(CodePurpose.TWO_FACTOR, user.email(), user.name()));
        });
    }

    /**
     * Termina o login com 2FA: confere o token temporário e o código recebido por e-mail e, se ambos estão certos, abre a sessão.
     */
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

    /**
     * Encerra a sessão do usuário apagando o refresh token dele no Redis.
     */
    public Mono<Void> logout(UUID publicId) {
        return refreshTokens.revoke(publicId);
    }

    // ---- senha ----

    /**
     * Troca a senha de quem está logado. Exige a senha atual. Contas do Google não têm senha (422). Depois de trocar, revoga o refresh token, então todas as sessões precisam entrar de novo.
     */
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

    /**
     * Termina a recuperação: confere o código, grava a nova senha e revoga as sessões do usuário.
     *
     * @throws DomainException.Unauthorized "Código inválido ou expirado" se o código não confere ou a conta não existe.
     */
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

    /**
     * Diz se o login com Google está ligado (GOOGLE_OAUTH_ENABLED).
     */
    public boolean googleEnabled() {
        return google.enabled();
    }

    /**
     * Monta o endereço do Google para onde o navegador é enviado.
     *
     * @param state valor aleatório que volta no callback para provar que a volta é da mesma pessoa.
     */
    public String googleAuthorizationUrl(String state) {
        return google.authorizationUrl(state);
    }

    /**
     * Atalho de {@link #loginWithGoogle(String, boolean)} que cria a conta se ela não existir.
     */
    public Mono<Session> loginWithGoogle(String code) {
        return loginWithGoogle(code, true);
    }

    /**
     * Entra com a conta que já existe (pelo Google ou pelo e-mail, vinculando as duas). Se não existir, cria a conta só
     * quando createIfMissing é verdadeiro (o cadastro com Google); no login comum devolve NotFound, para o front avisar
     * que a conta não existe. Contas do Google não passam pelo 2FA.
     */
    public Mono<Session> loginWithGoogle(String code, boolean createIfMissing) {
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
                    .switchIfEmpty(Mono.defer(() -> !createIfMissing
                            ? Mono.<User>error(new DomainException.NotFound(NO_ACCOUNT))
                            : users.save(new User(null, UUID.randomUUID(),
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

    /**
     * Gera um código, monta o e-mail da finalidade e o envia. Devolve o código só se {@code exposeCode} estiver ligado (ambiente de teste).
     */
    private Mono<CodeSent> sendCode(CodePurpose purpose, String address, String name) {
        return codes.issue(purpose, address)
                .flatMap(code -> email.send(composer.verificationCode(purpose, address, name, code))
                        .thenReturn(new CodeSent(exposeCode ? code : null)));
    }

    /**
     * Busca o usuário ou falha com 401 "Sessão inválida" (a pessoa tem token, mas a conta não existe mais).
     */
    private Mono<User> userOrUnauthorized(UUID publicId) {
        return users.findByPublicId(publicId)
                .switchIfEmpty(Mono.error(new DomainException.Unauthorized("Sessão inválida.")));
    }

    /**
     * Abre a sessão: emite o token de acesso (15 min) e o de refresh (7 dias), com um identificador novo que é guardado no Redis (o que invalida o refresh anterior).
     */
    private Mono<Session> openSession(User user) {
        var now = clock.instant();
        String refreshId = UUID.randomUUID().toString();
        String refresh = jwt.issue(user.publicId(), TokenType.REFRESH, refreshId, now);
        String access = jwt.issue(user.publicId(), TokenType.ACCESS, now);
        return refreshTokens.save(user.publicId(), refreshId).thenReturn(new Session(user, access, refresh));
    }
}
