package br.rhhub.domain.model;

/** Os valores seguem o AgentRH (aproved, recuse, pending) para o front não mudar. */
public enum ResumeStatus {
    PENDING("pending"),
    APROVED("aproved"),
    RECUSE("recuse");

    private final String value;

    ResumeStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ResumeStatus from(String value) {
        for (ResumeStatus status : values()) {
            if (status.value.equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        throw new IllegalArgumentException("Status desconhecido: " + value);
    }
}
