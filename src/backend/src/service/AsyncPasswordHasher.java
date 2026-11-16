package br.rhhub.service;

import br.rhhub.domain.auth.PasswordHasher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** BCrypt gasta ~250 ms de CPU: roda fora da thread do servidor para não travar as outras requisições. */
final class AsyncPasswordHasher {

    private final PasswordHasher hasher;

    /**
     * Envolve um {@link PasswordHasher} para usá-lo sem bloquear o servidor.
     */
    AsyncPasswordHasher(PasswordHasher hasher) {
        this.hasher = hasher;
    }

    /**
     * Gera o hash da senha em uma thread própria (boundedElastic). O BCrypt leva cerca de 250 ms de CPU e travaria as outras requisições se rodasse na thread do servidor.
     */
    Mono<String> hash(String password) {
        return Mono.fromCallable(() -> hasher.hash(password)).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Confere se a senha corresponde ao hash, também em thread própria.
     *
     * @return true se confere.
     */
    Mono<Boolean> matches(String password, String hash) {
        return Mono.fromCallable(() -> hasher.matches(password, hash)).subscribeOn(Schedulers.boundedElastic());
    }

    /** Hash de uma senha qualquer, para gastar o mesmo tempo quando o e-mail não existe. Bloqueia: use só na subida. */
    String hashNow(String password) {
        return hasher.hash(password);
    }
}
