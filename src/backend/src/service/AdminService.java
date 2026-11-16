package br.rhhub.service;

import br.rhhub.application.port.out.RefreshTokenStore;
import br.rhhub.application.port.out.UserRepository;
import br.rhhub.domain.auth.AccessPolicy;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.Role;
import br.rhhub.domain.model.User;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Administração de usuários, seguindo a tabela de permissões (admin e admin raiz). */
public class AdminService {

    private final UserRepository users;
    private final RefreshTokenStore refreshTokens;
    private final AccessPolicy policy;

    public AdminService(UserRepository users, RefreshTokenStore refreshTokens, AccessPolicy policy) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.policy = policy;
    }

    public Flux<User> listUsers(User actor) {
        return Flux.defer(() -> {
            policy.requireAdmin(actor);
            return users.findAll();
        });
    }

    public Mono<User> getUser(User actor, UUID publicId) {
        return Mono.defer(() -> {
            policy.requireAdmin(actor);
            return find(publicId);
        });
    }

    /** Admin promove candidato; só o admin raiz rebaixa. */
    public Mono<User> setRole(User actor, UUID publicId, String rawRole) {
        return Mono.defer(() -> {
            policy.requireAdmin(actor);
            Role role;
            try {
                role = Role.from(rawRole);
            } catch (RuntimeException e) {
                throw new DomainException.Invalid("Papel inválido: use user ou admin.");
            }
            return find(publicId).flatMap(target -> {
                if (target.role() == role) {
                    return Mono.just(target);
                }
                if (role == Role.ADMIN) {
                    policy.checkPromote(actor, target);
                } else {
                    policy.checkDemote(actor, target);
                }
                return users.save(target.withRole(role));
            });
        });
    }

    /** Só o admin raiz exclui contas de terceiros; a conta do raiz não é excluída. */
    public Mono<Void> deleteUser(User actor, UUID publicId) {
        return Mono.defer(() -> find(publicId).flatMap(target -> {
            policy.checkDelete(actor, target);
            return users.deleteByPublicId(publicId).then(Mono.defer(() -> refreshTokens.revoke(publicId)));
        }));
    }

    private Mono<User> find(UUID publicId) {
        return users.findByPublicId(publicId).switchIfEmpty(Mono.error(new DomainException.NotFound("Usuário não encontrado.")));
    }
}
