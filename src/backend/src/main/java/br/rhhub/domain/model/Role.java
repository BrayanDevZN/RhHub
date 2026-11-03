package br.rhhub.domain.model;

public enum Role {
    USER,
    ADMIN;

    public String value() {
        return name().toLowerCase();
    }

    public static Role from(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
