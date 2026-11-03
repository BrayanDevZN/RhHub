package br.rhhub.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BcryptPasswordHasherTest {

    private final BcryptPasswordHasher hasher = new BcryptPasswordHasher();

    @Test
    void hashesAndMatches() {
        String hash = hasher.hash("Senha@123");
        assertThat(hash).isNotEqualTo("Senha@123").startsWith("$2");
        assertThat(hasher.matches("Senha@123", hash)).isTrue();
        assertThat(hasher.matches("outra", hash)).isFalse();
    }

    @Test
    void nullsNeverMatch() {
        assertThat(hasher.matches(null, "x")).isFalse();
        assertThat(hasher.matches("x", null)).isFalse();
    }
}
