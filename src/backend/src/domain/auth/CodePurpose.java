package br.rhhub.domain.auth;

import java.time.Duration;

/** Para que serve um código de verificação: define o prefixo da chave no Redis e a validade (seção 4.9). */
public enum CodePurpose {
    SIGNUP("email_sender:create:", Duration.ofMinutes(15)),
    TWO_FACTOR("email_sender:auth:", Duration.ofMinutes(5)),
    PASSWORD_RESET("user_sender:update:", Duration.ofMinutes(8));

    private final String keyPrefix;
    private final Duration validFor;

    /**
     * Define o prefixo da chave no Redis e a validade do código desta finalidade.
     */
    CodePurpose(String keyPrefix, Duration validFor) {
        this.keyPrefix = keyPrefix;
        this.validFor = validFor;
    }

    /**
     * Monta a chave do Redis onde o código é guardado: prefixo da finalidade + e-mail. Assim o mesmo e-mail pode ter um código de cadastro e outro de 2FA ao mesmo tempo, sem um apagar o outro.
     *
     * @param email e-mail já normalizado.
     * @return a chave completa, por exemplo "email_sender:create:ana@gmail.com".
     */
    public String key(String email) {
        return keyPrefix + email;
    }

    /**
     * Quanto tempo o código vale: 15 min (cadastro), 5 min (2FA) ou 8 min (troca de senha).
     */
    public Duration validFor() {
        return validFor;
    }
}
