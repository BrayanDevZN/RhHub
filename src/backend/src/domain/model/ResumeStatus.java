package br.rhhub.domain.model;

/** Os valores seguem o AgentRH (pending, aproved, recuse) para o front não mudar. */
public enum ResumeStatus {
    PENDING("pending"),
    APROVED("aproved"),
    RECUSE("recuse");

    private final String value;

    /**
     * Associa cada situação ao texto gravado no banco (a grafia "aproved" e "recuse" é proposital).
     */
    ResumeStatus(String value) {
        this.value = value;
    }

    /**
     * Texto gravado no banco e devolvido pela API.
     */
    public String value() {
        return value;
    }

    /** Ciclo da candidatura: nasce pending e a análise leva a aproved ou recuse, que são finais. */
    public boolean canMoveTo(ResumeStatus next) {
        return this == PENDING && (next == APROVED || next == RECUSE);
    }

    /**
     * Converte o texto do banco para o enum, sem diferenciar maiúsculas.
     *
     * @throws IllegalArgumentException se o status for desconhecido.
     */
    public static ResumeStatus from(String value) {
        for (ResumeStatus status : values()) {
            if (status.value.equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        throw new IllegalArgumentException("Status desconhecido: " + value);
    }
}
