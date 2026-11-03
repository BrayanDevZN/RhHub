package br.rhhub.domain.model;

public enum Gender {
    MALE,
    FEMALE,
    OTHER;

    public String value() {
        return name().toLowerCase();
    }

    public static Gender from(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
