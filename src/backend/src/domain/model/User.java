package br.rhhub.domain.model;

import java.time.Instant;
import java.util.UUID;

/** cpf, age, gender e passwordHash são nulos em contas Google. id e createdAt são nulos antes de gravar. */
public record User(
        Long id,
        UUID publicId,
        String name,
        String email,
        String cpf,
        Integer age,
        Gender gender,
        String passwordHash,
        boolean twoFactor,
        Role role,
        AuthProvider provider,
        String googleSub,
        Instant createdAt) {

    /**
     * Diz se o usuário tem o papel de administrador.
     */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /**
     * Devolve uma cópia do usuário com o hash de senha trocado (o objeto original não muda).
     *
     * @param hash hash BCrypt da nova senha.
     */
    public User withPasswordHash(String hash) {
        return new User(id, publicId, name, email, cpf, age, gender, hash, twoFactor, role, provider, googleSub, createdAt);
    }

    /**
     * Devolve uma cópia com a verificação em duas etapas ligada ou desligada.
     */
    public User withTwoFactor(boolean enabled) {
        return new User(id, publicId, name, email, cpf, age, gender, passwordHash, enabled, role, provider, googleSub, createdAt);
    }

    /**
     * Devolve uma cópia com CPF, idade e gênero preenchidos. Usado ao completar o cadastro de uma conta criada com o Google.
     */
    public User withProfile(String newCpf, Integer newAge, Gender newGender) {
        return new User(id, publicId, name, email, newCpf, newAge, newGender, passwordHash, twoFactor, role, provider,
                googleSub, createdAt);
    }

    /**
     * Devolve uma cópia com outro papel (promover ou rebaixar).
     */
    public User withRole(Role newRole) {
        return new User(id, publicId, name, email, cpf, age, gender, passwordHash, twoFactor, newRole, provider, googleSub, createdAt);
    }

    /**
     * Devolve uma cópia ligada a uma conta Google, guardando o identificador estável dela (sub).
     */
    public User withGoogleSub(String sub) {
        return new User(id, publicId, name, email, cpf, age, gender, passwordHash, twoFactor, role, provider, sub, createdAt);
    }

    /** A mesma conta sem o hash da senha e sem o id do Google: o que pode ir para o cache e para leituras só de consulta. */
    public User withoutCredentials() {
        return new User(id, publicId, name, email, cpf, age, gender, null, twoFactor, role, provider, null, createdAt);
    }

    /**
     * Diz se a conta foi criada pelo login com Google.
     */
    public boolean isGoogle() {
        return provider == AuthProvider.GOOGLE;
    }
}
