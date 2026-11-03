package br.rhhub.application.port.out;

import java.time.Duration;

public enum CodePurpose {
    SIGNUP(Duration.ofMinutes(15)),
    TWO_FACTOR(Duration.ofMinutes(5)),
    PASSWORD_RESET(Duration.ofMinutes(8));

    private final Duration ttl;

    CodePurpose(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration ttl() {
        return ttl;
    }
}
