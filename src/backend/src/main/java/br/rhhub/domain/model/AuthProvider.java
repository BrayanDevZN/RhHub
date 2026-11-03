package br.rhhub.domain.model;

public enum AuthProvider {
    LOCAL,
    GOOGLE;

    public String value() {
        return name().toLowerCase();
    }

    public static AuthProvider from(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
