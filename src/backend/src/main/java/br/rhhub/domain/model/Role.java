package br.rhhub.domain.model;

public enum Role {
    USER, ADMIN;

    /** Valor gravado no banco e devolvido na API (minúsculo). */
    public String value() {
        return name().toLowerCase();
    }

    public static Role from(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
