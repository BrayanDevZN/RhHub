package br.rhhub.domain.model;

/** Papel do usuário: USER (candidato) ou ADMIN (RH). */
public enum Role {
    USER, ADMIN;

    /** Valor gravado no banco e devolvido na API (minúsculo). */
    public String value() {
        return name().toLowerCase();
    }

    /**
     * Converte o texto lido do banco ou da requisição para o enum, sem diferenciar maiúsculas nem espaços nas pontas.
     *
     * @throws IllegalArgumentException se o texto não for um valor de Role.
     */
    public static Role from(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
