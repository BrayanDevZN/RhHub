package br.rhhub.domain.model;

import java.time.Instant;
import java.util.UUID;

/** cpf, age e gender são nulos em contas Google; passwordHash é nulo nelas também. */
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

    public User withPasswordHash(String hash) {
        return new User(id, publicId, name, email, cpf, age, gender, hash, twoFactor, role, provider, googleSub, createdAt);
    }

    public User withTwoFactor(boolean value) {
        return new User(id, publicId, name, email, cpf, age, gender, passwordHash, value, role, provider, googleSub, createdAt);
    }

    public User withRole(Role value) {
        return new User(id, publicId, name, email, cpf, age, gender, passwordHash, twoFactor, value, provider, googleSub, createdAt);
    }

    public User withAge(Integer value) {
        return new User(id, publicId, name, email, cpf, value, gender, passwordHash, twoFactor, role, provider, googleSub, createdAt);
    }

    public User withGender(Gender value) {
        return new User(id, publicId, name, email, cpf, age, value, passwordHash, twoFactor, role, provider, googleSub, createdAt);
    }

    public User withGoogleSub(String sub) {
        return new User(id, publicId, name, email, cpf, age, gender, passwordHash, twoFactor, role, provider, sub, createdAt);
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
