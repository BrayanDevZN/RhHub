package br.rhhub.domain.model;

/** Gênero informado no cadastro (male, female, other). */
public enum Gender {
    MALE, FEMALE, OTHER;

    /** Valor gravado no banco e devolvido na API (minúsculo). */
    public String value() {
        return name().toLowerCase();
    }

    /**
     * Converte o texto lido do banco ou da requisição para o enum, sem diferenciar maiúsculas nem espaços nas pontas.
     *
     * @throws IllegalArgumentException se o texto não for um valor de Gender.
     */
    public static Gender from(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
