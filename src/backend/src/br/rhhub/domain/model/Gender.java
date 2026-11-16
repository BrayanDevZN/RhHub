package br.rhhub.domain.model;

/** Gênero informado no cadastro (male, female, other). */
public enum Gender {
    MALE, FEMALE, OTHER;

    /** Valor gravado no banco e devolvido na API (minúsculo). */
    public String value() {
        return name().toLowerCase();
    }

    public static Gender from(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
