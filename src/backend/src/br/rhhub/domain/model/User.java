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

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public User withPasswordHash(String hash) {
        return new User(id, publicId, name, email, cpf, age, gender, hash, twoFactor, role, provider, googleSub, createdAt);
    }

    public User withTwoFactor(boolean enabled) {
        return new User(id, publicId, name, email, cpf, age, gender, passwordHash, enabled, role, provider, googleSub, createdAt);
    }

    public User withProfile(String newCpf, Integer newAge, Gender newGender) {
        return new User(id, publicId, name, email, newCpf, newAge, newGender, passwordHash, twoFactor, role, provider,
                googleSub, createdAt);
    }

    public User withRole(Role newRole) {
        return new User(id, publicId, name, email, cpf, age, gender, passwordHash, twoFactor, newRole, provider, googleSub, createdAt);
    }

    public User withGoogleSub(String sub) {
        return new User(id, publicId, name, email, cpf, age, gender, passwordHash, twoFactor, role, provider, sub, createdAt);
    }

    public boolean isGoogle() {
        return provider == AuthProvider.GOOGLE;
    }
}
