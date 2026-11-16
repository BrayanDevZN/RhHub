package br.rhhub.domain.auth;

import java.time.Duration;

/** Para que serve um código de verificação: define o prefixo da chave no Redis e a validade (seção 4.9). */
public enum CodePurpose {
    SIGNUP("email_sender:create:", Duration.ofMinutes(15)),
    TWO_FACTOR("email_sender:auth:", Duration.ofMinutes(5)),
    PASSWORD_RESET("user_sender:update:", Duration.ofMinutes(8));

    private final String keyPrefix;
    private final Duration validFor;

    CodePurpose(String keyPrefix, Duration validFor) {
        this.keyPrefix = keyPrefix;
        this.validFor = validFor;
    }

    public String key(String email) {
        return keyPrefix + email;
    }

    public Duration validFor() {
        return validFor;
    }
}
