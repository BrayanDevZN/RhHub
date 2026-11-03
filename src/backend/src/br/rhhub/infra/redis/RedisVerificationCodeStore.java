package br.rhhub.infra.redis;

import br.rhhub.application.port.out.VerificationCodeStore;
import br.rhhub.domain.auth.CodeGenerator;
import br.rhhub.domain.auth.CodePurpose;
import br.rhhub.domain.exception.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RedisVerificationCodeStore implements VerificationCodeStore {

    static final int MAX_ATTEMPTS = 5;
    static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);

    private final RedisStore redis;

    public RedisVerificationCodeStore(RedisStore redis) {
        this.redis = redis;
    }

    @Override
    public Mono<String> issue(CodePurpose purpose, String email) {
        String key = purpose.key(email);
        String code = CodeGenerator.sixDigits();
        return redis.setIfAbsent(key + ":cooldown", "1", RESEND_COOLDOWN).flatMap(first -> {
            if (!Boolean.TRUE.equals(first)) {
                return Mono.error(new DomainException.TooManyRequests("Aguarde um minuto para pedir outro código."));
            }
            return redis.delete(key + ":attempts")
                    .then(redis.set(key, code, purpose.validFor()))
                    .thenReturn(code);
        });
    }

    @Override
    public Mono<Void> verify(CodePurpose purpose, String email, String code) {
        String key = purpose.key(email);
        return redis.get(key)
                .switchIfEmpty(Mono.error(new DomainException.Unauthorized("Código inválido ou expirado.")))
                .flatMap(expected -> {
                    if (code != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                            code.strip().getBytes(StandardCharsets.UTF_8))) {
                        return redis.delete(key, key + ":attempts").then();
                    }
                    return redis.incrementWithTtl(key + ":attempts", purpose.validFor()).flatMap(total -> {
                        if (total >= MAX_ATTEMPTS) {
                            return redis.delete(key, key + ":attempts").then(Mono.error(
                                    new DomainException.TooManyRequests("Muitas tentativas. Peça um novo código.")));
                        }
                        return Mono.error(new DomainException.Unauthorized("Código inválido ou expirado."));
                    });
                });
    }
}
