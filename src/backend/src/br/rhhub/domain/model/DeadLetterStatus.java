package br.rhhub.domain.model;

public enum DeadLetterStatus {
    /** Esgotou as tentativas e ainda precisa de atenção. */
    FAILED,
    /** Foi reprocessado ou tratado. */
    RESOLVED;

    public String value() {
        return name().toLowerCase();
    }

    public static DeadLetterStatus from(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
