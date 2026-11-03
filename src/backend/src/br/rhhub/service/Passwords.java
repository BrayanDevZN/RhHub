package br.rhhub.service;

import br.rhhub.domain.auth.PasswordHasher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** BCrypt gasta ~250 ms de CPU: roda fora da thread do servidor para não travar as outras requisições. */
final class Passwords {

    private final PasswordHasher hasher;

    Passwords(PasswordHasher hasher) {
        this.hasher = hasher;
    }

    Mono<String> hash(String password) {
        return Mono.fromCallable(() -> hasher.hash(password)).subscribeOn(Schedulers.boundedElastic());
    }

    Mono<Boolean> matches(String password, String hash) {
        return Mono.fromCallable(() -> hasher.matches(password, hash)).subscribeOn(Schedulers.boundedElastic());
    }

    /** Hash de uma senha qualquer, para gastar o mesmo tempo quando o e-mail não existe. Bloqueia: use só na subida. */
    String hashNow(String password) {
        return hasher.hash(password);
    }
}
