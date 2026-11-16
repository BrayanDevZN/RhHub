package br.rhhub.domain.auth;

import java.security.SecureRandom;

/** Código de verificação de 6 dígitos (no AgentRH eram 5), com zeros à esquerda quando precisar. */
public final class CodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Classe utilitária: não é criada.
     */
    private CodeGenerator() {
    }

    /**
     * Gera um código aleatório de 6 dígitos usando {@link SecureRandom} (imprevisível). Completa com zeros à esquerda, por exemplo "004821".
     *
     * @return o código como texto de 6 caracteres numéricos.
     */
    public static String sixDigits() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
