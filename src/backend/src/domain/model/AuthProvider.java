package br.rhhub.domain.model;

/** Como a conta foi criada: LOCAL (e-mail e senha) ou GOOGLE. */
public enum AuthProvider {
    LOCAL, GOOGLE;

    /** Valor gravado no banco e devolvido na API (minúsculo). */
    public String value() {
        return name().toLowerCase();
    }

    /**
     * Converte o texto lido do banco ou da requisição para o enum, sem diferenciar maiúsculas nem espaços nas pontas.
     *
     * @throws IllegalArgumentException se o texto não for um valor de AuthProvider.
     */
    public static AuthProvider from(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
