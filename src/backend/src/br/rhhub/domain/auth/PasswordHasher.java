package br.rhhub.domain.auth;

/** Abstração do hash de senha. A implementação (BCrypt) fica em infra/security. */
public interface PasswordHasher {

    String hash(String password);

    boolean matches(String password, String hash);
}
