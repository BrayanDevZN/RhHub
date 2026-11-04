package br.rhhub.service;

import br.rhhub.application.port.out.RefreshTokenStore;
import br.rhhub.application.port.out.UserRepository;
import br.rhhub.domain.auth.AccessPolicy;
import br.rhhub.domain.auth.PasswordHasher;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.Gender;
import br.rhhub.domain.model.User;
import br.rhhub.domain.rule.Rules;
import java.util.UUID;
import reactor.core.publisher.Mono;

/** A conta do próprio usuário: ver, completar o perfil, ligar o 2FA e excluir. */
public class UserService {

    /** Campos nulos ficam como estão. cpf só pode ser preenchido uma vez (contas do Google nascem sem ele). */
    public record Update(Integer age, String gender, Boolean twoFactor, String cpf) {
    }

    private final UserRepository users;
    private final Passwords passwords;
    private final RefreshTokenStore refreshTokens;
    private final AccessPolicy policy;

    public UserService(UserRepository users, PasswordHasher hasher, RefreshTokenStore refreshTokens, AccessPolicy policy) {
        this.users = users;
        this.passwords = new Passwords(hasher);
        this.refreshTokens = refreshTokens;
        this.policy = policy;
    }

    public Mono<User> me(UUID publicId) {
        return users.findByPublicId(publicId).switchIfEmpty(Mono.error(new DomainException.Unauthorized("Sessão inválida.")));
    }

    /** Leitura rápida de quem está logado (sem credenciais, possivelmente do cache). Não serve para alterar o usuário. */
    public Mono<User> identity(UUID publicId) {
        return users.findIdentityByPublicId(publicId)
                .switchIfEmpty(Mono.error(new DomainException.Unauthorized("Sessão inválida.")));
    }

    public Mono<User> update(UUID publicId, Update change) {
        return Mono.defer(() -> me(publicId).flatMap(user -> {
            Integer age = change.age() == null ? user.age() : Integer.valueOf(Rules.age(change.age()));
            Gender gender = change.gender() == null ? user.gender() : Rules.gender(change.gender());
            String cpf = user.cpf();
            Mono<Void> cpfCheck = Mono.empty();
            if (change.cpf() != null) {
                if (user.cpf() != null) {
                    return Mono.<User>error(new DomainException.Unprocessable("O CPF não pode ser alterado."));
                }
                String newCpf = Rules.cpf(change.cpf());
                cpf = newCpf;
                cpfCheck = users.existsByCpf(newCpf).flatMap(exists -> exists
                        ? Mono.<Void>error(new DomainException.Conflict("Esse CPF já está cadastrado."))
                        : Mono.<Void>empty());
            }
            boolean twoFactor = user.twoFactor();
            if (change.twoFactor() != null) {
                if (user.isGoogle() && change.twoFactor()) {
                    return Mono.<User>error(new DomainException.Unprocessable(
                            "Contas do Google não usam verificação em duas etapas."));
                }
                twoFactor = change.twoFactor();
            }
            User updated = user.withProfile(cpf, age, gender).withTwoFactor(twoFactor);
            return cpfCheck.then(Mono.defer(() -> users.save(updated)));
        }));
    }

    /**
     * Completa o cadastro de quem entrou com o Google (CPF, idade e gênero são obrigatórios, como no cadastro comum).
     * Os três vêm juntos. O CPF só entra se a conta ainda não tem um; se já tem, o enviado precisa ser o mesmo.
     */
    public Mono<User> completeProfile(UUID publicId, String cpf, Integer age, String gender) {
        return Mono.defer(() -> {
            if (cpf == null || cpf.isBlank() || age == null || gender == null || gender.isBlank()) {
                throw new DomainException.Invalid("Informe CPF, idade e gênero.");
            }
            return me(publicId).flatMap(user -> {
                if (user.cpf() != null && !user.cpf().equals(Rules.cpf(cpf))) {
                    return Mono.<User>error(new DomainException.Unprocessable("O CPF não pode ser alterado."));
                }
                return update(publicId, new Update(age, gender, null, user.cpf() == null ? cpf : null));
            });
        });
    }

    /** Conta local confirma com a senha; conta do Google não tem senha e confirma pela própria sessão. */
    public Mono<Void> deleteOwn(UUID publicId, String password) {
        return me(publicId).flatMap(user -> {
            policy.checkDelete(user, user);
            Mono<Boolean> confirmed = user.isGoogle() ? Mono.just(true)
                    : user.passwordHash() == null ? Mono.just(false) : passwords.matches(password, user.passwordHash());
            return confirmed.flatMap(ok -> ok
                    ? users.deleteByPublicId(publicId).then(Mono.defer(() -> refreshTokens.revoke(publicId)))
                    : Mono.<Void>error(new DomainException.Unauthorized("Senha incorreta.")));
        });
    }
}
