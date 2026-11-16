package br.rhhub.domain.auth;

import java.security.SecureRandom;

/** Código de verificação de 6 dígitos (no AgentRH eram 5), com zeros à esquerda quando precisar. */
public final class CodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private CodeGenerator() {
    }

    public static String sixDigits() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
