package br.rhhub.domain.model;

/** Os valores seguem o AgentRH (pending, aproved, recuse) para o front não mudar. */
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

    /** Ciclo da candidatura: nasce pending e a análise leva a aproved ou recuse, que são finais. */
    public boolean canMoveTo(ResumeStatus next) {
        return this == PENDING && (next == APROVED || next == RECUSE);
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
