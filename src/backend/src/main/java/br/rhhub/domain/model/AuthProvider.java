package br.rhhub.domain.model;

public enum AuthProvider {
    LOCAL, GOOGLE;

    /** Valor gravado no banco e devolvido na API (minúsculo). */
    public String value() {
        return name().toLowerCase();
    }

    public static AuthProvider from(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
