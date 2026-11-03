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
}
