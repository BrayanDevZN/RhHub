package br.rhhub.domain.auth;

import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.Role;
import br.rhhub.domain.model.User;

/**
 * Quem pode o quê (tabela da seção 4.4). O admin raiz não é um papel no banco: é o admin cujo e-mail é o ADMIN_EMAIL.
 */
public final class AccessPolicy {

    private final String rootEmail;

    public AccessPolicy(String rootEmail) {
        this.rootEmail = rootEmail == null ? "" : rootEmail.strip().toLowerCase();
    }

    public boolean isRoot(User user) {
        return user.isAdmin() && !rootEmail.isEmpty() && rootEmail.equals(user.email());
    }

    public void requireAdmin(User actor) {
        if (!actor.isAdmin()) {
            throw new DomainException.Forbidden("Apenas administradores podem fazer isso.");
        }
    }

    public void requireRoot(User actor) {
        if (!isRoot(actor)) {
            throw new DomainException.Forbidden("Apenas o administrador raiz pode fazer isso.");
        }
    }

    /** Admin e admin raiz promovem candidato a admin. */
    public void checkPromote(User actor, User target) {
        requireAdmin(actor);
        if (target.isAdmin()) {
            throw new DomainException.Conflict("Esse usuário já é administrador.");
        }
    }

    /** Só o raiz rebaixa, e nunca a si mesmo nem a outro raiz. */
    public void checkDemote(User actor, User target) {
        requireRoot(actor);
        if (!target.isAdmin() || isRoot(target)) {
            throw new DomainException.Forbidden("Não é possível rebaixar esse usuário.");
        }
    }

    /** Excluir a própria conta é livre (exceto o raiz); excluir a de terceiros é só do raiz. */
    public void checkDelete(User actor, User target) {
        if (isRoot(target)) {
            throw new DomainException.Forbidden("A conta do administrador raiz não pode ser excluída.");
        }
        if (actor.id() != null && actor.id().equals(target.id())) {
            return;
        }
        requireRoot(actor);
    }

    public Role roleAfterPromotion() {
        return Role.ADMIN;
    }
}
