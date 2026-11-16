package br.rhhub.domain.model;

/** Situação de uma mensagem que falhou: FAILED (precisa de atenção) ou RESOLVED (tratada). */
public enum DeadLetterStatus {
    /** Esgotou as tentativas e ainda precisa de atenção. */
    FAILED,
    /** Foi reprocessado ou tratado. */
    RESOLVED;

    /**
     * Valor em minúsculas gravado no banco e devolvido pela API.
     *
     * @return o nome do enum em minúsculas.
     */
    public String value() {
        return name().toLowerCase();
    }

    /**
     * Converte o texto lido do banco ou da requisição para o enum, sem diferenciar maiúsculas nem espaços nas pontas.
     *
     * @throws IllegalArgumentException se o texto não for um valor de DeadLetterStatus.
     */
    public static DeadLetterStatus from(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
